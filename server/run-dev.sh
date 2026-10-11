#!/usr/bin/env bash
# Starts the FitHall server for local development (macOS/Linux):  ./server/run-dev.sh
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -z "${ANTHROPIC_API_KEY:-}" ]; then
  # Hidden input: the key isn't echoed, stored, or logged.
  read -r -s -p "Anthropic API key (input hidden): " ANTHROPIC_API_KEY; echo
  export ANTHROPIC_API_KEY
fi
[ -n "$ANTHROPIC_API_KEY" ] || { echo "No API key entered." >&2; exit 1; }

# Keep the token secret between runs so the app stays signed in (file is git-ignored).
SECRET_FILE=server/.dev-token-secret
[ -f "$SECRET_FILE" ] || head -c 48 /dev/urandom | base64 | tr -d '\n' > "$SECRET_FILE"
export TOKEN_SECRET="$(cat "$SECRET_FILE")"
export INTEGRITY_MODE="${INTEGRITY_MODE:-off}"

echo "Starting FitHall server on http://localhost:8080. Ctrl+C to stop."
exec ./gradlew :server:run
