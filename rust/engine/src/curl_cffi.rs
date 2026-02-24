use std::process::Command;

use crate::errors::EngineError;

pub fn fetch_with_curl_cffi(
    python_executable: &str,
    script_path: &str,
    method: &str,
    url: &str,
    json_body: Option<&str>,
) -> Result<String, EngineError> {
    let payload = json_body.unwrap_or("{}");

    let output = Command::new(python_executable)
        .arg(script_path)
        .arg(method)
        .arg(url)
        .arg(payload)
        .output()
        .map_err(|err| EngineError::Process {
            detail: format!("failed to execute curl-cffi bridge: {err}"),
        })?;

    if !output.status.success() {
        let stderr = String::from_utf8_lossy(&output.stderr);
        return Err(EngineError::Process {
            detail: format!("curl-cffi bridge failed: {stderr}"),
        });
    }

    String::from_utf8(output.stdout).map_err(|err| EngineError::Process {
        detail: format!("curl-cffi bridge output was not utf8: {err}"),
    })
}
