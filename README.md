# log-friends-console

Spring Boot REST API platform for Log Friends observability system. Provides Agent management, AlertRule configuration, and Incident tracking with a PostgreSQL backend.

## Overview

`log-friends-console` is a centralized management console for the Log Friends observability stack. It serves as the control plane for:

- **Agent Management**: Register, track heartbeats, and monitor application agents
- **Alert Rules**: Define dynamic alerting rules with JSONB conditions (LOG_COUNT, LATENCY, ERROR_RATE, CUSTOM)
- **Incident Tracking**: Auto-generate incidents from triggered rules with state machine (TRIGGERED → ACKNOWLEDGED → RESOLVED)
- **Audit Logging**: Track all user actions for compliance and debugging

## Key Features

- **Agent CRUD + Heartbeat Tracking**: Agents register via Kafka handshake; console tracks runtime status and metadata
- **AlertRule Definition**: Store flexible rules as JSONB; support dynamic condition schemas
- **Incident Auto-Generation**: Rules trigger incidents automatically with cooldown protection
- **State Machine**: Enforce valid incident state transitions (TRIGGERED → ACKNOWLEDGED → RESOLVED) with optimistic locking
- **Dynamic Search**: Query incidents by status, severity, rule, and time range using JPA Specifications
- **Audit Trail**: Immutable audit logs for all entity changes

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    REST API Layer (Port 8082)                   │
├─────────────────────────────────────────────────────────────────┤
│  POST   /api/agents/register          (heartbeat auto-register)  │
│  GET    /api/agents                   (list with filters)        │
│  PATCH  /api/agents/{id}              (update metadata)          │
│  POST   /api/agents/heartbeat         (keep-alive + metadata)    │
│  DELETE /api/agents/{id}              (deactivate)               │
│                                                                   │
│  POST   /api/alert-rules              (create rule)              │
│  GET    /api/alert-rules              (list all rules)           │
│  PATCH  /api/alert-rules/{id}         (update condition)         │
│  PATCH  /api/alert-rules/{id}/toggle  (enable/disable)           │
│                                                                   │
│  GET    /api/incidents                (search + paginate)        │
│  POST   /api/incidents                (manual trigger)           │
│  PATCH  /api/incidents/{id}/ack       (acknowledge)              │
│  PATCH  /api/incidents/{id}/resolve   (resolve + summary)        │
│  GET    /api/incidents/count          (status counts)            │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                      Service Layer (Business Logic)             │
├─────────────────────────────────────────────────────────────────┤
│  AgentService         → findAll, register, heartbeat, checkStale │
│  AlertRuleService     → CRUD, toggleEnabled, validateCondition  │
│  IncidentService      → search, trigger, acknowledge, resolve   │
│  AuditService         → log all entity changes                   │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                    Data Layer (Repositories)                    │
├─────────────────────────────────────────────────────────────────┤
│  AgentRepository        : JpaRepository<Agent, Long>             │
│  AlertRuleRepository    : JpaRepository<AlertRule, Long>         │
│  IncidentRepository     : JpaRepository + JpaSpecificationExecutor│
│  AuditLogRepository     : JpaRepository<AuditLog, Long>          │
│                                                                   │
│              ↓                                                    │
│         PostgreSQL 15+ (Flyway migrations)                       │
│         ├─ agents (JSONB metadata)                              │
│         ├─ alert_rules (JSONB conditions)                       │
│         ├─ incidents (JSONB timeline, Optimistic Lock)          │
│         ├─ audit_logs (immutable JSONB changes)                 │
│         └─ users, teams (future)                                │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                   External Data Sources                         │
├─────────────────────────────────────────────────────────────────┤
│  Kafka (log-friends.batch)  → Agent registration + metrics      │
│  ClickHouse                 → Timeseries queries (/workers/:id)  │
│  TimescaleDB                → Raw event search                   │
└─────────────────────────────────────────────────────────────────┘
```

## Quick Start

### Prerequisites

- Java 21+
- Kotlin 2.3.20
- PostgreSQL 15+
- Docker & Docker Compose (recommended)

### Build & Run

**Using Docker Compose (Recommended)**

```bash
cd /Users/choeseonghyeon/Desktop/log-friends/log-friends-console
docker compose up --build
```

This will:
- Start PostgreSQL 15 (port 5432)
- Run Flyway migrations (V1-V5)
- Start console on http://localhost:8082

**Local Build & Run**

```bash
# Build
./gradlew build

