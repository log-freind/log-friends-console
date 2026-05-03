# AGENTS.md — log-friends-console

## 책임

`log-friends-console`은 SDK가 보낸 HTTP JSON batch를 `POST /ingest`로 수신하고, TimescaleDB에 원본 이벤트를 저장하며, 실패 이벤트 분리와 파생 통계 생성, 조회 API, 정적 UI를 제공하는 Spring Boot 서버다.

## 현재 경계

```text
SDK
  -> POST /ingest
  -> Console Ingest
  -> TimescaleDB Raw Event Tables
  -> Console Scheduler
  -> Dashboard / Log Catalog API
```

## 핵심 기준

- `/ingest`는 원본 저장 경로다.
- 정상 이벤트는 타입별 원본 테이블에 저장한다.
- 잘못된 이벤트는 `ingest_failed_events`에 분리한다.
- `/ingest` 응답은 `received`, `stored`, `failed` count만 반환한다.
- 상세 실패 목록과 실패 payload는 응답하지 않는다.
- Scheduler는 1분마다 최근 5분을 재계산하고 upsert한다.
- 미등록 worker는 자동 Agent 등록하지 않고 통계를 건너뛴다.
- 오래된 원본 이벤트 backfill은 1차 제외다.

## 원본 테이블

| eventType | table |
|-----------|-------|
| `HTTP` | `http_events` |
| `LOG` | `logs` |
| `JDBC` | `jdbc_events` |
| `METHOD_TRACE` | `method_traces` |
| `LOG_EVENT` | `custom_events` |

`custom_events.payload`는 JSONB object다.

## 빌드

```bash
./gradlew build
./gradlew bootRun
```

## 주의사항

- DB 스키마는 Flyway 기준으로 관리한다.
- Hibernate create/update 전제를 추가하지 않는다.
- Log Catalog 상세는 `docs/log-catalog/*` 문서로 분리한다.
- Console 기준 문서는 `docs/console/*`를 따른다.
