use std::fs;
use std::path::{Path, PathBuf};

use chrono::Utc;
use rusqlite::{params, Connection, OptionalExtension};

use crate::errors::EngineError;
use crate::models::{FavoriteItem, ResolvedVideo, VideoItem};

#[derive(Debug, Clone)]
pub struct Database {
    path: PathBuf,
}

impl Database {
    pub fn new(path: impl Into<PathBuf>) -> Self {
        Self { path: path.into() }
    }

    pub fn path(&self) -> &Path {
        &self.path
    }

    pub fn init(&self) -> Result<(), EngineError> {
        if let Some(parent) = self.path.parent() {
            fs::create_dir_all(parent).map_err(|err| EngineError::Database {
                detail: format!("failed creating db parent directory: {err}"),
            })?;
        }

        let conn = self.conn()?;
        conn.execute_batch(
            r#"
            PRAGMA journal_mode = WAL;
            PRAGMA foreign_keys = ON;

            CREATE TABLE IF NOT EXISTS video_cache (
                video_id TEXT PRIMARY KEY,
                payload_json TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS resolved_cache (
                page_url TEXT PRIMARY KEY,
                payload_json TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS favorites (
                video_id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                image_url TEXT,
                network TEXT,
                added_at INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS engine_meta (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            );
            "#,
        )?;
        Ok(())
    }

    pub fn cache_videos(&self, videos: &[VideoItem]) -> Result<(), EngineError> {
        let now = Utc::now().timestamp();
        let mut conn = self.conn()?;
        let tx = conn.transaction()?;
        {
            let mut stmt = tx.prepare(
                "INSERT INTO video_cache(video_id, payload_json, updated_at)
                 VALUES (?1, ?2, ?3)
                 ON CONFLICT(video_id) DO UPDATE SET payload_json = excluded.payload_json, updated_at = excluded.updated_at",
            )?;

            for video in videos {
                let payload = serde_json::to_string(video)?;
                stmt.execute(params![video.id, payload, now])?;
            }
        }
        tx.commit()?;
        Ok(())
    }

    pub fn cache_resolved_video(
        &self,
        page_url: &str,
        video: &ResolvedVideo,
    ) -> Result<(), EngineError> {
        let payload = serde_json::to_string(video)?;
        let conn = self.conn()?;
        conn.execute(
            "INSERT INTO resolved_cache(page_url, payload_json, updated_at)
             VALUES (?1, ?2, ?3)
             ON CONFLICT(page_url) DO UPDATE SET payload_json = excluded.payload_json, updated_at = excluded.updated_at",
            params![page_url, payload, Utc::now().timestamp()],
        )?;
        Ok(())
    }

    pub fn get_cached_resolved_video(
        &self,
        page_url: &str,
        max_age_seconds: i64,
    ) -> Result<Option<ResolvedVideo>, EngineError> {
        let conn = self.conn()?;
        let row: Option<(String, i64)> = conn
            .query_row(
                "SELECT payload_json, updated_at FROM resolved_cache WHERE page_url = ?1",
                params![page_url],
                |row| Ok((row.get(0)?, row.get(1)?)),
            )
            .optional()?;

        let Some((payload, updated_at)) = row else {
            return Ok(None);
        };

        let age = Utc::now().timestamp() - updated_at;
        if age > max_age_seconds {
            return Ok(None);
        }

        let parsed = serde_json::from_str::<ResolvedVideo>(&payload)?;
        Ok(Some(parsed))
    }

    pub fn add_favorite(&self, video: &VideoItem) -> Result<FavoriteItem, EngineError> {
        let now = Utc::now().timestamp();
        let favorite = FavoriteItem {
            video_id: video.id.clone(),
            title: video.title.clone(),
            image_url: video.image_url.clone(),
            network: video.network.clone(),
            added_at_epoch: now,
        };

        let conn = self.conn()?;
        conn.execute(
            "INSERT INTO favorites(video_id, title, image_url, network, added_at)
             VALUES (?1, ?2, ?3, ?4, ?5)
             ON CONFLICT(video_id) DO UPDATE SET title = excluded.title, image_url = excluded.image_url, network = excluded.network",
            params![
                favorite.video_id,
                favorite.title,
                favorite.image_url,
                favorite.network,
                favorite.added_at_epoch,
            ],
        )?;

        Ok(favorite)
    }

    pub fn remove_favorite(&self, video_id: &str) -> Result<bool, EngineError> {
        let conn = self.conn()?;
        let rows = conn.execute(
            "DELETE FROM favorites WHERE video_id = ?1",
            params![video_id],
        )?;
        Ok(rows > 0)
    }

    pub fn list_favorites(&self) -> Result<Vec<FavoriteItem>, EngineError> {
        let conn = self.conn()?;
        let mut stmt = conn.prepare(
            "SELECT video_id, title, image_url, network, added_at
             FROM favorites
             ORDER BY added_at DESC",
        )?;

        let rows = stmt.query_map([], |row| {
            Ok(FavoriteItem {
                video_id: row.get(0)?,
                title: row.get(1)?,
                image_url: row.get(2)?,
                network: row.get(3)?,
                added_at_epoch: row.get(4)?,
            })
        })?;

        let mut out = Vec::new();
        for row in rows {
            out.push(row?);
        }
        Ok(out)
    }

    pub fn set_meta(&self, key: &str, value: &str) -> Result<(), EngineError> {
        let conn = self.conn()?;
        conn.execute(
            "INSERT INTO engine_meta(key, value, updated_at)
             VALUES (?1, ?2, ?3)
             ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at",
            params![key, value, Utc::now().timestamp()],
        )?;
        Ok(())
    }

    pub fn get_meta(&self, key: &str) -> Result<Option<String>, EngineError> {
        let conn = self.conn()?;
        let val = conn
            .query_row(
                "SELECT value FROM engine_meta WHERE key = ?1",
                params![key],
                |row| row.get::<_, String>(0),
            )
            .optional()?;
        Ok(val)
    }

    pub fn export_to(&self, export_path: &str) -> Result<bool, EngineError> {
        let export = PathBuf::from(export_path);
        if let Some(parent) = export.parent() {
            fs::create_dir_all(parent).map_err(|err| EngineError::Database {
                detail: format!("failed creating export directory: {err}"),
            })?;
        }

        fs::copy(&self.path, export).map_err(|err| EngineError::Database {
            detail: format!("failed to export database: {err}"),
        })?;

        Ok(true)
    }

    pub fn import_from(&self, import_path: &str) -> Result<bool, EngineError> {
        let import = PathBuf::from(import_path);
        if !import.exists() {
            return Err(EngineError::NotFound {
                detail: format!("import file does not exist: {}", import.display()),
            });
        }

        if let Some(parent) = self.path.parent() {
            fs::create_dir_all(parent).map_err(|err| EngineError::Database {
                detail: format!("failed creating database directory: {err}"),
            })?;
        }

        fs::copy(import, &self.path).map_err(|err| EngineError::Database {
            detail: format!("failed to import database: {err}"),
        })?;

        self.init()?;
        Ok(true)
    }

    fn conn(&self) -> Result<Connection, EngineError> {
        Connection::open(&self.path).map_err(EngineError::from)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    fn sample_video(id: &str) -> VideoItem {
        VideoItem {
            id: id.to_string(),
            title: "Sample".to_string(),
            page_url: "https://example.com/v/1".to_string(),
            duration_seconds: Some(120),
            image_url: Some("https://example.com/image.jpg".to_string()),
            network: Some("youtube".to_string()),
            author_name: Some("author".to_string()),
            extractor: Some("youtube".to_string()),
            view_count: Some(42),
        }
    }

    #[test]
    fn favorites_roundtrip() {
        let tmp = tempdir().expect("tmpdir");
        let db = Database::new(tmp.path().join("db.sqlite"));
        db.init().expect("db init");

        let favorite = db
            .add_favorite(&sample_video("video-1"))
            .expect("add favorite");
        assert_eq!(favorite.video_id, "video-1");

        let favorites = db.list_favorites().expect("list favorites");
        assert_eq!(favorites.len(), 1);
        assert_eq!(favorites[0].video_id, "video-1");

        let removed = db.remove_favorite("video-1").expect("remove favorite");
        assert!(removed);
        assert!(db.list_favorites().expect("list favorites").is_empty());
    }

    #[test]
    fn export_and_import_roundtrip() {
        let tmp = tempdir().expect("tmpdir");
        let src = Database::new(tmp.path().join("src.sqlite"));
        src.init().expect("src init");
        src.add_favorite(&sample_video("video-2"))
            .expect("add favorite");

        let export_path = tmp.path().join("exports/backup.sqlite");
        src.export_to(export_path.to_str().expect("export path utf8"))
            .expect("export");

        let imported = Database::new(tmp.path().join("dst.sqlite"));
        imported.init().expect("dst init");
        imported
            .import_from(export_path.to_str().expect("export path utf8"))
            .expect("import");

        let favorites = imported.list_favorites().expect("list favorites");
        assert_eq!(favorites.len(), 1);
        assert_eq!(favorites[0].video_id, "video-2");
    }
}
