#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENGINE_CRATE_DIR="$ROOT_DIR/rust/engine"
OUT_DIR="$ROOT_DIR/app/src/main/java"

cd "$ROOT_DIR"
cargo build -p whirlpool_engine

cargo run -p whirlpool_engine --bin uniffi-bindgen generate \
  --library "$ROOT_DIR/target/debug/libwhirlpool_engine.so" \
  --language kotlin \
  --out-dir "$OUT_DIR" \
  --no-format

