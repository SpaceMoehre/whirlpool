#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_JNI_DIR="$ROOT_DIR/app/src/main/jniLibs"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$ANDROID_SDK_ROOT/ndk/29.0.14206865}"

mkdir -p "$ANDROID_JNI_DIR"

cd "$ROOT_DIR/rust/engine"

export ANDROID_SDK_ROOT
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export ANDROID_NDK_HOME

# Build native libs for physical arm64 devices and x86_64 emulators.
cargo ndk \
  -t arm64-v8a \
  -t x86_64 \
  -o "$ANDROID_JNI_DIR" \
  build --release
