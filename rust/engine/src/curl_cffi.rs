use std::collections::HashMap;
use std::process::Command;

use crate::errors::EngineError;

pub fn fetch_with_curl_cffi(
    python_executable: &str,
    script_path: &str,
    url: &str,
    query: &[(&str, String)],
) -> Result<String, EngineError> {
    let mut payload = HashMap::new();
    for (key, value) in query {
        payload.insert((*key).to_string(), value.clone());
    }

    let query_json = serde_json::to_string(&payload).map_err(|err| EngineError::Serialization {
        detail: format!("failed to encode curl-cffi query payload: {err}"),
    })?;

    let output = Command::new(python_executable)
        .arg(script_path)
        .arg(url)
        .arg(query_json)
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
