#!/usr/bin/env bash
# Run a k6 profile in docker and save the summary to loadtest/results/.
# Usage: ./loadtest/run.sh <profile> [script=redirect] [label]
#   ./loadtest/run.sh smoke
#   ./loadtest/run.sh stress redirect cold-cache-no-index
set -euo pipefail

PROFILE="${1:?profile required: smoke|load|peak|stress|spike|soak}"
SCRIPT="${2:-redirect}"
LABEL="${3:-}"
HERE="$(cd "$(dirname "$0")" && pwd)"
K6_IMAGE="${K6_IMAGE:-grafana/k6:1.0.0}"

NAME="$(date +%Y%m%d-%H%M%S)-${SCRIPT}-${PROFILE}${LABEL:+-$LABEL}"

docker run --rm -i \
  --network host \
  --user "$(id -u):$(id -g)" \
  -v "$HERE":/loadtest \
  -w /loadtest \
  -e PROFILE="$PROFILE" \
  -e BASE_URL="${BASE_URL:-http://localhost:8080}" \
  "$K6_IMAGE" run \
  --summary-export "results/$NAME.json" \
  "k6/$SCRIPT.js"

echo "Summary saved to $HERE/results/$NAME.json"
