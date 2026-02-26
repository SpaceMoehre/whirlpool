use std::fs;
use std::path::{Path, PathBuf};

use chrono::{DateTime, NaiveDateTime, SecondsFormat, TimeZone, Utc};
use rusqlite::{params, Connection, OptionalExtension};

use crate::errors::EngineError;
use crate::models::{FavoriteItem, ResolvedVideo, SourceServer, VideoItem};

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

        let mut conn = self.conn()?;
        conn.execute_batch(
            r#"
            PRAGMA journal_mode = WAL;
            PRAGMA foreign_keys = ON;

            CREATE TABLE IF NOT EXISTS "user_preferences" (
                "id" TEXT PRIMARY KEY NOT NULL,
                "preferenceValue" TEXT
            );

            CREATE TABLE IF NOT EXISTS "server_preferences" (
                "id" TEXT PRIMARY KEY NOT NULL,
                "preferenceValue" TEXT
            );

            CREATE TABLE IF NOT EXISTS "video_details" (
                "id" TEXT PRIMARY KEY NOT NULL,
                "url" TEXT NOT NULL,
                "title" TEXT,
                "thumb" TEXT,
                "preview" TEXT,
                "dateAdded" TEXT,
                "views" INTEGER DEFAULT (0),
                "duration" INTEGER DEFAULT (0),
                "uploader" TEXT,
                "uploaderUrl" TEXT,
                "tags" TEXT,
                "lastUpdated" TEXT,
                "flags" TEXT,
                "favoriteDate" TEXT,
                "lastWatchDate" TEXT,
                "uploadedAt" TEXT,
                "rating" REAL,
                "userViews" INTEGER,
                "network" TEXT,
                "aspectRatio" REAL,
                "allFormats" TEXT,
                "session" TEXT,
                "rawData" TEXT,
                "cacheDate" TEXT,
                "adData" TEXT
            );

            CREATE TABLE IF NOT EXISTS "categories" (
                "id" TEXT PRIMARY KEY NOT NULL,
                "name" TEXT NOT NULL,
                "clicks" INTEGER NOT NULL DEFAULT (0)
            );

            CREATE TABLE IF NOT EXISTS "searches" (
                "query" TEXT PRIMARY KEY NOT NULL,
                "timestamp" TEXT NOT NULL,
                "frequency" INTEGER NOT NULL DEFAULT (1)
            );
            "#,
        )?;

        Self::migrate_legacy_schema(&mut conn)?;
        Ok(())
    }

    pub fn cache_videos(&self, videos: &[VideoItem]) -> Result<(), EngineError> {
        let now_iso = now_iso();
        let mut conn = self.conn()?;
        let tx = conn.transaction()?;
        {
            let mut stmt = tx.prepare(
                r#"
                INSERT INTO "video_details" (
                    "id", "url", "title", "thumb", "dateAdded", "views", "duration",
                    "uploader", "network", "lastUpdated", "rawData", "cacheDate"
                )
                VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12)
                ON CONFLICT("id") DO UPDATE SET
                    "url" = excluded."url",
                    "title" = excluded."title",
                    "thumb" = excluded."thumb",
                    "views" = excluded."views",
                    "duration" = excluded."duration",
                    "uploader" = excluded."uploader",
                    "network" = excluded."network",
                    "lastUpdated" = excluded."lastUpdated",
                    "rawData" = excluded."rawData",
                    "cacheDate" = excluded."cacheDate"
                "#,
            )?;

            for video in videos {
                let payload = serde_json::to_string(video)?;
                let views = video.view_count.and_then(|count| i64::try_from(count).ok());
                let duration = video.duration_seconds.map(i64::from);
                stmt.execute(params![
                    video.id,
                    video.page_url,
                    video.title,
                    video.image_url,
                    now_iso,
                    views,
                    duration,
                    video.author_name,
                    video.network,
                    now_iso,
                    payload,
                    now_iso
                ])?;
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
        let now_iso = now_iso();
        let conn = self.conn()?;

        let updated = conn.execute(
            r#"
            UPDATE "video_details"
            SET
                "allFormats" = ?1,
                "cacheDate" = ?2,
                "lastUpdated" = ?3,
                "title" = COALESCE(NULLIF(?4, ''), "title")
            WHERE "url" = ?5
            "#,
            params![payload, now_iso, now_iso, video.title, page_url],
        )?;

        if updated == 0 {
            let resolved_id = if video.id.trim().is_empty() {
                format_resolved_cache_id(page_url)
            } else {
                video.id.clone()
            };
            let title = non_empty_str(&video.title).unwrap_or("Resolved Video");
            conn.execute(
                r#"
                INSERT INTO "video_details" (
                    "id", "url", "title", "lastUpdated", "cacheDate", "allFormats", "rawData", "dateAdded"
                )
                VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)
                ON CONFLICT("id") DO UPDATE SET
                    "url" = excluded."url",
                    "title" = excluded."title",
                    "lastUpdated" = excluded."lastUpdated",
                    "cacheDate" = excluded."cacheDate",
                    "allFormats" = excluded."allFormats",
                    "rawData" = excluded."rawData"
                "#,
                params![
                    resolved_id,
                    page_url,
                    title,
                    now_iso,
                    now_iso,
                    payload,
                    payload,
                    now_iso
                ],
            )?;
        }
        Ok(())
    }

    pub fn get_cached_resolved_video(
        &self,
        page_url: &str,
        max_age_seconds: i64,
    ) -> Result<Option<ResolvedVideo>, EngineError> {
        let conn = self.conn()?;
        let row: Option<(String, String)> = conn
            .query_row(
                r#"
                SELECT "allFormats", "cacheDate"
                FROM "video_details"
                WHERE "url" = ?1
                  AND "allFormats" IS NOT NULL
                  AND TRIM("allFormats") <> ''
                ORDER BY "cacheDate" DESC
                LIMIT 1
                "#,
                params![page_url],
                |row| Ok((row.get(0)?, row.get(1)?)),
            )
            .optional()?;

        let Some((payload, cache_date)) = row else {
            return Ok(None);
        };

        let Some(updated_at_epoch) = parse_timestamp_to_epoch_seconds(&cache_date) else {
            return Ok(None);
        };

        let age = Utc::now().timestamp() - updated_at_epoch;
        if age > max_age_seconds {
            return Ok(None);
        }

        Ok(serde_json::from_str::<ResolvedVideo>(&payload).ok())
    }

    pub fn add_favorite(&self, video: &VideoItem) -> Result<FavoriteItem, EngineError> {
        let now = Utc::now().timestamp();
        let now_iso = now_iso();
        let payload = serde_json::to_string(video)?;
        let favorite = FavoriteItem {
            video_id: video.id.clone(),
            title: video.title.clone(),
            image_url: video.image_url.clone(),
            network: video.network.clone(),
            added_at_epoch: now,
        };

        let conn = self.conn()?;
        conn.execute(
            r#"
            INSERT INTO "video_details" (
                "id", "url", "title", "thumb", "dateAdded", "views", "duration",
                "uploader", "network", "lastUpdated", "favoriteDate", "rawData"
            )
            VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12)
            ON CONFLICT("id") DO UPDATE SET
                "url" = excluded."url",
                "title" = excluded."title",
                "thumb" = excluded."thumb",
                "views" = excluded."views",
                "duration" = excluded."duration",
                "uploader" = excluded."uploader",
                "network" = excluded."network",
                "lastUpdated" = excluded."lastUpdated",
                "favoriteDate" = excluded."favoriteDate",
                "rawData" = excluded."rawData"
            "#,
            params![
                favorite.video_id,
                video.page_url,
                favorite.title,
                favorite.image_url,
                now_iso,
                video.view_count.and_then(|count| i64::try_from(count).ok()),
                video.duration_seconds.map(i64::from),
                video.author_name,
                favorite.network,
                now_iso,
                now_iso,
                payload,
            ],
        )?;

        Ok(favorite)
    }

    pub fn remove_favorite(&self, video_id: &str) -> Result<bool, EngineError> {
        let conn = self.conn()?;
        let rows = conn.execute(
            r#"
            UPDATE "video_details"
            SET "favoriteDate" = NULL
            WHERE "id" = ?1
            "#,
            params![video_id],
        )?;
        Ok(rows > 0)
    }

    pub fn list_favorites(&self) -> Result<Vec<FavoriteItem>, EngineError> {
        let conn = self.conn()?;
        let mut stmt = conn.prepare(
            r#"
            SELECT "id", COALESCE("title", ''), "thumb", "network", "favoriteDate"
            FROM "video_details"
            WHERE "favoriteDate" IS NOT NULL
              AND TRIM("favoriteDate") <> ''
            ORDER BY "favoriteDate" DESC
            "#,
        )?;

        let rows = stmt.query_map([], |row| {
            let video_id: String = row.get(0)?;
            let title: String = row.get(1)?;
            let favorite_date: String = row.get(4)?;
            Ok(FavoriteItem {
                video_id: video_id.clone(),
                title: if title.trim().is_empty() {
                    video_id
                } else {
                    title
                },
                image_url: row.get(2)?,
                network: row.get(3)?,
                added_at_epoch: parse_timestamp_to_epoch_seconds(&favorite_date)
                    .unwrap_or_else(|| Utc::now().timestamp()),
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
            r#"
            INSERT INTO "user_preferences" ("id", "preferenceValue")
            VALUES (?1, ?2)
            ON CONFLICT("id") DO UPDATE SET "preferenceValue" = excluded."preferenceValue"
            "#,
            params![key, value],
        )?;
        Ok(())
    }

    pub fn list_meta_with_prefix(&self, prefix: &str) -> Result<Vec<(String, String)>, EngineError> {
        let conn = self.conn()?;
        let mut stmt = conn.prepare(
            r#"
            SELECT "id", COALESCE("preferenceValue", '')
            FROM "user_preferences"
            WHERE "id" LIKE ?1
            ORDER BY "id" ASC
            "#,
        )?;
        let pattern = format!("{prefix}%");
        let rows = stmt.query_map(params![pattern], |row| {
            let key: String = row.get(0)?;
            let value: String = row.get(1)?;
            Ok((key, value))
        })?;

        let mut out = Vec::new();
        for row in rows {
            out.push(row?);
        }
        Ok(out)
    }

    pub fn get_meta(&self, key: &str) -> Result<Option<String>, EngineError> {
        let conn = self.conn()?;
        let val = conn
            .query_row(
                r#"SELECT "preferenceValue" FROM "user_preferences" WHERE "id" = ?1"#,
                params![key],
                |row| row.get::<_, Option<String>>(0),
            )
            .optional()?;
        Ok(val.flatten())
    }

    pub fn upsert_server(&self, server: &SourceServer) -> Result<(), EngineError> {
        let base_url = server.base_url.trim();
        if base_url.is_empty() {
            return Ok(());
        }

        let payload = serde_json::to_string(server)?;
        let conn = self.conn()?;
        conn.execute(
            r#"
            INSERT INTO "server_preferences" ("id", "preferenceValue")
            VALUES (?1, ?2)
            ON CONFLICT("id") DO UPDATE SET "preferenceValue" = excluded."preferenceValue"
            "#,
            params![base_url, payload],
        )?;
        Ok(())
    }

    pub fn remove_server(&self, base_url: &str) -> Result<bool, EngineError> {
        let conn = self.conn()?;
        let removed = conn.execute(
            r#"DELETE FROM "server_preferences" WHERE "id" = ?1"#,
            params![base_url.trim()],
        )?;
        Ok(removed > 0)
    }

    pub fn list_servers(&self) -> Result<Vec<SourceServer>, EngineError> {
        let conn = self.conn()?;
        let mut stmt = conn.prepare(
            r#"
            SELECT "id", COALESCE("preferenceValue", '')
            FROM "server_preferences"
            ORDER BY "id" ASC
            "#,
        )?;
        let rows = stmt.query_map([], |row| {
            let id: String = row.get(0)?;
            let payload: String = row.get(1)?;
            Ok((id, payload))
        })?;

        let mut out = Vec::new();
        for row in rows {
            let (id, payload) = row?;
            let parsed = serde_json::from_str::<SourceServer>(&payload).ok();
            let fallback_title = id
                .trim()
                .trim_start_matches("https://")
                .trim_start_matches("http://")
                .to_string();
            out.push(parsed.unwrap_or(SourceServer {
                base_url: id,
                title: if fallback_title.is_empty() {
                    "Source".to_string()
                } else {
                    fallback_title
                },
                color: None,
                icon_url: None,
            }));
        }
        Ok(out)
    }

    pub fn clear_cache_data(&self) -> Result<u64, EngineError> {
        let conn = self.conn()?;
        let rows = conn.execute(
            r#"
            DELETE FROM "video_details"
            WHERE "favoriteDate" IS NULL OR TRIM("favoriteDate") = ''
            "#,
            [],
        )?;
        Ok(rows as u64)
    }

    pub fn clear_watch_history(&self) -> Result<u64, EngineError> {
        let conn = self.conn()?;
        let rows = conn.execute(
            r#"
            UPDATE "video_details"
            SET "lastWatchDate" = NULL
            WHERE "lastWatchDate" IS NOT NULL AND TRIM("lastWatchDate") <> ''
            "#,
            [],
        )?;
        Ok(rows as u64)
    }

    pub fn clear_favorites(&self) -> Result<u64, EngineError> {
        let conn = self.conn()?;
        let rows = conn.execute(
            r#"
            UPDATE "video_details"
            SET "favoriteDate" = NULL
            WHERE "favoriteDate" IS NOT NULL AND TRIM("favoriteDate") <> ''
            "#,
            [],
        )?;
        Ok(rows as u64)
    }

    pub fn clear_achievements(&self) -> Result<u64, EngineError> {
        let conn = self.conn()?;
        let rows = conn.execute(
            r#"
            DELETE FROM "user_preferences"
            WHERE "id" LIKE 'achievement.%'
               OR "id" LIKE 'stats.%'
               OR "id" LIKE 'badges.%'
            "#,
            [],
        )?;
        Ok(rows as u64)
    }

    pub fn reset_all_data(&self) -> Result<(), EngineError> {
        let mut conn = self.conn()?;
        let tx = conn.transaction()?;
        tx.execute(r#"DELETE FROM "video_details""#, [])?;
        tx.execute(r#"DELETE FROM "searches""#, [])?;
        tx.execute(r#"DELETE FROM "categories""#, [])?;
        tx.execute(r#"DELETE FROM "user_preferences""#, [])?;
        tx.execute(r#"DELETE FROM "server_preferences""#, [])?;
        tx.commit()?;
        Ok(())
    }

    pub fn sync_categories(&self, categories: &[String]) -> Result<(), EngineError> {
        let mut conn = self.conn()?;
        let tx = conn.transaction()?;
        {
            let mut stmt = tx.prepare(
                r#"
                INSERT INTO "categories" ("id", "name")
                VALUES (?1, ?2)
                ON CONFLICT("id") DO UPDATE SET "name" = excluded."name"
                "#,
            )?;

            for category in categories {
                let trimmed = category.trim();
                if trimmed.is_empty() {
                    continue;
                }
                stmt.execute(params![trimmed, trimmed])?;
            }
        }
        tx.commit()?;
        Ok(())
    }

    pub fn record_search(&self, query: &str) -> Result<(), EngineError> {
        let conn = self.conn()?;
        let timestamp = now_iso();
        conn.execute(
            r#"
            INSERT INTO "searches" ("query", "timestamp", "frequency")
            VALUES (?1, ?2, 1)
            ON CONFLICT("query") DO UPDATE SET
                "timestamp" = excluded."timestamp",
                "frequency" = "searches"."frequency" + 1
            "#,
            params![query, timestamp],
        )?;

        conn.execute(
            r#"
            UPDATE "categories"
            SET "clicks" = "clicks" + 1
            WHERE lower("name") = lower(?1) OR lower("id") = lower(?1)
            "#,
            params![query],
        )?;

        Ok(())
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

    fn migrate_legacy_schema(conn: &mut Connection) -> Result<(), EngineError> {
        if Self::table_exists(conn, "engine_meta")? {
            Self::migrate_legacy_meta(conn)?;
        }
        if Self::table_exists(conn, "video_cache")? {
            Self::migrate_legacy_video_cache(conn)?;
        }
        if Self::table_exists(conn, "favorites")? {
            Self::migrate_legacy_favorites(conn)?;
        }
        if Self::table_exists(conn, "resolved_cache")? {
            Self::migrate_legacy_resolved_cache(conn)?;
        }
        Ok(())
    }

    fn table_exists(conn: &Connection, table_name: &str) -> Result<bool, EngineError> {
        let exists = conn
            .query_row(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?1 LIMIT 1",
                params![table_name],
                |_| Ok(()),
            )
            .optional()?
            .is_some();
        Ok(exists)
    }

    fn migrate_legacy_meta(conn: &Connection) -> Result<(), EngineError> {
        let mut stmt = conn.prepare("SELECT key, value FROM engine_meta")?;
        let rows = stmt.query_map([], |row| {
            let key: String = row.get(0)?;
            let value: String = row.get(1)?;
            Ok((key, value))
        })?;

        for row in rows {
            let (key, value) = row?;
            conn.execute(
                r#"
                INSERT INTO "user_preferences" ("id", "preferenceValue")
                VALUES (?1, ?2)
                ON CONFLICT("id") DO UPDATE SET "preferenceValue" = excluded."preferenceValue"
                "#,
                params![key, value],
            )?;
        }
        Ok(())
    }

    fn migrate_legacy_video_cache(conn: &Connection) -> Result<(), EngineError> {
        let mut stmt =
            conn.prepare("SELECT video_id, payload_json, updated_at FROM video_cache")?;
        let rows = stmt.query_map([], |row| {
            let video_id: String = row.get(0)?;
            let payload_json: String = row.get(1)?;
            let updated_at: i64 = row.get(2)?;
            Ok((video_id, payload_json, updated_at))
        })?;

        for row in rows {
            let (video_id, payload_json, updated_at) = row?;
            let updated_iso = epoch_seconds_to_iso(updated_at);
            let parsed = serde_json::from_str::<VideoItem>(&payload_json).ok();

            let (url, title, thumb, views, duration, uploader, network) =
                if let Some(video) = parsed {
                    (
                        video.page_url,
                        video.title,
                        video.image_url,
                        video.view_count.and_then(|count| i64::try_from(count).ok()),
                        video.duration_seconds.map(i64::from),
                        video.author_name,
                        video.network,
                    )
                } else {
                    (
                        fallback_url(&video_id),
                        video_id.clone(),
                        None,
                        None,
                        None,
                        None,
                        None,
                    )
                };

            conn.execute(
                r#"
                INSERT INTO "video_details" (
                    "id", "url", "title", "thumb", "dateAdded", "views", "duration",
                    "uploader", "network", "lastUpdated", "rawData", "cacheDate"
                )
                VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12)
                ON CONFLICT("id") DO UPDATE SET
                    "url" = excluded."url",
                    "title" = excluded."title",
                    "thumb" = excluded."thumb",
                    "views" = excluded."views",
                    "duration" = excluded."duration",
                    "uploader" = excluded."uploader",
                    "network" = excluded."network",
                    "lastUpdated" = excluded."lastUpdated",
                    "rawData" = excluded."rawData",
                    "cacheDate" = excluded."cacheDate"
                "#,
                params![
                    video_id,
                    url,
                    title,
                    thumb,
                    updated_iso,
                    views,
                    duration,
                    uploader,
                    network,
                    updated_iso,
                    payload_json,
                    updated_iso
                ],
            )?;
        }
        Ok(())
    }

    fn migrate_legacy_favorites(conn: &Connection) -> Result<(), EngineError> {
        let mut stmt =
            conn.prepare("SELECT video_id, title, image_url, network, added_at FROM favorites")?;
        let rows = stmt.query_map([], |row| {
            let video_id: String = row.get(0)?;
            let title: String = row.get(1)?;
            let image_url: Option<String> = row.get(2)?;
            let network: Option<String> = row.get(3)?;
            let added_at: i64 = row.get(4)?;
            Ok((video_id, title, image_url, network, added_at))
        })?;

        for row in rows {
            let (video_id, title, image_url, network, added_at) = row?;
            let favorite_date = epoch_seconds_to_iso(added_at);
            let existing_url: Option<String> = conn
                .query_row(
                    r#"SELECT "url" FROM "video_details" WHERE "id" = ?1"#,
                    params![video_id],
                    |r| r.get(0),
                )
                .optional()?;

            conn.execute(
                r#"
                INSERT INTO "video_details" (
                    "id", "url", "title", "thumb", "network", "favoriteDate", "lastUpdated", "dateAdded"
                )
                VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)
                ON CONFLICT("id") DO UPDATE SET
                    "title" = excluded."title",
                    "thumb" = excluded."thumb",
                    "network" = excluded."network",
                    "favoriteDate" = excluded."favoriteDate",
                    "lastUpdated" = excluded."lastUpdated"
                "#,
                params![
                    video_id,
                    existing_url.unwrap_or_else(|| fallback_url(&video_id)),
                    title,
                    image_url,
                    network,
                    favorite_date,
                    favorite_date,
                    favorite_date
                ],
            )?;
        }
        Ok(())
    }

    fn migrate_legacy_resolved_cache(conn: &Connection) -> Result<(), EngineError> {
        let mut stmt =
            conn.prepare("SELECT page_url, payload_json, updated_at FROM resolved_cache")?;
        let rows = stmt.query_map([], |row| {
            let page_url: String = row.get(0)?;
            let payload_json: String = row.get(1)?;
            let updated_at: i64 = row.get(2)?;
            Ok((page_url, payload_json, updated_at))
        })?;

        for row in rows {
            let (page_url, payload_json, updated_at) = row?;
            let updated_iso = epoch_seconds_to_iso(updated_at);
            let parsed = serde_json::from_str::<ResolvedVideo>(&payload_json).ok();
            let id = parsed
                .as_ref()
                .map(|video| video.id.trim())
                .filter(|id| !id.is_empty())
                .map(ToOwned::to_owned)
                .unwrap_or_else(|| format_resolved_cache_id(&page_url));
            let title = parsed
                .as_ref()
                .map(|video| video.title.trim())
                .filter(|title| !title.is_empty())
                .unwrap_or("Resolved Video");

            let updated = conn.execute(
                r#"
                UPDATE "video_details"
                SET
                    "allFormats" = ?1,
                    "cacheDate" = ?2,
                    "lastUpdated" = ?3,
                    "title" = COALESCE(NULLIF(?4, ''), "title")
                WHERE "url" = ?5
                "#,
                params![payload_json, updated_iso, updated_iso, title, page_url],
            )?;

            if updated == 0 {
                conn.execute(
                    r#"
                    INSERT INTO "video_details" (
                        "id", "url", "title", "lastUpdated", "cacheDate", "allFormats", "rawData", "dateAdded"
                    )
                    VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)
                    ON CONFLICT("id") DO UPDATE SET
                        "url" = excluded."url",
                        "title" = excluded."title",
                        "lastUpdated" = excluded."lastUpdated",
                        "cacheDate" = excluded."cacheDate",
                        "allFormats" = excluded."allFormats",
                        "rawData" = excluded."rawData"
                    "#,
                    params![
                        id,
                        page_url,
                        title,
                        updated_iso,
                        updated_iso,
                        payload_json,
                        payload_json,
                        updated_iso
                    ],
                )?;
            }
        }
        Ok(())
    }
}

fn now_iso() -> String {
    Utc::now().to_rfc3339_opts(SecondsFormat::Millis, true)
}

fn epoch_seconds_to_iso(epoch_seconds: i64) -> String {
    Utc.timestamp_opt(epoch_seconds, 0)
        .single()
        .map(|time| time.to_rfc3339_opts(SecondsFormat::Millis, true))
        .unwrap_or_else(now_iso)
}

fn parse_timestamp_to_epoch_seconds(value: &str) -> Option<i64> {
    let trimmed = value.trim();
    if trimmed.is_empty() {
        return None;
    }

    if let Ok(raw_number) = trimmed.parse::<i64>() {
        if raw_number > 10_000_000_000 {
            return Some(raw_number / 1000);
        }
        return Some(raw_number);
    }

    if let Ok(parsed) = DateTime::parse_from_rfc3339(trimmed) {
        return Some(parsed.timestamp());
    }

    const CANDIDATE_FORMATS: [&str; 3] = [
        "%Y-%m-%dT%H:%M:%S%.f",
        "%Y-%m-%d %H:%M:%S%.f",
        "%Y-%m-%dT%H:%M:%S",
    ];

    for format in CANDIDATE_FORMATS {
        if let Ok(parsed) = NaiveDateTime::parse_from_str(trimmed, format) {
            return Some(Utc.from_utc_datetime(&parsed).timestamp());
        }
    }

    None
}

fn fallback_url(video_id: &str) -> String {
    format!("local://video/{video_id}")
}

fn format_resolved_cache_id(page_url: &str) -> String {
    format!("resolved:{page_url}")
}

fn non_empty_str(value: &str) -> Option<&str> {
    let trimmed = value.trim();
    if trimmed.is_empty() {
        None
    } else {
        Some(trimmed)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use rusqlite::Connection;
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
    fn template_schema_tables_exist() {
        let tmp = tempdir().expect("tmpdir");
        let db = Database::new(tmp.path().join("schema.sqlite"));
        db.init().expect("db init");

        let conn = Connection::open(db.path()).expect("open db");
        let mut stmt = conn
            .prepare("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name")
            .expect("prepare list tables");
        let names = stmt
            .query_map([], |row| row.get::<_, String>(0))
            .expect("query tables")
            .collect::<Result<Vec<_>, _>>()
            .expect("collect tables");

        assert!(names.iter().any(|name| name == "video_details"));
        assert!(names.iter().any(|name| name == "searches"));
        assert!(names.iter().any(|name| name == "categories"));
        assert!(names.iter().any(|name| name == "user_preferences"));
        assert!(names.iter().any(|name| name == "server_preferences"));
    }

    #[test]
    fn searches_and_category_clicks_roundtrip() {
        let tmp = tempdir().expect("tmpdir");
        let db = Database::new(tmp.path().join("search.sqlite"));
        db.init().expect("db init");

        db.sync_categories(&["Amateur".to_string(), "Professional".to_string()])
            .expect("sync categories");
        db.record_search("Amateur").expect("record first search");
        db.record_search("Amateur").expect("record second search");

        let conn = Connection::open(db.path()).expect("open db");
        let frequency: i64 = conn
            .query_row(
                r#"SELECT "frequency" FROM "searches" WHERE "query" = 'Amateur'"#,
                [],
                |row| row.get(0),
            )
            .expect("query search frequency");
        assert_eq!(frequency, 2);

        let clicks: i64 = conn
            .query_row(
                r#"SELECT "clicks" FROM "categories" WHERE "id" = 'Amateur'"#,
                [],
                |row| row.get(0),
            )
            .expect("query category clicks");
        assert_eq!(clicks, 2);
    }

    #[test]
    fn import_template_schema_keeps_favorites_compatible() {
        let tmp = tempdir().expect("tmpdir");
        let template_path = tmp.path().join("template.sqlite");
        let conn = Connection::open(&template_path).expect("create template db");
        conn.execute_batch(
            r#"
            CREATE TABLE IF NOT EXISTS "user_preferences" ("id" TEXT PRIMARY KEY NOT NULL, "preferenceValue" TEXT);
            CREATE TABLE IF NOT EXISTS "server_preferences" ("id" TEXT PRIMARY KEY NOT NULL, "preferenceValue" TEXT);
            CREATE TABLE IF NOT EXISTS "video_details" ("id" TEXT PRIMARY KEY NOT NULL, "url" TEXT NOT NULL, "title" TEXT, "thumb" TEXT, "preview" TEXT, "dateAdded" TEXT, "views" INTEGER DEFAULT (0), "duration" INTEGER DEFAULT (0), "uploader" TEXT, "uploaderUrl" TEXT, "tags" TEXT, "lastUpdated" TEXT, "flags" TEXT, "favoriteDate" TEXT, "lastWatchDate" TEXT, "uploadedAt" TEXT, "rating" REAL, "userViews" INTEGER, "network" TEXT, "aspectRatio" REAL, "allFormats" TEXT, "session" TEXT, "rawData" TEXT, "cacheDate" TEXT, "adData" TEXT);
            CREATE TABLE IF NOT EXISTS "categories" ("id" TEXT PRIMARY KEY NOT NULL, "name" TEXT NOT NULL, "clicks" INTEGER NOT NULL DEFAULT (0));
            CREATE TABLE IF NOT EXISTS "searches" ("query" TEXT PRIMARY KEY NOT NULL, "timestamp" TEXT NOT NULL, "frequency" INTEGER NOT NULL DEFAULT (1));
            "#,
        )
        .expect("create template tables");
        conn.execute(
            r#"
            INSERT INTO "video_details" ("id", "url", "title", "thumb", "network", "favoriteDate")
            VALUES (?1, ?2, ?3, ?4, ?5, ?6)
            "#,
            params![
                "video-42",
                "https://example.com/v/42",
                "Template favorite",
                "https://example.com/42.jpg",
                "example-network",
                "2025-11-30T20:40:00Z"
            ],
        )
        .expect("seed template favorite");

        let imported = Database::new(tmp.path().join("imported.sqlite"));
        imported.init().expect("import target init");
        imported
            .import_from(template_path.to_str().expect("template path utf8"))
            .expect("import template");

        let favorites = imported.list_favorites().expect("list favorites");
        assert_eq!(favorites.len(), 1);
        assert_eq!(favorites[0].video_id, "video-42");
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

    #[test]
    fn server_preferences_roundtrip() {
        let tmp = tempdir().expect("tmpdir");
        let db = Database::new(tmp.path().join("servers.sqlite"));
        db.init().expect("db init");

        db.upsert_server(&SourceServer {
            base_url: "https://getfigleaf.com".to_string(),
            title: "Fig Leaf".to_string(),
            color: Some("#478003".to_string()),
            icon_url: Some("https://cdn.example.com/figleaf.png".to_string()),
        })
        .expect("upsert server");
        db.upsert_server(&SourceServer {
            base_url: "https://example.com".to_string(),
            title: "Example".to_string(),
            color: None,
            icon_url: None,
        })
        .expect("upsert second server");

        let servers = db.list_servers().expect("list servers");
        assert_eq!(servers.len(), 2);
        assert!(servers.iter().any(|server| {
            server.base_url == "https://getfigleaf.com"
                && server.title == "Fig Leaf"
                && server.color.as_deref() == Some("#478003")
                && server.icon_url.as_deref() == Some("https://cdn.example.com/figleaf.png")
        }));

        let removed = db
            .remove_server("https://example.com")
            .expect("remove server");
        assert!(removed);
        assert_eq!(db.list_servers().expect("list servers").len(), 1);
    }

    #[test]
    fn reset_all_data_clears_tables() {
        let tmp = tempdir().expect("tmpdir");
        let db = Database::new(tmp.path().join("reset.sqlite"));
        db.init().expect("db init");

        db.add_favorite(&sample_video("video-reset"))
            .expect("add favorite");
        db.set_meta("settings.theme", "dark").expect("set meta");
        db.upsert_server(&SourceServer {
            base_url: "https://getfigleaf.com".to_string(),
            title: "Fig Leaf".to_string(),
            color: None,
            icon_url: None,
        })
        .expect("set server");

        db.reset_all_data().expect("reset all");

        let conn = Connection::open(db.path()).expect("open db");
        let count_video_details: i64 = conn
            .query_row(r#"SELECT COUNT(*) FROM "video_details""#, [], |row| row.get(0))
            .expect("count video_details");
        let count_meta: i64 = conn
            .query_row(r#"SELECT COUNT(*) FROM "user_preferences""#, [], |row| row.get(0))
            .expect("count user_preferences");
        let count_servers: i64 = conn
            .query_row(r#"SELECT COUNT(*) FROM "server_preferences""#, [], |row| row.get(0))
            .expect("count server_preferences");
        assert_eq!(count_video_details, 0);
        assert_eq!(count_meta, 0);
        assert_eq!(count_servers, 0);
    }
}
