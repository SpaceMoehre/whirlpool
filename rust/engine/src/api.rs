use std::collections::HashMap;

use reqwest::StatusCode;
use serde_json::json;

use crate::curl_cffi::fetch_with_curl_cffi;
use crate::errors::EngineError;
use crate::models::{
    ApiStatusChannel, ApiStatusResponse, ApiVideoEnvelope, ApiVideoRecord, EngineConfig,
    FilterSelection, StatusChannel, StatusChoice, StatusFilterOption, StatusSummary, VideoItem,
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
        let parsed = self.fetch_status_payload()?;
        let channels = parsed.channels.unwrap_or_default();
        let channel_ids = channels.iter().map(|channel| channel.id.clone()).collect();
        let channel_details = channels.into_iter().map(map_status_channel).collect();

        Ok(StatusSummary {
            name: parsed.name.unwrap_or_else(|| "unknown".to_string()),
            api_version: parsed
                .api_version
                .or(parsed.id)
                .unwrap_or_else(|| "unknown".to_string()),
            icon_url: parsed.icon_url,
            primary_color: parsed.primary_color.or(parsed.color),
            secondary_color: parsed.secondary_color,
            channels: channel_ids,
            channel_details,
            sources: parsed.sources.or(parsed.categories).unwrap_or_default(),
            adblock_required: parsed.adblock_required.unwrap_or(false),
            source_releases_url: parsed.source_releases_url,
        })
    }

    pub fn discover_videos_with_filters(
        &self,
        query: &str,
        page: u32,
        limit: u32,
        channel_id: Option<&str>,
        selections: &[FilterSelection],
    ) -> Result<Vec<VideoItem>, EngineError> {
        let status = self.fetch_status_payload()?;
        let selected_channel =
            select_channel_with_id_or_default(&status, channel_id).ok_or_else(|| {
                EngineError::NotFound {
                    detail: "no active channel returned by /api/status".to_string(),
                }
            })?;

        let payload =
            build_videos_payload(selected_channel, query, page, limit, selections).to_string();

        let primary = format!("{}/api/videos", self.base_url);
        let body = self.fetch_text("POST", &primary, Some(&payload))?;

        parse_videos(&body, &selected_channel.id)
    }

    fn fetch_status_payload(&self) -> Result<ApiStatusResponse, EngineError> {
        let endpoint = format!("{}/api/status", self.base_url);
        // Some upstream gateways reject POST requests without a Content-Length.
        let body = self.fetch_text("POST", &endpoint, Some("{}"))?;
        let parsed = serde_json::from_str::<ApiStatusResponse>(&body)?;
        Ok(parsed)
    }

    fn fetch_text(
        &self,
        method: &str,
        url: &str,
        json_body: Option<&str>,
    ) -> Result<String, EngineError> {
        let runtime = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .map_err(|err| EngineError::Network {
                detail: format!("failed to build runtime: {err}"),
            })?;

        let request_method =
            reqwest::Method::from_bytes(method.as_bytes()).map_err(|err| EngineError::Network {
                detail: format!("invalid request method {method}: {err}"),
            })?;

        let request_result = runtime.block_on(async {
            let client = reqwest::Client::builder()
                .user_agent(DEFAULT_USER_AGENT)
                .build()?;

            let mut request = client.request(request_method, url);
            if let Some(body) = json_body {
                request = request
                    .header("Content-Type", "application/json")
                    .body(body.to_owned());
            }

            let response = request.send().await?;
            let status = response.status();
            let body = response.text().await?;
            Ok::<(StatusCode, String), reqwest::Error>((status, body))
        });

        let (status, body) = match request_result {
            Ok(result) => result,
            Err(err) => {
                if let Some(script_path) = &self.curl_cffi_script_path {
                    return fetch_with_curl_cffi(
                        &self.python_executable,
                        script_path,
                        method,
                        url,
                        json_body,
                    );
                }
                return Err(EngineError::Network {
                    detail: format!("network request failed: {err}"),
                });
            }
        };

        if status.is_success() {
            return Ok(body);
        }

        if should_try_curl_cffi(status) {
            if let Some(script_path) = &self.curl_cffi_script_path {
                return fetch_with_curl_cffi(
                    &self.python_executable,
                    script_path,
                    method,
                    url,
                    json_body,
                );
            }
        }

        Err(EngineError::Network {
            detail: format!("request failed with status {status} at {url}: {body}"),
        })
    }
}

