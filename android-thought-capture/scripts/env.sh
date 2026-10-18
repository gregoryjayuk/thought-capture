#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

export JAVA_HOME="$ROOT/.tools/jdk/Contents/Home"
export ANDROID_SDK_ROOT="$ROOT/android-sdk"
export GRADLE_USER_HOME="$ROOT/.gradle-home"
export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"