# Run JAR
java -jar build/libs/log-friends-console-0.1.0.jar

# Or use gradle bootRun
./gradlew bootRun
```

### Database Setup (Local)

```sql
-- Manual PostgreSQL setup (if not using Docker)
createdb logfriends_platform

-- User (optional, console uses default logfriends/logfriends)
createuser logfriends -P
-- Enter password: logfriends

GRANT ALL PRIVILEGES ON DATABASE logfriends_platform TO logfriends;
```

Flyway will auto-run migrations on startup (V1 users/teams → V5 audit_logs).

## Configuration

**Spring Boot Properties** (`src/main/resources/application.yml`)

```yaml
server:
  port: 8082                          # REST API port

spring:
  application:
    name: log-friends-platform

  datasource:
    url: jdbc:postgresql://localhost:5432/logfriends_platform
    username: logfriends
    password: logfriends
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2

  jpa:
    hibernate:
      ddl-auto: validate               # Flyway manages schema
    open-in-view: false               # OSIV disabled (N+1 safe)
    properties:
      hibernate:
        default_batch_fetch_size: 100
        jdbc.batch_size: 50

  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
```

**Environment Variables** (all optional, shown with defaults)

```bash
export POSTGRES_HOST=localhost
export POSTGRES_PORT=5432
export POSTGRES_DB=logfriends_platform
export POSTGRES_USER=logfriends
export POSTGRES_PASSWORD=logfriends
```

## API Documentation

### Agent Endpoints

#### Register Agent

```http
POST /api/agents/register
Content-Type: application/json

{
  "workerId": "worker-order-001",
  "appName": "order-service",
  "sdkVersion": "1.0.0",
  "javaVersion": "21.0.1",
  "hostname": "pod-order-abc",
  "metadata": {
    "spring.profiles": "prod",
    "k8s.namespace": "production",
    "region": "us-east-1"
  }
}

HTTP/1.1 201 Created
{
  "id": 1,
  "workerId": "worker-order-001",
  "appName": "order-service",
  "status": "RUNNING",
  "sdkVersion": "1.0.0",
  "javaVersion": "21.0.1",
  "hostname": "pod-order-abc",
  "metadata": {...},
  "lastHeartbeat": "2024-04-15T10:30:00Z",
  "registeredAt": "2024-04-15T10:00:00Z",
  "updatedAt": "2024-04-15T10:30:00Z"
}
```

#### List Agents

```http
GET /api/agents

HTTP/1.1 200 OK
[
  {
    "id": 1,
    "workerId": "worker-order-001",
    "appName": "order-service",
    "status": "RUNNING",
    ...
  },
  {
    "id": 2,
    "workerId": "worker-payment-001",
    "appName": "payment-service",
    "status": "STOPPED",
    ...
  }
]
```

#### Agent Heartbeat

```http
POST /api/agents/heartbeat
Content-Type: application/json

{
  "workerId": "worker-order-001",
  "metadata": {
    "memory.usage.percent": 65.5,
    "gc.count": 42,
    "request.count.1m": 1200
  }
}

HTTP/1.1 200 OK
{
  "id": 1,
  "status": "RUNNING",
  "lastHeartbeat": "2024-04-15T10:35:00Z",
  ...
}
```

### AlertRule Endpoints

#### Create Alert Rule

```http
POST /api/alert-rules
Content-Type: application/json

{
  "name": "High Error Rate on Order Service",
  "description": "Alert when error logs exceed 10 in 5 minutes",
  "severity": "CRITICAL",
  "condition": {
    "type": "LOG_COUNT",
    "level": "ERROR",
    "threshold": 10,
    "window_minutes": 5,
    "service_pattern": "order-*"
  },
  "notifyChannels": ["slack", "email"],
  "cooldownMinutes": 30
}

