#!/usr/bin/env bash

set -euo pipefail

INGEST_URL="${LOGFRIENDS_INGEST_URL:-http://localhost:8080/ingest}"
WORKER_ID="${LOGFRIENDS_WORKER_ID:-test-dedup-worker-1}"

echo "=== Log Friends Real Ingest & Dedup Verification ==="
echo "Endpoint: $INGEST_URL"
echo "WorkerId: $WORKER_ID"

if ! command -v curl >/dev/null; then
  echo "Error: curl is required" >&2
  exit 1
fi

if ! command -v jq >/dev/null; then
  echo "Error: jq is required" >&2
  exit 1
fi

# 1. Health check / reachability test
echo ""
echo "[Test 1] Testing Ingest Endpoint Connectivity..."
test_event_id="test-evt-$(date +%s)-1"
test_session_id="test-sess-$(date +%s)"
timestamp="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

payload1=$(jq -n \
  --arg workerId "$WORKER_ID" \
  --arg timestamp "$timestamp" \
  --arg eventId "$test_event_id" \
  --arg sessionId "$test_session_id" \
  '{
    workerId: $workerId,
    events: [{
      type: "LOG_EVENT",
      timestamp: $timestamp,
      eventName: "orderCreated",
      eventId: $eventId,
      sessionId: $sessionId,
      payload: { amount: 15000 }
    }]
  }')

response1=$(curl -s -w "\n%{http_code}" -X POST "$INGEST_URL" \
  -H "Content-Type: application/json" \
  -d "$payload1")

http_code1=$(echo "$response1" | tail -n1)
body1=$(echo "$response1" | head -n -1)

if [[ "$http_code1" != "200" ]]; then
  echo "Failed to connect to $INGEST_URL (HTTP $http_code1). Is Console backend running?"
  echo "Response: $body1"
  exit 1
fi

stored1=$(echo "$body1" | jq -r '.stored // 0')
echo "Result 1: HTTP $http_code1, body=$body1"
if [[ "$stored1" -ne 1 ]]; then
  echo "Error: Expected stored=1, got stored=$stored1"
  exit 1
fi
echo "✓ Test 1 Passed: Event stored successfully."

# 2. Retransmission duplicate test (same eventId and timestamp in separate request)
echo ""
echo "[Test 2] Testing Retransmission Deduplication..."
response2=$(curl -s -w "\n%{http_code}" -X POST "$INGEST_URL" \
  -H "Content-Type: application/json" \
  -d "$payload1")

http_code2=$(echo "$response2" | tail -n1)
body2=$(echo "$response2" | head -n -1)
stored2=$(echo "$body2" | jq -r '.stored // 0')
failed2=$(echo "$body2" | jq -r '.failed // 0')

echo "Result 2: HTTP $http_code2, body=$body2"
if [[ "$stored2" -ne 0 || "$failed2" -ne 0 ]]; then
  echo "Error: Expected stored=0 failed=0 for retransmission duplicate, got stored=$stored2 failed=$failed2"
  exit 1
fi
echo "✓ Test 2 Passed: Retransmitted duplicate safely ignored (stored=0, failed=0)."

# 3. Same-batch duplicate test
echo ""
echo "[Test 3] Testing Intra-Batch Deduplication..."
same_batch_event_id="intra-batch-$(date +%s)"
payload3=$(jq -n \
  --arg workerId "$WORKER_ID" \
  --arg timestamp "$timestamp" \
  --arg eventId "$same_batch_event_id" \
  '{
    workerId: $workerId,
    events: [
      {
        type: "LOG_EVENT",
        timestamp: $timestamp,
        eventName: "itemFavorited",
        eventId: $eventId,
        payload: { itemId: "item-1" }
      },
      {
        type: "LOG_EVENT",
        timestamp: $timestamp,
        eventName: "itemFavorited",
        eventId: $eventId,
        payload: { itemId: "item-1" }
      }
    ]
  }')

response3=$(curl -s -w "\n%{http_code}" -X POST "$INGEST_URL" \
  -H "Content-Type: application/json" \
  -d "$payload3")

http_code3=$(echo "$response3" | tail -n1)
body3=$(echo "$response3" | head -n -1)
stored3=$(echo "$body3" | jq -r '.stored // 0')

echo "Result 3: HTTP $http_code3, body=$body3"
if [[ "$stored3" -ne 1 ]]; then
  echo "Error: Expected stored=1 for 2 same-batch duplicates, got stored=$stored3"
  exit 1
fi
echo "✓ Test 3 Passed: Intra-batch duplicate safely deduped to 1 store."

# 4. Batch size limit test (> 50 events)
echo ""
echo "[Test 4] Testing Max Batch Limit (>50 events)..."
payload4=$(jq -n \
  --arg workerId "$WORKER_ID" \
  --arg timestamp "$timestamp" \
  '{
    workerId: $workerId,
    events: [range(0; 55) | {
      type: "LOG_EVENT",
      timestamp: $timestamp,
      eventName: "bulkItem"
    }]
  }')

response4=$(curl -s -w "\n%{http_code}" -X POST "$INGEST_URL" \
  -H "Content-Type: application/json" \
  -d "$payload4")

http_code4=$(echo "$response4" | tail -n1)
body4=$(echo "$response4" | head -n -1)
echo "Result 4: HTTP $http_code4, body=$body4"
if [[ "$http_code4" != "400" ]]; then
  echo "Error: Expected HTTP 400 for batch > 50, got HTTP $http_code4"
  exit 1
fi
echo "✓ Test 4 Passed: Batch size limit enforced."

echo ""
echo "=== All Real Ingestion & Deduplication Tests Passed! ==="
