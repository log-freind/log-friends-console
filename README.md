# log-friends-console

Spring Boot control plane and ingest server for Log Friends. It receives SDK event batches over HTTP, stores raw events in TimescaleDB tables, and manages agents, log specs, alert rules, incidents, and event statistics.

## Current Architecture

```text
log-friends-sdk
  -> POST /ingest
  -> log-friends-console
  -> TimescaleDB
```

Kafka and Protobuf transport are excluded from the current ingest path.

## Responsibilities

- **Ingest**: receive SDK JSON batches at `POST /ingest`
- **Raw event storage**: store events in type-specific TimescaleDB hypertables
- **Partial failure handling**: store malformed events separately without failing the whole batch
- **Derived stats**: generate dashboard/stat data with an internal scheduler
- **Metadata APIs**: manage agents, log specs, alert rules, incidents, and event stats

## Raw Event Tables

| Event type | Table |
|------------|-------|
| `LOG` | `log_events` |
| `HTTP` | `http_events` |
| `JDBC` | `jdbc_events` |
| `METHOD_TRACE` | `method_trace_events` |
| `LOG_EVENT` | `custom_events` |

Invalid ingest events are stored in `ingest_failed_events`. Failure statistics are stored separately in `ingest_failure_stats`.

## Ingest Response

`POST /ingest` returns counts only. It does not return failed payload details.

```json
{
  "received": 100,
  "stored": 97,
  "failed": 3
}
```

## Derived Statistics

The first implementation keeps stats generation inside `log-friends-console`:

- scheduler runs every 1 minute
- scheduler recalculates the recent 5 minute window
- `event_stats` uses `(agent_id, event_type, window_start)` as a unique upsert key
- failed ingest events are aggregated into `ingest_failure_stats`
- unregistered workers are not auto-registered; their stats are skipped
- old backfill is out of scope for the first implementation

## REST API

| Area | Endpoint |
|------|----------|
| Ingest | `POST /ingest` |
| Events | `GET /api/events/*` |
| Agents | `/api/agents` |
| Log specs | `/api/log-specs` |
| Event stats | `/api/event-stats` |
| Alert rules | `/api/alert-rules` |
| Incidents | `/api/incidents` |

## Run

### Docker

```bash
cd /Users/choeseonghyeon/Desktop/log-friends
docker compose -f docker-compose.infra.yml up -d
docker compose -f docker-compose.platform.yml up -d --build
```

Console runs on:

```text
http://localhost:8082
```

### Local

```bash
cd /Users/choeseonghyeon/Desktop/log-friends/log-friends-console
./gradlew bootRun
```

## Configuration

| Environment variable | Default |
|----------------------|---------|
| `POSTGRES_HOST` | `localhost` |
| `POSTGRES_PORT` | `5432` |
| `POSTGRES_DB` | `logfriends_platform` |
| `POSTGRES_USER` | `logfriends` |
| `POSTGRES_PASSWORD` | `logfriends` |

## Related Docs

- `../docs/system/overview.md`
- `../docs/system/runtime-flow.md`
- `../docs/console/overview.md`
- `../docs/console/ingest-api.md`