HTTP/1.1 201 Created
{
  "id": 1,
  "name": "High Error Rate on Order Service",
  "severity": "CRITICAL",
  "condition": {...},
  "notifyChannels": ["slack", "email"],
  "cooldownMinutes": 30,
  "enabled": true,
  "lastTriggered": null,
  "createdAt": "2024-04-15T10:00:00Z",
  "updatedAt": "2024-04-15T10:00:00Z"
}
```

#### Condition Examples

**LOG_COUNT**: Trigger on log volume
```json
{
  "type": "LOG_COUNT",
  "level": "ERROR",
  "threshold": 10,
  "window_minutes": 5,
  "service_pattern": "order-*"
}
```

**LATENCY**: Trigger on endpoint latency
```json
{
  "type": "LATENCY",
  "endpoint": "/api/orders",
  "threshold_ms": 3000,
  "window_minutes": 1
}
```

**ERROR_RATE**: Trigger on error percentage
```json
{
  "type": "ERROR_RATE",
  "threshold_percent": 5,
  "window_minutes": 10,
  "app_names": ["order-service", "payment-service"]
}
```

**CUSTOM**: Application-defined condition
```json
{
  "type": "CUSTOM",
  "rule_id": "custom_rule_123",
  "params": {
    "custom_field": "value"
  }
}
```

#### List Alert Rules

```http
GET /api/alert-rules

HTTP/1.1 200 OK
[
  {
    "id": 1,
    "name": "High Error Rate on Order Service",
    "severity": "CRITICAL",
    "enabled": true,
    ...
  }
]
```

#### Toggle Alert Rule

```http
PATCH /api/alert-rules/1/toggle

HTTP/1.1 200 OK
{
  "id": 1,
  "enabled": false,  // Was true, now disabled
  ...
}
```

### Incident Endpoints

#### Search Incidents (Dynamic Query)

```http
GET /api/incidents?status=TRIGGERED&severity=CRITICAL&from=2024-04-14T00:00:00Z&to=2024-04-15T23:59:59Z&page=0&size=20&sort=triggeredAt,desc

HTTP/1.1 200 OK
{
  "content": [
    {
      "id": 101,
      "ruleId": 1,
      "title": "High Error Rate Detected",
      "status": "TRIGGERED",
      "severity": "CRITICAL",
      "triggeredAt": "2024-04-15T10:00:00Z",
      "acknowledgedAt": null,
      "resolvedAt": null,
      "timeline": [
        {
          "at": "2024-04-15T10:00:00Z",
          "action": "TRIGGERED",
          "detail": "ERROR 15 occurrences/5min"
        }
      ],
      "version": 1,
      "createdAt": "2024-04-15T10:00:00Z"
    }
  ],
  "pageable": {
    "size": 20,
    "number": 0
  },
  "totalElements": 42,
  "totalPages": 3
}
```

#### Acknowledge Incident

```http
PATCH /api/incidents/101/acknowledge
Content-Type: application/json

{
  "userId": 5
}

HTTP/1.1 200 OK
{
  "id": 101,
  "status": "ACKNOWLEDGED",
  "acknowledgedAt": "2024-04-15T10:05:00Z",
  "acknowledgedBy": 5,
  "version": 2,
  "timeline": [
    {
      "at": "2024-04-15T10:00:00Z",
      "action": "TRIGGERED"
    },
    {
      "at": "2024-04-15T10:05:00Z",
      "action": "ACKNOWLEDGED",
      "userId": 5
    }
  ],
  ...
}
```

#### Resolve Incident

```http
PATCH /api/incidents/101/resolve
Content-Type: application/json

{
  "userId": 5,
  "summary": "Root cause: DB connection pool exhausted. Solution: increased pool size from 10 to 20."
}

HTTP/1.1 200 OK
{
  "id": 101,
  "status": "RESOLVED",
  "resolvedAt": "2024-04-15T10:30:00Z",
  "resolvedBy": 5,
  "summary": "Root cause: DB connection pool exhausted...",
  "version": 3,
  "timeline": [
    {
      "at": "2024-04-15T10:00:00Z",
      "action": "TRIGGERED"
    },
    {
      "at": "2024-04-15T10:05:00Z",
      "action": "ACKNOWLEDGED",
      "userId": 5
    },
    {
      "at": "2024-04-15T10:30:00Z",
      "action": "RESOLVED",
      "userId": 5,
      "detail": "Root cause: DB connection pool..."
    }
  ],
  ...
}
```

#### Incident Status Counts

```http
GET /api/incidents/count

