# Log Friends Console

SDK가 보낸 이벤트를 저장하고, **코드에 정의된 의미와 실제 payload를 함께 조회하는 백엔드**입니다.
화면은 별도 저장소인 [Console Web](https://github.com/log-freind/log-friends-console-web)에서 제공합니다.

```text
Kotlin / TypeScript SDK → Console → PostgreSQL + TimescaleDB
                           ↓
                      Console Web / MCP
```

## 무엇을 할 수 있나요?

| 기능 | 확인할 내용 |
|---|---|
| Log Catalog | 이벤트 설명, 필드 계약, 코드 힌트, 실제 샘플, 필드 누락과 추가 |
| Raw Events | 발생한 이벤트 조회, 기간·앱·worker·세션 필터, CSV 내보내기 |
| Overview | HTTP 트래픽, 지연, 이벤트 발생 횟수, 오류 |
| Frontend Tree용 API | 브라우저 이벤트에 담긴 페이지와 컴포넌트 위치 |
| 읽기 전용 MCP | AI 도구에서 이벤트 계약·샘플·수집 실패·worker 상태 조회 |

데이터 분석이나 ML 학습 자체를 수행하는 플랫폼은 아닙니다.
데이터를 이해하고 활용하기 전 필요한 확인 작업을 돕습니다.

## 로컬 실행

**JDK 21과 TimescaleDB 확장이 설치된 PostgreSQL**이 필요합니다.
DB와 접속 사용자를 먼저 준비하세요. 일반 PostgreSQL만 있으면 hypertable 마이그레이션을 실행할 수 없습니다.

```bash
export POSTGRES_HOST=localhost
export POSTGRES_PORT=5433
export POSTGRES_DB=logfriends_platform
export POSTGRES_USER=logfriends
export POSTGRES_PASSWORD=logfriends

./gradlew bootRun
```

위 계정은 로컬 예시입니다. 운영 비밀번호로 사용하지 마세요.
시작 시 [Flyway 마이그레이션](src/main/resources/db/migration)이 테이블을 구성하고 스키마를 검증합니다.

다른 터미널에서 확인합니다.

```bash
curl --fail http://localhost:8080/actuator/health
curl --fail http://localhost:8080/api/log-catalog/apps
```

앱 목록이 비어 있다면 SDK를 연결한 서비스나 [Examples](https://github.com/log-freind/log-friends-examples)를 실행하세요.
그다음 Console Web을 실행해 데이터를 확인합니다.

## SDK 연결 시 알아둘 점

- 서비스는 먼저 `POST /api/agents`에 등록합니다. `/ingest`는 Agent를 자동 등록하지 않습니다.
- `POST /ingest`는 **요청당 최대 50건**입니다. Kotlin SDK와 부하 테스트 스크립트의 기본 100건을 그대로 쓰면 요청이 거절됩니다.
- 현재 요청 제한은 Console 인스턴스별 `(clientIp, workerId)` 조합당 분당 120회입니다. 초과하면 429 응답을 받습니다.
- 유효한 이벤트는 타입별 테이블에 JDBC batch로 저장합니다. 이벤트 단위 검증 실패는 별도 기록하지만, 잘못된 요청 JSON은 요청 전체가 거절될 수 있습니다.

응답 예시:

```json
{"received": 50, "stored": 49, "failed": 1}
```

HTTP 200만으로 전부 저장됐다고 판단하지 마세요. 응답 집계와 DB를 확인해야 합니다.
동일한 `eventId + timestamp` 중복이 제외되면 `received`와 `stored + failed`가 다를 수 있습니다.

## 자주 쓰는 설정

| 환경변수 | 기본값 / 용도 |
|---|---|
| `POSTGRES_HOST`, `POSTGRES_PORT` | `localhost`, `5433` |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | `logfriends_platform`, `logfriends`, `logfriends` |
| `LOGFRIENDS_WEB_ALLOWED_ORIGINS` | `http://localhost:3000`, 여러 origin은 쉼표로 구분 |
| `LOGFRIENDS_INGEST_DB_BATCH_SIZE` | 100, 같은 타입의 DB 쓰기 단위. HTTP 수신 제한과 별개 |
| `LOGFRIENDS_MCP_ENABLED` | `false` |

`bootRun`은 로컬 `.env`도 읽으며 이미 지정한 환경변수가 우선합니다.
설정 전체는 [application.yml](src/main/resources/application.yml)을 확인하세요.

## 읽기 전용 MCP

```bash
LOGFRIENDS_MCP_ENABLED=true ./gradlew bootRun
```

MCP 클라이언트의 Streamable HTTP 연결 주소에 `http://localhost:8080/mcp`를 등록합니다.
Console 자체에는 모델 API 키가 필요하지 않습니다.

제공 도구: `search_log_catalog`, `get_event_samples`, `get_ingest_failure_summary`, `get_worker_status`.
샘플 조회는 최대 7일·20건, 결과는 64KiB, 동시 쿼리는 2개로 제한합니다.

원격 연결은 `LOGFRIENDS_MCP_ALLOWED_HOSTS`와 `LOGFRIENDS_MCP_ALLOWED_ORIGINS`를 설정해야 합니다.
**Host/Origin 검사와 CORS는 인증이 아닙니다.** 현재 인증·권한 기능이 없으므로 신뢰할 수 있는 내부망에서 사용하세요.
키 이름 기반 마스킹만으로 모든 민감정보를 제거할 수는 없습니다.

## 현재 범위와 한계

- 발견된 코드 힌트는 LogSpec으로 자동 확정되지 않습니다. 계약은 `/api/log-specs` API에서 관리합니다.
- mismatch는 필드 누락·추가 비교입니다. 타입 변경이나 중첩 객체 전체를 검증하는 기능은 아닙니다.
- Overview는 원본 이벤트를 조회합니다. 별도 스케줄러 집계를 Timescale Continuous Aggregate로 구현한 것은 아닙니다.
- CSV는 조회 결과를 메모리에 구성합니다. 대량 내보내기는 기간을 나누세요.
- 브로커, 영구 수집 큐, exactly-once 보장은 제공하지 않습니다.

## 개발 및 저장 테스트

```bash
./gradlew build
```

`curl`과 `jq`가 있으면 소량의 직접 저장 테스트를 실행할 수 있습니다.

```bash
LOGFRIENDS_INGEST_URL=http://localhost:8080/ingest \
LOGFRIENDS_WORKER_ID=console-test-local-1 \
TOTAL_EVENTS=100 BATCH_SIZE=50 \
./scripts/load-test-catalog-products-listed.sh
```

[테스트 스크립트](scripts/load-test-catalog-products-listed.sh)는 SDK를 거치지 않습니다.
대규모 실행 전 요청 제한과 전송 속도를 조정해야 하며, 과거 부하 테스트 수치는 현재 설정의 성능 보장이 아닙니다.

[Kotlin SDK](https://github.com/log-freind/log-friends-kt-sdk) ·
[Console Web](https://github.com/log-freind/log-friends-console-web) ·
[Examples](https://github.com/log-freind/log-friends-examples) · [Apache-2.0](LICENSE)
