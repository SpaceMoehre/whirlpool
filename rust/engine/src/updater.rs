use chrono::Utc;

use crate::errors::EngineError;
use crate::models::{GitHubRelease, YtDlpUpdateInfo};

const DEFAULT_RELEASES_API: &str = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest";
const GH_USER_AGENT: &str = "whirlpool-engine/0.1 (+android; uniffi)";

pub fn default_release_api() -> &'static str {
    DEFAULT_RELEASES_API
}

pub fn check_yt_dlp_update(
    release_api: &str,
    current_version: Option<String>,
) -> Result<YtDlpUpdateInfo, EngineError> {
    let latest_version = fetch_latest_release_tag(release_api)?;
    let update_available = match (&current_version, &latest_version) {
        (Some(current), Some(latest)) => normalize_tag(current) != normalize_tag(latest),
        _ => false,
    };

    Ok(YtDlpUpdateInfo {
        current_version,
        latest_version,
        update_available,
        checked_at_epoch: Utc::now().timestamp(),
    })
}

fn fetch_latest_release_tag(release_api: &str) -> Result<Option<String>, EngineError> {
    let runtime = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .map_err(|err| EngineError::Network {
            detail: format!("failed to build runtime: {err}"),
        })?;

    let body = runtime
        .block_on(async {
            let client = reqwest::Client::builder()
                .user_agent(GH_USER_AGENT)
                .build()?;
            let response = client.get(release_api).send().await?.error_for_status()?;
            response.text().await
        })
        .map_err(|err| EngineError::Network {
            detail: format!("failed fetching latest yt-dlp release: {err}"),
        })?;

    let parsed = serde_json::from_str::<GitHubRelease>(&body)?;
    Ok(parsed.tag_name)
}

fn normalize_tag(tag: &str) -> String {
    tag.trim()
        .to_ascii_lowercase()
        .trim_start_matches('v')
        .to_string()
}

#[cfg(test)]
mod tests {
    use super::normalize_tag;

    #[test]
    fn strips_v_prefix_and_casing() {
        assert_eq!(normalize_tag("v2025.01.01"), "2025.01.01");
        assert_eq!(normalize_tag("V2025.01.02"), "2025.01.02");
        assert_eq!(normalize_tag(" 2025.01.03 "), "2025.01.03");
    }
}
