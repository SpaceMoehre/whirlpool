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
    pub icon_url: Option<String>,
    pub primary_color: Option<String>,
    pub secondary_color: Option<String>,
    pub channels: Vec<String>,
    pub channel_details: Vec<StatusChannel>,
    pub sources: Vec<String>,
    pub adblock_required: bool,
    pub source_releases_url: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct UserPreference {
    pub id: String,
    pub preference_value: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct SourceServer {
    pub base_url: String,
    pub title: String,
    pub color: Option<String>,
    pub icon_url: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct StatusChannel {
    pub id: String,
    pub title: String,
    pub description: Option<String>,
    pub favicon_url: Option<String>,
    pub ytdlp_command: Option<String>,
    pub options: Vec<StatusFilterOption>,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct StatusFilterOption {
    pub id: String,
    pub title: String,
    pub multi_select: bool,
    pub choices: Vec<StatusChoice>,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct StatusChoice {
    pub id: String,
    pub title: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct FilterSelection {
    pub option_id: String,
    pub choice_id: String,
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
    pub id: Option<String>,
    pub name: Option<String>,
    pub subtitle: Option<String>,
    pub description: Option<String>,
    #[serde(rename = "iconUrl")]
    pub icon_url: Option<String>,
    pub color: Option<String>,
    pub status: Option<String>,
    pub message: Option<String>,
    pub categories: Option<Vec<String>>,
    #[serde(rename = "apiVersion")]
    pub api_version: Option<String>,
    #[serde(rename = "primaryColor")]
    pub primary_color: Option<String>,
    #[serde(rename = "secondaryColor")]
    pub secondary_color: Option<String>,
    pub channels: Option<Vec<ApiStatusChannel>>,
    pub sources: Option<Vec<String>>,
    #[serde(rename = "adblockRequired")]
    pub adblock_required: Option<bool>,
    #[serde(rename = "sourceReleasesUrl")]
    pub source_releases_url: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct ApiVideoEnvelope {
    #[serde(default)]
    pub videos: Vec<ApiVideoRecord>,
    #[serde(default)]
    pub items: Vec<ApiVideoRecord>,
}

#[derive(Debug, Deserialize)]
pub struct ApiStatusChannel {
    pub id: String,
    pub name: Option<String>,
    pub description: Option<String>,
    pub favicon: Option<String>,
    pub color: Option<String>,
    pub status: Option<String>,
    #[serde(default)]
    pub default: bool,
    #[serde(default)]
    pub options: Vec<ApiStatusChannelOption>,
    #[serde(default)]
    pub categories: Vec<String>,
    #[serde(rename = "ytdlpCommand")]
    pub ytdlp_command: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct ApiStatusChannelOption {
    pub id: String,
    pub title: Option<String>,
    #[serde(rename = "multiSelect", default)]
    pub multi_select: bool,
    #[serde(default)]
    pub options: Vec<ApiStatusChoice>,
}

#[derive(Debug, Deserialize)]
pub struct ApiStatusChoice {
    pub id: String,
    pub title: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct ApiVideoRecord {
    pub id: Option<String>,
    #[serde(rename = "hashedUrl")]
    pub hashed_url: Option<String>,
    pub title: Option<String>,
    pub url: Option<String>,
    pub duration: Option<u32>,
    #[serde(alias = "thumb")]
    pub image: Option<String>,
    #[serde(alias = "channel")]
    pub network: Option<String>,
    #[serde(alias = "uploader")]
    pub author_name: Option<String>,
    pub extractor: Option<String>,
    #[serde(alias = "views")]
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
