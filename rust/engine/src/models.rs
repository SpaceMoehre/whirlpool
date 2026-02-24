use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct EngineConfig {
    pub api_base_url: String,
    pub db_path: String,
    pub yt_dlp_path: String,
    pub python_executable: String,
    pub curl_cffi_script_path: Option<String>,
    pub yt_dlp_repo_api: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct StatusSummary {
    pub name: String,
    pub api_version: String,
    pub primary_color: Option<String>,
    pub secondary_color: Option<String>,
    pub channels: Vec<String>,
    pub sources: Vec<String>,
    pub adblock_required: bool,
    pub source_releases_url: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct VideoItem {
    pub id: String,
    pub title: String,
    pub page_url: String,
    pub duration_seconds: Option<u32>,
    pub image_url: Option<String>,
    pub network: Option<String>,
    pub author_name: Option<String>,
    pub extractor: Option<String>,
    pub view_count: Option<u64>,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct ResolvedVideo {
    pub id: String,
    pub title: String,
    pub page_url: String,
    pub stream_url: String,
    pub thumbnail_url: Option<String>,
    pub author_name: Option<String>,
    pub extractor: Option<String>,
    pub duration_seconds: Option<u32>,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct FavoriteItem {
    pub video_id: String,
    pub title: String,
    pub image_url: Option<String>,
    pub network: Option<String>,
    pub added_at_epoch: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct YtDlpUpdateInfo {
    pub current_version: Option<String>,
    pub latest_version: Option<String>,
    pub update_available: bool,
    pub checked_at_epoch: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct BridgeHealth {
    pub engine_ready: bool,
    pub db_accessible: bool,
    pub last_error: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct ApiStatusResponse {
    pub name: Option<String>,
    #[serde(rename = "apiVersion")]
    pub api_version: Option<String>,
    #[serde(rename = "primaryColor")]
    pub primary_color: Option<String>,
    #[serde(rename = "secondaryColor")]
    pub secondary_color: Option<String>,
    pub channels: Option<Vec<String>>,
    pub sources: Option<Vec<String>>,
    #[serde(rename = "adblockRequired")]
    pub adblock_required: Option<bool>,
    #[serde(rename = "sourceReleasesUrl")]
    pub source_releases_url: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct ApiVideoEnvelope {
    pub videos: Vec<ApiVideoRecord>,
}

#[derive(Debug, Deserialize)]
pub struct ApiVideoRecord {
    pub id: Option<String>,
    pub title: Option<String>,
    pub url: Option<String>,
    pub duration: Option<u32>,
    pub image: Option<String>,
    pub network: Option<String>,
    pub author_name: Option<String>,
    pub extractor: Option<String>,
    pub view_count: Option<u64>,
}

#[derive(Debug, Deserialize)]
pub struct GitHubRelease {
    pub tag_name: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct YtDlpResponse {
    pub id: Option<String>,
    pub title: Option<String>,
    pub webpage_url: Option<String>,
    pub url: Option<String>,
    pub thumbnail: Option<String>,
    pub uploader: Option<String>,
    pub extractor: Option<String>,
    pub duration: Option<f64>,
    pub formats: Option<Vec<YtDlpFormat>>,
}

#[derive(Debug, Deserialize)]
pub struct YtDlpFormat {
    pub url: Option<String>,
    pub protocol: Option<String>,
}