fn should_try_curl_cffi(status: StatusCode) -> bool {
    matches!(
        status,
        StatusCode::FORBIDDEN | StatusCode::TOO_MANY_REQUESTS | StatusCode::SERVICE_UNAVAILABLE
    )
}

fn select_channel(status: &ApiStatusResponse) -> Option<&ApiStatusChannel> {
    let channels = status.channels.as_ref()?;
    channels
        .iter()
        .find(|channel| channel.default)
        .or_else(|| {
            channels
                .iter()
                .find(|channel| channel.status.as_deref() == Some("active"))
        })
        .or_else(|| channels.first())
}

fn select_channel_with_id_or_default<'a>(
    status: &'a ApiStatusResponse,
    channel_id: Option<&str>,
) -> Option<&'a ApiStatusChannel> {
    let channels = status.channels.as_ref()?;
    if let Some(channel_id) = channel_id.filter(|id| !id.trim().is_empty()) {
        if let Some(channel) = channels.iter().find(|channel| channel.id == channel_id) {
            return Some(channel);
        }
    }
    select_channel(status)
}

fn map_status_channel(channel: ApiStatusChannel) -> StatusChannel {
    let title = channel.name.unwrap_or_else(|| channel.id.clone());
    let options = channel
        .options
        .into_iter()
        .map(|option| {
            let option_title = option.title.unwrap_or_else(|| option.id.clone());
            let choices = option
                .options
                .into_iter()
                .map(|choice| StatusChoice {
                    id: choice.id.clone(),
                    title: choice.title.unwrap_or(choice.id),
                })
                .collect();
            StatusFilterOption {
                id: option.id,
                title: option_title,
                choices,
            }
        })
        .collect();

    StatusChannel {
        id: channel.id,
        title,
        options,
    }
}

fn build_videos_payload(
    channel: &ApiStatusChannel,
    query: &str,
    page: u32,
    limit: u32,
    selections: &[FilterSelection],
) -> serde_json::Value {
    let mut payload = serde_json::Map::new();
    payload.insert("channel".to_string(), json!(channel.id));
    payload.insert("query".to_string(), json!(query));
    payload.insert("page".to_string(), json!(page.to_string()));
    payload.insert("perPage".to_string(), json!(limit.to_string()));

    let explicit_selections: HashMap<&str, &str> = selections
        .iter()
        .filter_map(|selection| {
            let option_id = selection.option_id.trim();
            let choice_id = selection.choice_id.trim();
            if option_id.is_empty() || choice_id.is_empty() {
                return None;
            }
            Some((option_id, choice_id))
        })
        .collect();

    for option in &channel.options {
        if option.id.trim().is_empty() || option.options.is_empty() {
            continue;
        }

        let selected_id = explicit_selections
            .get(option.id.as_str())
            .and_then(|candidate| {
                option
                    .options
                    .iter()
                    .find(|choice| choice.id == **candidate)
                    .map(|choice| choice.id.as_str())
            })
            .or_else(|| option.options.first().map(|choice| choice.id.as_str()));

        if let Some(selected_id) = selected_id {
            payload.insert(option.id.clone(), json!(selected_id));
        }
    }

    serde_json::Value::Object(payload)
}

fn parse_videos(body: &str, default_channel_id: &str) -> Result<Vec<VideoItem>, EngineError> {
    if let Ok(envelope) = serde_json::from_str::<ApiVideoEnvelope>(body) {
        let source = if envelope.videos.is_empty() {
            envelope.items
        } else {
            envelope.videos
        };
        return Ok(source
            .into_iter()
            .map(|record| map_video_record(record, default_channel_id))
            .collect());
    }

    if let Ok(videos) = serde_json::from_str::<Vec<ApiVideoRecord>>(body) {
        return Ok(videos
            .into_iter()
            .map(|record| map_video_record(record, default_channel_id))
            .collect());
    }

    Err(EngineError::Serialization {
        detail: "unexpected videos payload shape".to_string(),
    })
}

