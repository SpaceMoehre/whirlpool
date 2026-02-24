# Setup

## 1. Prerequisites
- Rust toolchain (1.75+)
- Android SDK + NDK
- `cargo-ndk`
- Android Studio (for app module)
- Optional for anti-bot bypass at runtime: Python + `curl-cffi`

## 2. Generate UniFFI Kotlin Bindings
Run from project root:

```bash
scripts/generate_uniffi_kotlin.sh
```

This does:
1. `cargo build -p whirlpool_engine`
2. `uniffi-bindgen generate --library target/debug/libwhirlpool_engine.so --language kotlin --out-dir app/src/main/java`

Generated Kotlin bridge file:
- `app/src/main/java/com/whirlpool/engine/whirlpool_engine.kt`

## 3. Build Android `.so` Libraries (arm64)
Run:

```bash
scripts/build_android_libs.sh
```

This outputs native libs into:
- `app/src/main/jniLibs/arm64-v8a/libwhirlpool_engine.so`

## 4. Android Studio Linking Notes
- `app/build.gradle.kts` already points `jniLibs` to `src/main/jniLibs`.
- UniFFI-generated bindings are in `app/src/main/java/com/whirlpool/engine`.
- App code uses `com.whirlpool.engine.Engine` directly.

## 5. Runtime Configuration
`EngineRepository` provides `EngineConfig` values:
- `apiBaseUrl`: `https://hottubapi.com`
- `dbPath`: app sandbox path (`files/shared/whirlpool.db`)
- `ytDlpPath`: app sandbox path (`files/yt-dlp`)
- `pythonExecutable`: `python3`
- `curlCffiScriptPath`: copied to app files dir from assets
- `ytDlpRepoApi`: GitHub releases endpoint

Ensure `yt-dlp` binary is present and executable inside app sandbox before playback resolution.

## 6. Rust Tests
Run:

```bash
cargo test -p whirlpool_engine
```

## 7. Logcat Workflow (JNI/Rust Error Check)
Before each run:

```bash
adb logcat -c
```

After boot/test run:

```bash
adb logcat -d | rg -i "(rust|panic|jni|nativeactivity|uniffi|ffi)"
```

Helper:

```bash
scripts/logcat_check.sh
```
