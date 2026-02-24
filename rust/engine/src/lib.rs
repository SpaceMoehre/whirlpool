mod api;
mod curl_cffi;
mod db;
mod errors;
mod models;
mod updater;
mod ytdlp;

use std::sync::Arc;

use api::ApiClient;
use db::Database;
use errors::EngineError;
use models::{
    BridgeHealth, EngineConfig, FavoriteItem, ResolvedVideo, StatusSummary, VideoItem,
    YtDlpUpdateInfo,
};
use updater::{check_yt_dlp_update, default_release_api};
use ytdlp::YtDlpClient;

uniffi::setup_scaffolding!();

#[derive(uniffi::Object)]
pub struct Engine {
    config: EngineConfig,
    db: Database,
    api: ApiClient,
    yt_dlp: YtDlpClient,
}

#[uniffi::export]
impl Engine {
    #[uniffi::constructor]
    pub fn new(config: EngineConfig) -> Result<Arc<Self>, EngineError> {
        validate_config(&config)?;

        let db = Database::new(&config.db_path);
        db.init()?;

        let engine = Arc::new(Self {
            api: ApiClient::new(&config),
            yt_dlp: YtDlpClient::new(config.yt_dlp_path.clone(), config.python_executable.clone()),
            db,
            config,
        });

        // Boot-time update check; errors are persisted and surfaced through bridge health.
        if let Err(err) = engine.sync_boot_metadata() {
            let _ = engine.db.set_meta("boot_error", &err.to_string());
        }

        Ok(engine)
    }

    pub fn sync_status(&self) -> Result<StatusSummary, EngineError> {
        self.api.fetch_status()
    }

    pub fn discover_videos(
        &self,
        query: String,
        page: u32,
        limit: u32,
    ) -> Result<Vec<VideoItem>, EngineError> {
        let videos = self.api.discover_videos(&query, page, limit)?;
        self.db.cache_videos(&videos)?;
        Ok(videos)
    }

    pub fn resolve_stream(&self, page_url: String) -> Result<ResolvedVideo, EngineError> {
        if let Some(cached) = self.db.get_cached_resolved_video(&page_url, 60 * 60 * 6)? {
            return Ok(cached);
        }

        let resolved = self.yt_dlp.extract_stream(&page_url)?;
        self.db.cache_resolved_video(&page_url, &resolved)?;
        Ok(resolved)
    }

    pub fn list_favorites(&self) -> Result<Vec<FavoriteItem>, EngineError> {
        self.db.list_favorites()
    }

    pub fn add_favorite(&self, video: VideoItem) -> Result<FavoriteItem, EngineError> {
        self.db.add_favorite(&video)
    }

    pub fn remove_favorite(&self, video_id: String) -> Result<bool, EngineError> {
        self.db.remove_favorite(&video_id)
    }

    pub fn export_database(&self, export_path: String) -> Result<bool, EngineError> {
        self.db.export_to(&export_path)
    }

    pub fn import_database(&self, import_path: String) -> Result<bool, EngineError> {
        self.db.import_from(&import_path)
    }

    pub fn check_yt_dlp_update(&self) -> Result<YtDlpUpdateInfo, EngineError> {
        let release_api = self
            .config
            .yt_dlp_repo_api
            .as_deref()
            .unwrap_or(default_release_api());

        let current = self.yt_dlp.current_version().ok();
        let update = check_yt_dlp_update(release_api, current)?;

        if let Some(current) = &update.current_version {
            self.db.set_meta("yt_dlp_current", current)?;
        }
        if let Some(latest) = &update.latest_version {
            self.db.set_meta("yt_dlp_latest", latest)?;
        }
        self.db.set_meta(
            "yt_dlp_update_available",
            &update.update_available.to_string(),
        )?;

        Ok(update)
    }

    pub fn run_yt_dlp_update(&self) -> Result<String, EngineError> {
        let output = self.yt_dlp.update_binary()?;
        self.db.set_meta("yt_dlp_last_update_output", &output)?;
        Ok(output)
    }

    pub fn bridge_health(&self) -> Result<BridgeHealth, EngineError> {
        let last_error = self.db.get_meta("boot_error")?;
        Ok(BridgeHealth {
            engine_ready: true,
            db_accessible: self.db.path().exists(),
            last_error,
        })
    }
}

impl Engine {
    fn sync_boot_metadata(&self) -> Result<(), EngineError> {
        let update = self.check_yt_dlp_update()?;
        self.db
            .set_meta("boot_checked_at", &update.checked_at_epoch.to_string())?;
        Ok(())
    }
}

fn validate_config(config: &EngineConfig) -> Result<(), EngineError> {
    if config.api_base_url.trim().is_empty() {
        return Err(EngineError::InvalidConfig {
            detail: "api_base_url cannot be empty".to_string(),
        });
    }
    if config.db_path.trim().is_empty() {
        return Err(EngineError::InvalidConfig {
            detail: "db_path cannot be empty".to_string(),
        });
    }
    if config.yt_dlp_path.trim().is_empty() {
        return Err(EngineError::InvalidConfig {
            detail: "yt_dlp_path cannot be empty".to_string(),
        });
    }
    if config.python_executable.trim().is_empty() {
        return Err(EngineError::InvalidConfig {
            detail: "python_executable cannot be empty".to_string(),
        });
    }
    Ok(())
}

const _: fn() = || {
    fn assert_send_sync<T: Send + Sync>() {}
    assert_send_sync::<Engine>();
};

pub use errors::EngineError as UniFfiEngineError;
pub use models::{
    BridgeHealth as UniFfiBridgeHealth, EngineConfig as UniFfiEngineConfig,
    FavoriteItem as UniFfiFavoriteItem, ResolvedVideo as UniFfiResolvedVideo,
    StatusSummary as UniFfiStatusSummary, VideoItem as UniFfiVideoItem,
    YtDlpUpdateInfo as UniFfiYtDlpUpdateInfo,
};
