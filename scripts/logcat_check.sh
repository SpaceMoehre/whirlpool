#!/usr/bin/env bash
set -euo pipefail

adb logcat -c
echo "Logcat cleared. Run your app now."
sleep 1
adb logcat -d | rg -i "(rust|panic|jni|nativeactivity|uniffi|ffi)"
