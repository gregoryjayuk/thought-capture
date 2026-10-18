#!/usr/bin/env bash
set -euo pipefail

# Every setting can be overridden from the environment, e.g.
#   PORT=5050 MODEL=/path/to/ggml-base.en.bin ./run_demo.sh
ROOT="$(cd "$(dirname "$0")" && pwd)"
WHISPER_DIR="${WHISPER_DIR:-$ROOT/third_party/whisper.cpp}"
WHISPER_SERVER="${WHISPER_SERVER:-$WHISPER_DIR/build/bin/whisper-server}"
MODEL="${MODEL:-$WHISPER_DIR/models/ggml-large-v3-turbo-q5_0.bin}"
PUBLIC_DIR="${PUBLIC_DIR:-$ROOT/public}"
HOST="${HOST:-127.0.0.1}"
PORT="${PORT:-5001}"
LANGUAGE="${LANGUAGE:-en}"

if [ ! -x "$WHISPER_SERVER" ]; then
  echo "Missing whisper-server binary at: $WHISPER_SERVER (see README: Build whisper.cpp)"
  exit 1
fi

if [ ! -f "$MODEL" ]; then
  echo "Missing model file at: $MODEL (see README: Download the model)"
  exit 1
fi

if ! command -v ffmpeg >/dev/null 2>&1; then
  echo "ffmpeg is required but was not found in PATH"
  exit 1
fi

echo "Starting whisper-server on http://$HOST:$PORT"
exec "$WHISPER_SERVER" \
  -m "$MODEL" \
  --host "$HOST" \
  --port "$PORT" \
  --public "$PUBLIC_DIR" \
  --convert \
  -nt \
  -l "$LANGUAGE" \
  -ng
