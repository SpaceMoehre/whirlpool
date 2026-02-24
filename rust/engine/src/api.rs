use reqwest::StatusCode;

use crate::curl_cffi::fetch_with_curl_cffi;
use crate::errors::EngineError;
use crate::models::{
    ApiStatusResponse, ApiVideoEnvelope, ApiVideoRecord, EngineConfig, StatusSummary, VideoItem,
};

const DEFAULT_USER_AGENT: &str = "whirlpool-engine/0.1 (+android; uniffi)";

#[derive(Debug, Clone)]
pub struct ApiClient {
    base_url: String,
    python_executable: String,
    curl_cffi_script_path: Option<String>,
}

impl ApiClient {
    pub fn new(config: &EngineConfig) -> Self {
        Self {
            base_url: config.api_base_url.trim_end_matches('/').to_string(),
            python_executable: config.python_executable.clone(),
            curl_cffi_script_path: config.curl_cffi_script_path.clone(),
        }
    }

    pub fn fetch_status(&self) -> Result<StatusSummary, EngineError> {
        let endpoint = format!("{}/api/status", self.base_url);
        let body = self.fetch_text(&endpoint, &[])?;
        let parsed = serde_json::from_str::<ApiStatusResponse>(&body)?;

        Ok(StatusSummary {
            name: parsed.name.unwrap_or_else(|| "unknown".to_string()),
            api_version: parsed.api_version.unwrap_or_else(|| "unknown".to_string()),
            primary_color: parsed.primary_color,
            secondary_color: parsed.secondary_color,
            channels: parsed.channels.unwrap_or_default(),
            sources: parsed.sources.unwrap_or_default(),
            adblock_required: parsed.adblock_required.unwrap_or(false),
            source_releases_url: parsed.source_releases_url,
        })
    }

    pub fn discover_videos(
        &self,
        query: &str,
        page: u32,
        limit: u32,
    ) -> Result<Vec<VideoItem>, EngineError> {
        let params = vec![
            ("query", query.to_string()),
            ("page", page.to_string()),
            ("limit", limit.to_string()),
        ];

        let primary = format!("{}/api/videos", self.base_url);
        let body = match self.fetch_text(&primary, &params) {
            Ok(body) => body,
            Err(err) => {
                // Compatibility path for older servers exposing `/api/video`.
                let fallback = format!("{}/api/video", self.base_url);
                match self.fetch_text(&fallback, &params) {
                    Ok(body) => body,
                    Err(_) => return Err(err),
                }
            }
        };

        parse_videos(&body)
    }

    pub fn fetch_text(&self, url: &str, query: &[(&str, String)]) -> Result<String, EngineError> {
        let runtime = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .map_err(|err| EngineError::Network {
                detail: format!("failed to build runtime: {err}"),
            })?;

        let (status, body) = runtime
            .block_on(async {
                let client = reqwest::Client::builder()
                    .user_agent(DEFAULT_USER_AGENT)
                    .build()?;

                let response = client.get(url).query(query).send().await?;
                let status = response.status();
                let body = response.text().await?;
                Ok::<(StatusCode, String), reqwest::Error>((status, body))
            })
            .map_err(|err| EngineError::Network {
                detail: format!("network request failed: {err}"),
            })?;

        if status.is_success() {
            return Ok(body);
        }

        if should_try_curl_cffi(status) {
            if let Some(script_path) = &self.curl_cffi_script_path {
                return fetch_with_curl_cffi(&self.python_executable, script_path, url, query);
            }
        }

        Err(EngineError::Network {
            detail: format!("request failed with status {status} at {url}"),
        })
    }
}

fn should_try_curl_cffi(status: StatusCode) -> bool {
    matches!(
        status,
        StatusCode::FORBIDDEN | StatusCode::TOO_MANY_REQUESTS | StatusCode::SERVICE_UNAVAILABLE
    )
}

fn parse_videos(body: &str) -> Result<Vec<VideoItem>, EngineError> {
    if let Ok(envelope) = serde_json::from_str::<ApiVideoEnvelope>(body) {
        return Ok(envelope.videos.into_iter().map(map_video_record).collect());
    }

    if let Ok(videos) = serde_json::from_str::<Vec<ApiVideoRecord>>(body) {
        return Ok(videos.into_iter().map(map_video_record).collect());
    }

    Err(EngineError::Serialization {
        detail: "unexpected videos payload shape".to_string(),
    })
}

fn map_video_record(record: ApiVideoRecord) -> VideoItem {
    let page_url = record.url.unwrap_or_default();
    let id = record
        .id
        .filter(|value| !value.is_empty())
        .unwrap_or_else(|| page_url.clone());

    VideoItem {
        id,
        title: record.title.unwrap_or_else(|| "Untitled".to_string()),
        page_url,
        duration_seconds: record.duration,
        image_url: record.image,
        network: record.network,
        author_name: record.author_name,
        extractor: record.extractor,
        view_count: record.view_count,
    }
}
