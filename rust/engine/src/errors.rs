#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum EngineError {
    #[error("invalid config: {detail}")]
    InvalidConfig { detail: String },
    #[error("network failure: {detail}")]
    Network { detail: String },
    #[error("database failure: {detail}")]
    Database { detail: String },
    #[error("serialization failure: {detail}")]
    Serialization { detail: String },
    #[error("process failure: {detail}")]
    Process { detail: String },
    #[error("not found: {detail}")]
    NotFound { detail: String },
}

impl From<rusqlite::Error> for EngineError {
    fn from(value: rusqlite::Error) -> Self {
        Self::Database {
            detail: value.to_string(),
        }
    }
}

impl From<serde_json::Error> for EngineError {
    fn from(value: serde_json::Error) -> Self {
        Self::Serialization {
            detail: value.to_string(),
        }
    }
}