HTTP/1.1 200 OK
{
  "triggered": 5,
  "acknowledged": 3,
  "resolved": 127
}
```

## Data Models

### Agent Entity

Represents a running observability agent collecting logs/metrics from a Spring Boot application.

| Field | Type | Nullable | Notes |
|-------|------|----------|-------|
| id | BIGSERIAL | ✗ | PK, auto-generated |
| workerId | VARCHAR(100) | ✗ | UNIQUE; e.g., "worker-order-001" |
| appName | VARCHAR(100) | ✗ | Application name |
| status | ENUM | ✗ | RUNNING, STOPPED, UNKNOWN |
| sdkVersion | VARCHAR(50) | ✓ | e.g., "1.0.0" |
| javaVersion | VARCHAR(50) | ✓ | e.g., "21.0.1" |
| hostname | VARCHAR(255) | ✓ | Pod/VM hostname |
| metadata | JSONB | ✗ | Flexible key-value storage (indexed with GIN) |
| lastHeartbeat | TIMESTAMPTZ | ✓ | Last ping time |
| registeredAt | TIMESTAMPTZ | ✗ | Creation timestamp (immutable) |
| updatedAt | TIMESTAMPTZ | ✗ | Last update timestamp |

**Business Logic**:
- `heartbeat()` — Updates lastHeartbeat, sets status to RUNNING
- `markStopped()` — Sets status to STOPPED
- `updateInfo()` — Partial update of appName, sdkVersion, etc.
- **Scheduled Task**: `checkStaleAgents()` runs every 60s, marks agents as STOPPED if no heartbeat for 5 minutes

### AlertRule Entity

Defines conditions that trigger automatic incident creation.

| Field | Type | Nullable | Notes |
|-------|------|----------|-------|
| id | BIGSERIAL | ✗ | PK |
| name | VARCHAR(200) | ✗ | Rule display name |
| description | TEXT | ✓ | Optional documentation |
| severity | ENUM | ✗ | CRITICAL, WARNING, INFO |
| condition | JSONB | ✗ | Rule condition schema (indexed with GIN) |
| notifyChannels | TEXT[] | ✗ | PostgreSQL array: ["slack", "email"] |
| cooldownMinutes | INT | ✗ | Minimum gap between triggers (default: 30) |
| enabled | BOOLEAN | ✗ | Soft-delete via enabled=false (partial index) |
| createdBy | BIGINT | ✓ | FK to users.id (future) |
| lastTriggered | TIMESTAMPTZ | ✓ | Track cooldown enforcement |
| createdAt | TIMESTAMPTZ | ✗ | Creation timestamp |
| updatedAt | TIMESTAMPTZ | ✗ | Last update timestamp |

**Indexes**:
- `idx_alert_rules_enabled` (partial, enabled=true only) — Fast rule evaluation
- `idx_alert_rules_condition` (GIN) — JSONB condition queries
- `idx_alert_rules_severity` — Filter by severity

**Business Logic**:
- `canTrigger()` — Returns true if cooldown period expired
- `markTriggered()` — Updates lastTriggered timestamp
- `enable()` / `disable()` — Toggle without delete
- Service validates condition has "type" field (LOG_COUNT, LATENCY, ERROR_RATE, CUSTOM)

### Incident Entity

Represents an event triggered by an AlertRule, with state machine transitions.

| Field | Type | Nullable | Notes |
|-------|------|----------|-------|
| id | BIGSERIAL | ✗ | PK |
| ruleId | BIGINT | ✗ | FK to alert_rules.id |
| title | VARCHAR(300) | ✗ | Incident title |
| status | ENUM | ✗ | TRIGGERED → ACKNOWLEDGED → RESOLVED (state machine) |
| severity | ENUM | ✗ | Copied from AlertRule at trigger time |
| triggeredAt | TIMESTAMPTZ | ✗ | When incident was created |
| acknowledgedAt | TIMESTAMPTZ | ✓ | When acknowledged |
| resolvedAt | TIMESTAMPTZ | ✓ | When resolved |
| acknowledgedBy | BIGINT | ✓ | FK to users.id |
| resolvedBy | BIGINT | ✓ | FK to users.id |
| summary | TEXT | ✓ | Resolution summary/root cause |
| timeline | JSONB | ✗ | Immutable event log (array of {at, action, userId?, detail?}) |
| version | INT | ✗ | **Optimistic Locking**: Incremented on each update, prevents concurrent modifications |
| createdAt | TIMESTAMPTZ | ✗ | Creation timestamp |
| updatedAt | TIMESTAMPTZ | ✗ | Last update timestamp |

**State Machine**:
```
TRIGGERED
  ├─→ ACKNOWLEDGED (via acknowledge())
  └─→ RESOLVED (via resolve())