fn map_video_record(record: ApiVideoRecord, default_channel_id: &str) -> VideoItem {
    let page_url = record.url.unwrap_or_default();
    let id = record
        .id
        .filter(|value| !value.is_empty())
        .or(record.hashed_url.filter(|value| !value.is_empty()))
        .unwrap_or_else(|| page_url.clone());

    VideoItem {
        id,
        title: record.title.unwrap_or_else(|| "Untitled".to_string()),
        page_url,
        duration_seconds: record.duration,
        image_url: record.image,
        network: record
            .network
            .or_else(|| Some(default_channel_id.to_string())),
        author_name: record.author_name,
        extractor: record.extractor,
        view_count: record.view_count,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_getfigleaf_status_with_channel_objects() {
        let payload = r##"{
            "id": "figleaf",
            "name": "Fig Leaf",
            "subtitle": "Watch it!",
            "description": "A source for all your fig leaf needs.",
            "iconUrl": "https://cdn.hottubapp.io/assets/channels/figleaf.png",
            "color": "#478003",
            "status": "normal",
            "notices": [],
            "channels": [
                {
                    "id": "catflix",
                    "name": "Catflix",
                    "description": "All cats, all the time.",
                    "color": "indigo",
                    "status": "active",
                    "default": true,
                    "options": [
                        {
                            "id": "sort",
                            "title": "Sort",
                            "options": [
                                { "id": "views", "title": "Views" },
                                { "id": "likes", "title": "Likes" },
                                { "id": "latest", "title": "Latest" }
                            ]
                        }
                    ],
                    "categories": ["Funny Cats", "Kittens"],
                    "ytdlpCommand": "--format best[ext=mp4]"
                }
            ],
            "subscription": { "status": "inactive" },
            "nsfw": false,
            "categories": ["Cute", "Funny"],
            "message": "New tutorials available."
        }"##;

        let parsed: ApiStatusResponse = serde_json::from_str(payload).expect("parse status");
        let channel = select_channel(&parsed).expect("select channel");
        assert_eq!(channel.id, "catflix");
        assert!(channel.default);
        let payload = build_videos_payload(channel, "", 1, 10, &[]);
        assert_eq!(
            payload.get("sort").and_then(|value| value.as_str()),
            Some("views")
        );
    }

    #[test]
    fn parses_getfigleaf_items_video_envelope() {
        let payload = r#"{
            "pageInfo": {
                "recommendations": [],
                "hasNextPage": true
            },
            "items": [
                {
                    "hashedUrl": "c85017ca87477168d648727753c4ded8a35f173e22ef93743e707b296becb299",
                    "id": "c85017ca87477168d648727753c4ded8a35f173e22ef93743e707b296becb299",
                    "title": "20 Minutes of Adorable Kittens | BEST Compilation",
                    "url": "https://www.youtube.com/watch?v=y0sF5xhGreA",
                    "duration": 0,
                    "views": 14622653,
                    "channel": "catflix",
                    "thumb": "https://i.ytimg.com/vi/y0sF5xhGreA/hqdefault.jpg",
                    "uploader": "Unknown",
                    "uploaderUrl": "",
                    "uploaderId": "unknown"
                },
                {
                    "hashedUrl": "a867c7478b16ab1139475edf4fabe5898ad825e358469e1d4396ca9e2986bb8b",
                    "id": "a867c7478b16ab1139475edf4fabe5898ad825e358469e1d4396ca9e2986bb8b",
                    "title": "The three Charo kitten brothers are very close and always together!",
                    "url": "https://www.youtube.com/watch?v=hD48qDOTyTs",
                    "duration": 0,
                    "views": 5294,
                    "channel": "catflix",
                    "thumb": "https://i.ytimg.com/vi/hD48qDOTyTs/hqdefault.jpg",
                    "uploader": "Unknown",
                    "uploaderUrl": "",
                    "uploaderId": "unknown"
                }
            ]
        }"#;

        let videos = parse_videos(payload, "catflix").expect("parse items envelope");
        assert_eq!(videos.len(), 2);
        assert_eq!(
            videos[0].id,
            "c85017ca87477168d648727753c4ded8a35f173e22ef93743e707b296becb299"
        );
        assert_eq!(videos[0].network.as_deref(), Some("catflix"));
        assert_eq!(
            videos[0].image_url.as_deref(),
            Some("https://i.ytimg.com/vi/y0sF5xhGreA/hqdefault.jpg")
        );
        assert_eq!(videos[0].author_name.as_deref(), Some("Unknown"));
        assert_eq!(videos[0].view_count, Some(14_622_653));
    }

    #[test]
    fn parses_items_video_envelope_shape() {
        let payload = r#"{
            "pageInfo": { "hasNextPage": true },
            "items": [{
                "hashedUrl": "abc",
                "id": "abc",
                "title": "Clip",
                "url": "https://example.com/watch?v=1",
                "duration": 0,
                "views": 42,
                "channel": "catflix",
                "thumb": "https://img.example.com/1.jpg",
                "uploader": "Uploader"
            }]
        }"#;

        let videos = parse_videos(payload, "catflix").expect("parse items envelope");
        assert_eq!(videos.len(), 1);
        assert_eq!(videos[0].id, "abc");
        assert_eq!(videos[0].network.as_deref(), Some("catflix"));
        assert_eq!(
            videos[0].image_url.as_deref(),
            Some("https://img.example.com/1.jpg")
        );
        assert_eq!(videos[0].author_name.as_deref(), Some("Uploader"));
    }

    #[test]
    fn selects_default_channel_and_latest_sort() {
        let status: ApiStatusResponse = serde_json::from_str(
            r#"{
                "channels": [{
                    "id": "catflix",
                    "default": true,
                    "options": [{
                        "id": "sort",
                        "options": [
                            { "id": "views" },
                            { "id": "latest" }
                        ]
                    }]
                }]
            }"#,
        )
        .expect("parse status");

        let channel = select_channel(&status).expect("default channel");
        assert_eq!(channel.id, "catflix");
        let payload = build_videos_payload(channel, "", 1, 10, &[]);
        assert_eq!(
            payload.get("sort").and_then(|value| value.as_str()),
            Some("views")
        );
        assert_eq!(
            payload.get("page").and_then(|value| value.as_str()),
            Some("1")
        );
        assert_eq!(
            payload.get("perPage").and_then(|value| value.as_str()),
            Some("10")
        );
    }

    #[test]
    fn payload_uses_explicit_option_selection_when_valid() {
        let status: ApiStatusResponse = serde_json::from_str(
            r#"{
                "channels": [{
                    "id": "catflix",
                    "default": true,
                    "options": [{
                        "id": "sort",
                        "options": [
                            { "id": "views" },
                            { "id": "latest" }
                        ]
                    }, {
                        "id": "duration",
                        "options": [
                            { "id": "short" },
                            { "id": "long" }
                        ]
                    }]
                }]
            }"#,
        )
        .expect("parse status");

        let channel = select_channel(&status).expect("default channel");
        let payload = build_videos_payload(
            channel,
            "kittens",
            1,
            10,
            &[
                FilterSelection {
                    option_id: "sort".to_string(),
                    choice_id: "latest".to_string(),
                },
                FilterSelection {
                    option_id: "duration".to_string(),
                    choice_id: "long".to_string(),
                },
            ],
        );
        assert_eq!(
            payload.get("sort").and_then(|value| value.as_str()),
            Some("latest")
        );
        assert_eq!(
            payload.get("duration").and_then(|value| value.as_str()),
            Some("long")
        );
        assert_eq!(
            payload.get("query").and_then(|value| value.as_str()),
            Some("kittens")
        );
    }

    #[test]
    #[ignore = "live network test against getfigleaf.com"]
    fn fetches_and_parses_live_getfigleaf_videos() {
        let client = ApiClient::new(&EngineConfig {
            api_base_url: "https://getfigleaf.com".to_string(),
            db_path: "/tmp/whirlpool-live-test.db".to_string(),
            yt_dlp_path: "/tmp/yt-dlp".to_string(),
            python_executable: "python3".to_string(),
            curl_cffi_script_path: None,
            yt_dlp_repo_api: None,
        });

        let status = client.fetch_status().expect("fetch status");
        assert!(
            !status.channels.is_empty(),
            "status should include channels"
        );

        let videos = client
            .discover_videos_with_filters("", 1, 10, None, &[])
            .expect("fetch and parse videos");
        assert!(!videos.is_empty(), "videos response should not be empty");
        assert!(
            videos
                .iter()
                .all(|video| !video.id.is_empty() && video.page_url.starts_with("https://")),
            "all parsed videos must have ids and https URLs"
        );
    }
}
