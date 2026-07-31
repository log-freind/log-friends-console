#!/usr/bin/env bash

set -euo pipefail

INGEST_URL="${LOGFRIENDS_INGEST_URL:-http://localhost:8080/ingest}"
WORKER_ID="${LOGFRIENDS_WORKER_ID:-order-service-local-1}"
TOTAL_EVENTS="${TOTAL_EVENTS:-10000}"
BATCH_SIZE="${BATCH_SIZE:-100}"
RUN_ID="${RUN_ID:-catalog-load-$(date -u +%Y%m%dT%H%M%SZ)}"

if ! command -v curl >/dev/null; then
  echo "curl is required" >&2
  exit 1
fi

if ! command -v jq >/dev/null; then
  echo "jq is required" >&2
  exit 1
fi

if (( TOTAL_EVENTS <= 0 || BATCH_SIZE <= 0 )); then
  echo "TOTAL_EVENTS and BATCH_SIZE must be positive integers" >&2
  exit 1
fi

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

received_total=0
stored_total=0
failed_total=0
sent_total=0
started_at="$(date +%s)"

echo "Console direct load test"
echo "  ingestUrl: $INGEST_URL"
echo "  workerId: $WORKER_ID"
echo "  eventName: catalogProductsListed"
echo "  totalEvents: $TOTAL_EVENTS"
echo "  batchSize: $BATCH_SIZE"
echo "  runId: $RUN_ID"

for (( offset=0; offset<TOTAL_EVENTS; offset+=BATCH_SIZE )); do
  count=$BATCH_SIZE
  if (( offset + count > TOTAL_EVENTS )); then
    count=$((TOTAL_EVENTS - offset))
  fi

  payload_file="$tmp_dir/batch-$offset.json"
  timestamp="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

  jq -n \
    --arg workerId "$WORKER_ID" \
    --arg timestamp "$timestamp" \
    --arg runId "$RUN_ID" \
    --argjson offset "$offset" \
    --argjson count "$count" \
    '{
      workerId: $workerId,
      events: [range(0; $count) | {
        type: "LOG_EVENT",
        timestamp: $timestamp,
        eventName: "catalogProductsListed",
        payload: {
          runId: $runId,
          source: "console-direct-load-test",
          sequence: ($offset + .)
        }
      }]
    }' > "$payload_file"

  response="$(curl --fail-with-body --silent --show-error \
    --header 'Content-Type: application/json' \
    --data @"$payload_file" \
    "$INGEST_URL")"

  received="$(jq -er '.received' <<< "$response")"
  stored="$(jq -er '.stored' <<< "$response")"
  failed="$(jq -er '.failed' <<< "$response")"

  received_total=$((received_total + received))
  stored_total=$((stored_total + stored))
  failed_total=$((failed_total + failed))
  sent_total=$((sent_total + count))
done

elapsed_seconds=$(( $(date +%s) - started_at ))

echo
echo "Result"
echo "  sent: $sent_total"
echo "  received: $received_total"
echo "  stored: $stored_total"
echo "  failed: $failed_total"
echo "  elapsedSeconds: $elapsed_seconds"

if (( received_total != TOTAL_EVENTS || stored_total != TOTAL_EVENTS || failed_total != 0 )); then
  echo "FAIL: Console did not store every submitted event" >&2
  exit 1
fi

echo "PASS: Console stored every submitted event"
echo
echo "Database verification SQL:"
echo "SELECT count(*) FROM custom_events"
echo "WHERE event_name = 'catalogProductsListed'"
echo "  AND payload ->> 'runId' = '$RUN_ID';"
