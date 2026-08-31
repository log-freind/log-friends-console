#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

export LOGFRIENDS_INGEST_URL="${LOGFRIENDS_INGEST_URL:-http://192.168.0.38/ingest}"
export LOGFRIENDS_WORKER_ID="${LOGFRIENDS_WORKER_ID:-order-service-local-1}"
export TOTAL_EVENTS="${TOTAL_EVENTS:-100000}"
export BATCH_SIZE="${BATCH_SIZE:-100}"

exec "$script_dir/load-test-catalog-products-listed.sh"
