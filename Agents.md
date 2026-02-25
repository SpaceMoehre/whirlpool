# Whirlpool Agents Notes

## Project Architecture
- `rust/engine`: Rust `whirlpool_engine` core (`staticlib` + `cdylib`) exposed with UniFFI.
- `app`: Native Android UI written in Kotlin + Jetpack Compose + Media3.
- `scripts`: Build and operational helpers for UniFFI generation, Android cross-compile, and logcat checks.

### Runtime Data Flow
1. Compose `WhirlpoolViewModel` calls `EngineRepository`.
2. `EngineRepository` invokes UniFFI Kotlin bindings (`com.whirlpool.engine.*`).
3. Rust `Engine` executes API discovery, yt-dlp resolution, update checks, and SQLite reads/writes.
4. Kotlin receives clean Data Objects and renders UI / controls Media3 playback.

### Rust Engine Modules
- `api.rs`: `/api/status` + `/api/videos` discovery (`/api/video` fallback).
- `curl_cffi.rs`: Python bridge invocation for curl-cffi browser impersonation.
- `ytdlp.rs`: yt-dlp extraction (`-J`) and update command (`-U`) support.
- `updater.rs`: boot-time GitHub release checks for official `yt-dlp/yt-dlp`.
- `db.rs`: shared SQLite schema for cache, favorites, engine metadata, plus import/export.
- `lib.rs`: UniFFI object export and public bridge methods.

## Tech Stack
- Rust 1.75+
- UniFFI (Kotlin bindings)
- Kotlin + Jetpack Compose (Material3)
- Android Media3 / ExoPlayer
- SQLite (owned by Rust core via `rusqlite`)
- `cargo-ndk` for Android ABI build output

## Bridge Mapping
- Rust `Engine` object -> Kotlin `Engine` class.
- Rust `EngineConfig` record -> Kotlin `EngineConfig` data class.
- Rust `StatusSummary` -> Kotlin `StatusSummary`.
- Rust `VideoItem` -> Kotlin `VideoItem`.
- Rust `ResolvedVideo` -> Kotlin `ResolvedVideo`.
- Rust `FavoriteItem` -> Kotlin `FavoriteItem`.
- Rust `YtDlpUpdateInfo` -> Kotlin `YtDlpUpdateInfo`.
- Rust `EngineError` -> Kotlin `EngineException` sealed type.

## FFI Safety Constraints
- `Engine` is `Send + Sync` asserted at compile-time in Rust.
- Kotlin only consumes UniFFI-generated interfaces; no direct JNI pointer handling in app code.
- SQLite write/read ownership remains in Rust; Kotlin never writes DB files directly.

