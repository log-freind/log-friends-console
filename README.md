# log-friends-console

Spring Boot Console server for Log Friends. It receives SDK HTTP JSON batches, registers and manages agents, stores raw events in PostgreSQL/TimescaleDB, and exposes the Log Catalog, Raw Events page, query APIs, and derived event statistics.

## Current Architecture

```text
log-friends-sdk
  -> POST /ingest
  -> log-friends-console
  -> TimescaleDB/PostgreSQL
  -> Dashboard / Log Catalog
```

The current path is intentionally small: SDKs send JSON batches directly to the Console, and the Console persists, validates, aggregates, and serves the data. There is no broker or separate distributed pipeline in this phase.

## Responsibilities

- **Ingest**: receive SDK JSON batches at `POST /ingest`
- **Raw event storage**: store valid events in type-specific raw event tables
- **Partial failure handling**: store malformed events separately without failing the whole batch
- **Agent management**: register startup agents through `POST /api/agents`
- **Log Catalog**: combine LogSpec metadata, recent `LOG_EVENT` samples, mismatch checks, field requests, and discovered LogEvent hints
- **Raw Events UI**: serve `/raw-events` for raw event lookup and custom event CSV export
- **Derived stats**: generate dashboard/stat data with an internal scheduler
- **Metadata APIs**: manage agents, log specs, discovered candidates, field requests, raw event queries, and event stats

## Ingest

`POST /ingest` accepts a batch with a fixed `workerId` and event payloads captured by the SDK. Valid events are stored by `eventType`; invalid events are stored in `ingest_failed_events`.

`/ingest` returns counts only. It does not return failed payload details.

```json
{
  "received": 100,
  "stored": 97,
  "failed": 3
}
```

Unregistered workers are not auto-registered by ingest. SDK startup registration must happen through `POST /api/agents`.

## Raw Event Tables

| Event type | Table |
|------------|-------|
| `LOG` | `logs` |
| `HTTP` | `http_events` |
| `JDBC` | `jdbc_events` |
| `METHOD_TRACE` | `method_traces` |
| `LOG_EVENT` | `custom_events` (`payload` JSONB) |

Invalid ingest events are stored in `ingest_failed_events`. Failure statistics are aggregated into `ingest_failure_stats`.

`LOG_EVENT.eventName` follows the SDK contract: it is required and must be camelCase matching `^[a-z][a-zA-Z0-9]*$`.

## Agent Registration

The SDK registers its running app instance at startup with `POST /api/agents`. The request requires `workerId` and `appName`; optional fields include `sdkVersion`, `javaVersion`, `hostname`, and `metadata`.

Registration creates the Console Agent record and returns the `agentId` plus known LogSpecs for the app. Duplicate `workerId` registration is rejected; use `PATCH /api/agents/{id}` for metadata changes and `POST /api/agents/heartbeat` for liveness updates.

## Log Catalog

`/log-catalog` is the default UI. The Log Catalog API exposes:

| Endpoint | Purpose |
|----------|---------|
| `GET /api/log-catalog/apps` | list apps and worker IDs |
| `GET /api/log-catalog/apps/{appName}/events` | list catalog events, summaries, samples, mismatches, field requests, and discovered hints |

LogSpecs are registered and edited through Console APIs under `/api/log-specs`; they are not auto-created by ingest.

## Discovered LogEvent Candidates

SDKs can report candidate `LOG_EVENT` definitions after agent registration:

| Endpoint | Purpose |
|----------|---------|
| `POST /api/agents/{agentId}/discovered-log-events` | upsert discovered candidates from source class/method metadata |
| `GET /api/agents/{agentId}/discovered-log-events` | list discovered candidates for an agent |

Candidates are stored in `discovered_log_events` with status such as `DISCOVERED` or `IGNORED`, and appear as hints in the Log Catalog.

## Raw Events

`/raw-events` serves the raw event lookup page. The backing APIs are:

| Endpoint | Event data |
|----------|------------|
| `GET /api/events/http` | `HTTP` raw events |
| `GET /api/events/log` | `LOG` raw events |
| `GET /api/events/jdbc` | `JDBC` raw events |
| `GET /api/events/method-trace` | `METHOD_TRACE` raw events |
| `GET /api/events/custom` | `LOG_EVENT` raw events |
| `GET /api/events/custom.csv` | CSV export for filtered `LOG_EVENT` rows |

`/api/events/custom.csv` returns `text/csv` with an attachment filename of `log-friends-custom-events.csv`.

## Derived Statistics

The first implementation keeps stats generation inside `log-friends-console`:

- scheduler runs every 1 minute
- scheduler recalculates the recent 5 minute window
- `event_stats` uses `(agent_id, event_type, window_start)` as a unique upsert key
- failed ingest events are aggregated into `ingest_failure_stats`
- unregistered workers are not auto-registered; their stats are skipped
- old backfill is out of scope for the first implementation

## Database and Migrations

The Console uses PostgreSQL with the TimescaleDB extension. Flyway manages schema creation from `src/main/resources/db/migration`; Hibernate runs in `validate` mode.

Flyway creates the agent, LogSpec, raw event, failure, stats, field request, and discovered LogEvent tables. Timescale hypertables are created for the raw event tables and `ingest_failed_events`.

## REST API

| Area | Endpoint |
|------|----------|
| Ingest | `POST /ingest` |
| Agents | `POST /api/agents`, `GET /api/agents`, `POST /api/agents/heartbeat` |
| Log Catalog | `GET /api/log-catalog/*` |
| Log specs | `/api/log-specs` |
| Discovered LogEvents | `/api/agents/{agentId}/discovered-log-events` |
| Field requests | `/api/field-requests` |
| Raw events | `GET /api/events/*`, `GET /api/events/custom.csv` |
| Event stats | `/api/event-stats` |

## Local Run and Build

Start PostgreSQL/TimescaleDB first, then run from this directory:

```bash
./gradlew bootRun
```

Console runs on:

```text
http://localhost:8080
```

Build and test:

```bash
./gradlew build
```

## Configuration

`bootRun` loads a local `.env` file if present, and existing shell environment variables take precedence. Do not commit local secrets.

| Environment variable | Default |
|----------------------|---------|
| `POSTGRES_HOST` | `localhost` |
| `POSTGRES_PORT` | `5433` |
| `POSTGRES_DB` | `logfriends_platform` |
| `POSTGRES_USER` | `logfriends` |
| `POSTGRES_PASSWORD` | `logfriends` |

Use `.env` for local database overrides only. The application reads these values into `spring.datasource.url`, username, and password.

## Related Docs

- `../docs/system/overview.md`
- `../docs/system/runtime-flow.md`
- `../docs/console/overview.md`
- `../docs/console/ingest-api.md`
