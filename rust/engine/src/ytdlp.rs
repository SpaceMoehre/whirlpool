use std::process::Command;

use crate::errors::EngineError;
use crate::models::{ResolvedVideo, YtDlpResponse};

#[derive(Debug, Clone)]
pub struct YtDlpClient {
    binary_path: String,
    python_executable: String,
}

impl YtDlpClient {
    pub fn new(binary_path: String, python_executable: String) -> Self {
        Self {
            binary_path,
            python_executable,
        }
    }

    pub fn extract_stream(&self, page_url: &str) -> Result<ResolvedVideo, EngineError> {
        let output = self.run_ytdlp(&["-J", "--no-playlist", "--no-warnings", page_url])?;

        if !output.status.success() {
            let stderr = String::from_utf8_lossy(&output.stderr);
            return Err(EngineError::Process {
                detail: format!("yt-dlp extraction failed: {stderr}"),
            });
        }

        let text = String::from_utf8(output.stdout).map_err(|err| EngineError::Process {
            detail: format!("yt-dlp output is not utf8: {err}"),
        })?;

        let payload = serde_json::from_str::<YtDlpResponse>(&text)?;

        let stream_url = payload
            .url
            .or_else(|| {
                payload.formats.as_ref().and_then(|formats| {
                    formats
                        .iter()
                        .find(|format| {
                            format
                                .protocol
                                .as_ref()
                                .map(|protocol| protocol.starts_with("http"))
                                .unwrap_or(false)
                        })
                        .and_then(|format| format.url.clone())
                })
            })
            .ok_or_else(|| EngineError::NotFound {
                detail: "yt-dlp output did not include a stream url".to_string(),
            })?;

        Ok(ResolvedVideo {
            id: payload.id.unwrap_or_else(|| page_url.to_string()),
            title: payload.title.unwrap_or_else(|| "Untitled".to_string()),
            page_url: payload.webpage_url.unwrap_or_else(|| page_url.to_string()),
            stream_url,
            thumbnail_url: payload.thumbnail,
            author_name: payload.uploader,
            extractor: payload.extractor,
            duration_seconds: payload.duration.map(|value| value as u32),
        })
    }

    pub fn current_version(&self) -> Result<String, EngineError> {
        let output = self.run_ytdlp(&["--version"])?;

        if !output.status.success() {
            let stderr = String::from_utf8_lossy(&output.stderr);
            return Err(EngineError::Process {
                detail: format!("yt-dlp --version failed: {stderr}"),
            });
        }

        let version = String::from_utf8(output.stdout).map_err(|err| EngineError::Process {
            detail: format!("invalid yt-dlp --version output: {err}"),
        })?;

        Ok(version.trim().to_string())
    }

    pub fn update_binary(&self) -> Result<String, EngineError> {
        let output = self.run_ytdlp(&["-U"])?;

        if !output.status.success() {
            let stderr = String::from_utf8_lossy(&output.stderr);
            return Err(EngineError::Process {
                detail: format!("yt-dlp update failed: {stderr}"),
            });
        }

        String::from_utf8(output.stdout).map_err(|err| EngineError::Process {
            detail: format!("invalid yt-dlp update output: {err}"),
        })
    }

    fn run_ytdlp(&self, args: &[&str]) -> Result<std::process::Output, EngineError> {
        match Command::new(&self.binary_path).args(args).output() {
            Ok(output) => Ok(output),
            Err(direct_err) => self.run_with_python(args).map_err(|python_err| {
                EngineError::Process {
                    detail: format!(
                        "failed to execute yt-dlp directly: {direct_err}; python fallback failed: {python_err}"
                    ),
                }
            }),
        }
    }

    fn run_with_python(&self, args: &[&str]) -> Result<std::process::Output, EngineError> {
        let module_output = Command::new(&self.python_executable)
            .arg("-m")
            .arg("yt_dlp")
            .args(args)
            .output()
            .map_err(|err| EngineError::Process {
                detail: format!("failed to execute yt-dlp via python module: {err}"),
            })?;

        if module_output.status.success() || !module_missing(&module_output.stderr) {
            return Ok(module_output);
        }

        Command::new(&self.python_executable)
            .arg(&self.binary_path)
            .args(args)
            .output()
            .map_err(|err| EngineError::Process {
                detail: format!("failed to execute yt-dlp via python script: {err}"),
            })
    }
}

fn module_missing(stderr: &[u8]) -> bool {
    let text = String::from_utf8_lossy(stderr).to_ascii_lowercase();
    text.contains("no module named") && text.contains("yt_dlp")
}
