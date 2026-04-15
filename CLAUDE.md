# CLAUDE.md — log-friends-console

## 개요
Log Friends 관측성 스택의 중앙 관리 콘솔(Control Plane). **Agent 등록/모니터링 / AlertRule 관리 / Incident 추적 / AuditLog** 기록을 REST API로 제공하는 Spring Boot 백엔드.

## 핵심 스택
- Kotlin 2.3.20 / JVM 21
- Spring Boot 3.4.0 (web, data-jpa, validation, actuator)
- PostgreSQL + Flyway (V1~V5) + jOOQ 3.19.16
- 서버 포트: 8082

## 빌드 & 실행
```bash
./gradlew build              # 빌드 + 테스트
./gradlew bootRun            # 로컬 실행 (포트 8082, PostgreSQL 필요)
```

## 주요 파일
```
src/main/kotlin/com/logfriends/platform/
├── api/rest/
│   ├── AgentController.kt           # /api/agents/*
│   ├── AlertRuleController.kt       # /api/alert-rules/*
│   └── IncidentController.kt        # /api/incidents/*
├── domain/agent/                    # heartbeat, stale 체크 (60초마다)
├── domain/alert/                    # AlertRule CRUD + condition 검증
├── domain/incident/                 # 상태 기계 + OptimisticLock
│   └── repository/IncidentSpec.kt   # JPA Specification 동적 쿼리
└── domain/audit/                    # AuditLog (불변, write-only)
src/main/resources/db/migration/     # V1~V5 Flyway 마이그레이션
```

## 환경변수
| 변수 | 기본값 | 설명 |
|---|---|---|
| `POSTGRES_HOST` | `localhost` | PostgreSQL 호스트 |
| `POSTGRES_PORT` | `5432` | PostgreSQL 포트 |
| `POSTGRES_DB` | `logfriends_platform` | DB 이름 |
| `POSTGRES_USER` | `logfriends` | 사용자 |
| `POSTGRES_PASSWORD` | `logfriends` | 비밀번호 |

## 주의사항
- DB 스키마는 **Flyway 전담**: `ddl-auto: validate` (Hibernate create/update 금지)
- `OSIV false`: 트랜잭션 내에서 연관 엔티티 접근 필요
- `@Version` Optimistic Locking: 동시 수정 시 `OPTIMISTIC_LOCK_CONFLICT` 에러
- Agent stale 체크: 5분 heartbeat 없으면 STOPPED
- AlertRule condition type: `LOG_COUNT | LATENCY | ERROR_RATE | CUSTOM`
- 커밋: 한글, `feat:`/`fix:`/`refactor:`/`docs:` prefix
