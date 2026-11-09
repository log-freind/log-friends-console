# Log Friends Console

> **개발 구현 기준 문서:** 최신 로컬 코드의 사용법을 설명합니다. 이 README 전용 PR에는 제품 코드·배포 변경이 포함되지 않습니다. GitHub 기본 브랜치만 clone한 경우 아래 기능·명령이 아직 일치하지 않을 수 있으며, 관련 코드 반영 후 사용해야 합니다.

SDK 이벤트 수집·저장, 로그인·대화, Spring AI 도구와 승인된 학습 실행을 담당하는 **Kotlin 백엔드**입니다.
화면은 별도 [Console Web](https://github.com/log-freind/log-friends-console-web)에서 제공합니다.

문서 기준: 2026-11-10 작업 트리의 코드. 공개 릴리스나 현재 운영 배포 상태를 보증하지 않습니다.

```text
SDK → HTTP JSON batch → Console → PostgreSQL + TimescaleDB
                           ↑
                      Console Web / MCP
```

## 주요 기능

| 기능 | 실제 범위 |
|---|---|
| 수집·조회 | HTTP·LOG·JDBC·METHOD_TRACE·LOG_EVENT 저장, 수집 실패 분리, Raw Events·CSV |
| Log Catalog | 이벤트 설명·필드 계약·코드 힌트·샘플, 필드 누락·추가 비교 |
| API 수집 설정 | 소유 앱의 메서드·경로별 ON/OFF, 정책 버전·SDK 전달·ACK |
| Overview | HTTP 트래픽·지연·오류·이벤트 통계, 브라우저 위치 정보 조회 API |
| 로그인·대화 | 쿠키 세션, 사용자별 대화 이력, Model Planning과 이벤트 조사 |
| Spring AI | 수집 근거 조회, 데이터 검증, 모델 비교안·수집 변경안 제안 |
| 학습·개선 | 합성 주문 검증, 해시 승인, 제한된 CPU Job과 반복 비교 |
| 읽기 전용 MCP | 이벤트 계약·샘플·수집 실패·Worker 상태 조회 |

화면과 요청 전달은 Web, 대화·모델 호출·도구 실행과 승인 검증은 이 서버가 담당합니다.

## 로컬 실행

**JDK 21과 TimescaleDB 확장이 설치된 PostgreSQL**을 먼저 준비하세요. 일반 PostgreSQL만으로는 hypertable 마이그레이션을 실행할 수 없습니다.

```bash
export POSTGRES_HOST=localhost
export POSTGRES_PORT=5433
export POSTGRES_DB=logfriends_platform
export POSTGRES_USER=logfriends
export POSTGRES_PASSWORD=logfriends

SERVER_PORT=8082 LOGFRIENDS_TRAINING_ENABLED=false ./gradlew bootRun \
  --args='--logfriends.web.allowed-origins=http://localhost:3000 --logfriends.account.allowed-origins=http://localhost:3000 --logfriends.account.secure-cookie=false --logfriends.account.signup-enabled=true'
```

위 DB 계정은 로컬 예시이며 운영용이 아닙니다. 시작 시 [Flyway](src/main/resources/db/migration)가 스키마를 구성하고 Hibernate는 검증만 합니다.

- 가입 활성화와 Secure 쿠키 해제는 로컬 HTTP용입니다. 운영에서는 HTTPS·Secure 쿠키를 사용하고 필요한 계정을 만든 뒤 가입을 닫으세요.
- Web CORS와 계정 Origin 검사는 별도입니다. Web 포트가 다르면 위의 두 Origin 설정을 함께 바꾸세요.
- 서버 기본 포트는 8080이고 위 예시는 로컬 SDK 연결용 8082입니다. 기본 경로는 `/logfriend`이며 아래의 `/api/...`, `/ingest` 앞에도 붙입니다.

다른 터미널에서 상태를 확인합니다.

```bash
curl --fail http://localhost:8082/logfriend/actuator/health
```

Console Web을 별도로 실행해 가입·로그인하고 앱을 등록하세요. 서버 수집 키를 발급받아 현행 인증을 지원하는 SDK를 연결합니다.

### 설정

| 환경변수 | 기본값 / 용도 |
|---|---|
| `POSTGRES_HOST`, `POSTGRES_PORT` | `localhost`, `5433` |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | `logfriends_platform`, `logfriends`, `logfriends` |
| `SERVER_PORT`, `LOGFRIENDS_CONTEXT_PATH` | `8080`, `/logfriend` |
| `LOGFRIENDS_WEB_ALLOWED_ORIGINS` | `http://localhost:3000`, 쉼표로 구분 |
| `LOGFRIENDS_INGEST_DB_BATCH_SIZE` | `100`, DB 쓰기 단위이며 HTTP 수신 제한과 별개 |
| `LOGFRIENDS_AGENT_ENABLED` | `true` |
| `LOGFRIENDS_AGENT_MODEL`, `OPENAI_API_KEY` | 기본 모델 `gpt-4.1-mini`, 서버 전용 키 |
| `LOGFRIENDS_VALIDATION_AGENT_ENABLED` | `false`, 별도 Spring AI 검증 에이전트 |
| `LOGFRIENDS_TRAINING_ENABLED` | `false`, Kubernetes 실행은 별도 준비 필요 |
| `LOGFRIENDS_TRAINING_MASTER_URL` | 학습용 HTTPS Kubernetes API 주소. 실행 컨텍스트와 일치해야 함 |
| `LOGFRIENDS_MCP_ENABLED` | `false` |

`bootRun`은 로컬 `.env`도 읽으며 값이 비어 있지 않은 기존 환경변수가 우선합니다. 전체 설정은 [application.yml](src/main/resources/application.yml)을 확인하세요. 실제 비밀키는 README나 브라우저에 넣지 마세요.

## SDK 수집 경계

- 서버 SDK는 `LOGFRIENDS_APP_KEY`를 설정합니다. Console은 `X-LogFriends-App-Key`와 앱/Worker 연결을 검증하며 키 없는 구버전 요청은 거절합니다. 브라우저에 서버 비밀키를 넣지 마세요.
- 먼저 `POST /api/agents`로 등록해야 합니다. `/ingest`는 Agent를 자동 등록하지 않습니다.
- `POST /ingest`는 요청당 최대 **50건**입니다. Console 인스턴스별 `(clientIp, workerId)` 조합당 기본 분당 120회이며 초과하면 429입니다.
- 정상 이벤트는 타입별 테이블에 JDBC batch로 저장하고 이벤트 단위 실패는 별도로 기록합니다. 잘못된 JSON은 요청 전체가 거절될 수 있습니다.

```json
{"received": 50, "stored": 49, "failed": 1}
```

HTTP 200은 전체 저장 보장이 아닙니다. 중복이 제외되면 `received`와 `stored + failed`도 다를 수 있습니다.

## 로그인과 대화

- 이메일·비밀번호 가입·로그인·로그아웃과 대화 생성·목록·조회를 제공합니다. 비밀번호는 BCrypt, 세션은 7일 HttpOnly·SameSite=Strict 쿠키이며 토큰 원문은 DB에 저장하지 않습니다.
- 가입은 기본 비활성, Secure 쿠키는 기본 활성입니다. `logfriends.account.allowed-origins`도 명시하세요. 계정·대화 API 기본값은 `http://localhost:3002,http://127.0.0.1:3002`, 개선 실험 API는 `http://localhost:3000`으로 서로 다릅니다.
- 대화 소유권·중복 요청·대화별 동시 전송을 검사합니다. 모델 호출 중 DB 트랜잭션을 유지하지 않으며, 응답 저장은 내부 서비스만 수행합니다.
- `/api/account/model-planning`과 `/api/account/investigate`는 로그인 쿠키·Origin·앱 소유권을 확인합니다. 모델 키가 없거나 에이전트가 비활성이면 새 모델 요청은 503으로 실패하지만 서버 시작과 저장된 대화 조회는 가능합니다.

## AI 제안과 승인된 학습

1. **데이터 검증:** `demoOrderObserved`와 `demoOrderResolved`의 합성 주문만 지원합니다. 주문별 일대일 연결, 필수 필드·타입·시각·정답, 시간순 분할과 결과 시각 경계를 검사해 보고서·스냅샷을 저장합니다. 결과 누락을 비취소로 채우지 않습니다.
2. **모델 제안과 승인:** AI는 학습 구간 통계를 보고 모델·코드를 제안합니다. 서버는 모델 카탈로그(`src/main/resources/training/model_catalog.json`, 개발 코드 경로)의 이진 분류 CPU 후보와 코드 정책을 검사합니다. 사용자는 데이터·프로그램 해시를 확인해 승인합니다. 승인·Job 실행은 AI 도구로 노출하지 않습니다.
3. **실행과 반복 개선:** 승인된 작업을 Kubernetes CPU Job으로 실행하고 상태·결과를 저장합니다. 개선 실험은 별도 정책 해시 승인 후 최대 2~3회 비교와 한 번의 최종 평가로 제한합니다. 개선을 보장하지 않으며, 취소해도 이미 실행 중인 Job은 즉시 삭제되지 않습니다.

학습 활성화 변수만 바꿔서는 실행할 수 없습니다. 코드에 지정된 namespace·PVC·사전 적재 이미지, 제한된 Kubernetes 권한, 생성 코드용 NetworkPolicy가 필요합니다. 개발 코드의 `src/main/kotlin/com/logfriends/platform/training/execution/KubernetesTrainingJobs.kt`와 `training/experiment/service/ImprovementPolicy.kt`을 확인하세요. 보안 설정 검사는 실제 클러스터 격리의 입증이 아닙니다.

## 읽기 전용 MCP

로컬 실행 명령에 `LOGFRIENDS_MCP_ENABLED=true`를 추가하고 Streamable HTTP 주소 `http://localhost:8082/logfriend/mcp`로 연결합니다. 외부 MCP 조회에는 모델 키가 필요하지 않습니다.

도구는 `search_log_catalog`, `get_event_samples`, `get_ingest_failure_summary`, `get_worker_status`이며 샘플은 최대 7일·20건입니다.

원격 연결에는 `LOGFRIENDS_MCP_ALLOWED_HOSTS`와 `LOGFRIENDS_MCP_ALLOWED_ORIGINS`를 설정하세요. **Host/Origin 검사와 CORS는 인증이 아닙니다.** 외부 MCP는 로그인·앱 소유권 경로와 동일한 인증을 제공하지 않으므로 신뢰할 수 있는 내부망에서만 사용하세요. 키 이름 기반 마스킹이 모든 민감정보를 제거하지는 않습니다.

## 현재 한계

- 범용 AutoML, 임의 데이터·Python 실행, 학습 모델의 운영 배포는 제공하지 않습니다. 합성 주문 결과는 고객 데이터 성능이나 운영 준비 완료의 증거가 아닙니다.
- 소유권 검사가 있는 경로와 별개로 모든 관리·통계·MCP API에 일괄 테넌트 격리가 적용된 것은 아닙니다. 소셜 로그인·이메일 확인·비밀번호 재설정·대화 삭제는 없습니다.
- 코드 힌트는 LogSpec으로 자동 확정되지 않습니다. mismatch는 필드 누락·추가 비교이며 타입·중첩 객체 전체 검증은 아닙니다.
- Overview 원본 조회와 별도 스케줄러 집계가 있으며 Timescale Continuous Aggregate 구현은 아닙니다. CSV는 메모리에 구성하므로 대량 조회는 기간을 나누세요.
- 브로커·영구 수집 큐·exactly-once 보장을 제공하지 않습니다.

## 개발과 검증

현재 [빌드 설정](build.gradle.kts)은 JDK 21, Kotlin 2.3.20, Spring Boot 4.0.8, Spring AI 2.0.1입니다.

```bash
./gradlew build
```

`LOGFRIENDS_TEST_DB_URL`이 없으면 DB 통합 테스트는 제외됩니다. Docker를 켜고 임시 TimescaleDB를 포함하려면 다음을 실행하세요.

```bash
bash scripts/test-with-db.sh
```

테스트 DB는 로컬 임시 포트로만 열고 종료 시 삭제합니다. 이 스크립트는 외부 Kubernetes Job 테스트를 제외합니다. 단위·DB 테스트 통과는 실제 클러스터 실행이나 처리량 보장이 아닙니다.

[기능별 소스](src/main/kotlin/com/logfriends/platform) · [테스트](src/test/kotlin/com/logfriends/platform) · [Apache-2.0](LICENSE)

상위 작업 공간의 `../docs/console/*`는 로컬 전용 문서이며 이 GitHub 저장소에는 포함되지 않습니다.