ACKNOWLEDGED
  └─→ RESOLVED (via resolve())

RESOLVED (final state, no transitions)
```

**Indexes**:
- `idx_incidents_status` — Filter by current status
- `idx_incidents_rule_id` — Find incidents for a rule
- `idx_incidents_triggered_at` (DESC) — Recent incidents first
- `idx_incidents_severity_status` — Combined filter (severity + status)

**Optimistic Locking**: `@Version` field prevents lost updates when multiple users modify incident simultaneously. Throws `ObjectOptimisticLockingFailureException` wrapped in `BusinessException(OPTIMISTIC_LOCK_CONFLICT)`.

### AuditLog Entity

Immutable record of all changes to audited entities (Users/Teams future).

| Field | Type | Nullable | Notes |
|-------|------|----------|-------|
| id | BIGSERIAL | ✗ | PK |
| userId | BIGINT | ✗ | Who made the change |
| userEmail | VARCHAR(255) | ✗ | User email (denormalized for audit) |
| action | ENUM | ✗ | CREATE, UPDATE, DELETE, STATUS_CHANGE, LOGIN |
| entityType | VARCHAR(50) | ✗ | "Agent", "AlertRule", "Incident", "User" |
| entityId | BIGINT | ✓ | ID of changed entity |
| changes | JSONB | ✗ | {"field": {old: value, new: value}, ...} |
| ipAddress | VARCHAR(50) | ✓ | Client IP for security audit |
| createdAt | TIMESTAMPTZ | ✗ | When logged (immutable) |

**Design**: No UPDATE or DELETE operations allowed (write-only for compliance).

## File Structure

```
log-friends-console/
├── src/main/kotlin/com/logfriends/platform/
│   ├── PlatformApplication.kt           # @SpringBootApplication, @EnableJpaAuditing
│   ├── config/
│   │   └── AppConfig.kt                # @EnableScheduling, @EnableAsync
│   ├── api/
│   │   ├── rest/
│   │   │   ├── AgentController.kt      # GET/POST/PATCH /api/agents/*
│   │   │   ├── AlertRuleController.kt  # GET/POST/PATCH /api/alert-rules/*
│   │   │   └── IncidentController.kt   # GET/POST/PATCH /api/incidents/*
│   │   └── dto/
│   │       ├── AgentDto.kt             # AgentRegisterRequest, AgentResponse, etc.
│   │       ├── AlertRuleDto.kt         # AlertRuleCreateRequest, AlertRuleResponse, etc.
│   │       └── IncidentDto.kt          # IncidentTriggerRequest, IncidentResponse, etc.
│   ├── domain/
│   │   ├── agent/
│   │   │   ├── entity/
│   │   │   │   ├── Agent.kt            # @Entity, status machine (heartbeat, markStopped)
│   │   │   │   └── AgentStatus.kt      # RUNNING, STOPPED, UNKNOWN
│   │   │   ├── repository/
│   │   │   │   └── AgentRepository.kt  # JpaRepository + custom queries
│   │   │   └── service/
│   │   │       └── AgentService.kt     # register, heartbeat, checkStaleAgents (scheduled)
│   │   ├── alert/
│   │   │   ├── entity/
│   │   │   │   ├── AlertRule.kt        # @Entity, JSONB condition, cooldown logic
│   │   │   │   └── Severity.kt         # CRITICAL, WARNING, INFO
│   │   │   ├── repository/
│   │   │   │   └── AlertRuleRepository.kt
│   │   │   └── service/
│   │   │       └── AlertRuleService.kt # CRUD, validateCondition (LOG_COUNT, LATENCY, etc.)
│   │   ├── incident/
│   │   │   ├── entity/
│   │   │   │   ├── Incident.kt         # @Entity, state machine, optimistic locking
│   │   │   │   └── IncidentStatus.kt   # TRIGGERED, ACKNOWLEDGED, RESOLVED
│   │   │   ├── repository/
│   │   │   │   ├── IncidentRepository.kt   # JpaRepository + JpaSpecificationExecutor
│   │   │   │   └── IncidentSpec.kt         # JPA Specification builders for dynamic queries
│   │   │   └── service/
│   │   │       └── IncidentService.kt  # search, trigger, acknowledge, resolve
│   │   ├── audit/
│   │   │   ├── entity/
│   │   │   │   ├── AuditLog.kt         # @Entity immutable, JSONB changes
│   │   │   │   └── AuditAction.kt      # CREATE, UPDATE, DELETE, etc.
│   │   │   ├── repository/
│   │   │   │   └── AuditLogRepository.kt
│   │   │   └── service/
│   │   │       └── AuditService.kt     # log actions (future: integrate with listeners)
│   ├── common/
│   │   ├── entity/
│   │   │   └── BaseEntity.kt           # @MappedSuperclass, id, createdAt, updatedAt
│   │   └── exception/
│   │       ├── BusinessException.kt    # Custom runtime exception
│   │       └── ErrorCode.kt            # Enum of HTTP status + messages (Korean)
│   └── (+ GlobalExceptionHandler future)
├── src/main/resources/
│   ├── application.yml                 # Spring Boot config, Flyway, Hibernate
│   └── db/migration/
│       ├── V1__create_teams_users.sql  # users table (future auth)
│       ├── V2__create_agents.sql       # agents + indexes
│       ├── V3__create_alert_rules.sql  # alert_rules + GIN indexes
│       ├── V4__create_incidents.sql    # incidents + optimistic locking
│       └── V5__create_audit_logs.sql   # audit_logs (immutable)
├── build.gradle.kts                    # Kotlin 2.3.20, Spring Boot 3.4.0, Java 21
├── settings.gradle.kts
└── README.md
```

## Related Repositories

- **[log-friends-sdk](https://github.com/...)** — Pure Kotlin SDK (compileOnly) for instrumentation
- **[java-agent](https://github.com/...)** — ByteBuddy agent with Kafka producer (Shadow JAR)
- **[log-friends-pipeline](https://github.com/...)** — Spark + ClickHouse/TimescaleDB ingestion
- **[log-friends-examples](https://github.com/...)** — Sample Spring Boot apps with console integration
- **[docs](https://github.com/...)** — Architecture, ADRs, design docs

## Development

### Technologies

- **Kotlin 2.3.20** (JVM, no reflection abuses)
- **Spring Boot 3.4.0** (minimal starters: web, data-jpa, validation, actuator)
- **JPA/Hibernate 6** (Jakarta Persistence API)
- **PostgreSQL 15+** (JSONB, GIN indexes, optimistic locking)
- **Flyway 9** (SQL migrations, no XML)
- **Jackson** (ISO 8601 dates, non-null serialization)

### Building

```bash
./gradlew build              # Full build (tests + JAR)
./gradlew bootRun            # Run locally
./gradlew test               # Unit tests
```

### Testing

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests AgentServiceTest
```

## Troubleshooting

**Issue**: Incident update fails with "Optimistic Lock Conflict"
- **Cause**: Another user modified the incident between read and write
- **Solution**: Fetch fresh copy (version will be incremented), retry

**Issue**: AlertRule trigger rejected (cooldown)
- **Cause**: Rule was triggered less than `cooldownMinutes` ago
- **Solution**: Wait for cooldown period, or adjust `cooldownMinutes` in PUT /api/alert-rules/{id}

**Issue**: Agent marked as STOPPED immediately after registration
- **Cause**: No heartbeat within 5 minutes (scheduled task runs every 60s)
- **Solution**: Ensure agent sends POST /api/agents/heartbeat at least every 5 minutes

**Issue**: Database migrations fail on startup
- **Cause**: Previous migration left in incomplete state
- **Solution**: Check PostgreSQL logs, manually inspect `flyway_schema_history` table, run `REPAIR` if needed

## License

Part of Log Friends observability stack. See main repository for details.
