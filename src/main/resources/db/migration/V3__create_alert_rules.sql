-- =============================================
-- V3: 알림 규칙 테이블 (JSONB condition)
-- =============================================

CREATE TABLE alert_rules (
    id              BIGSERIAL    PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    severity        VARCHAR(20)  NOT NULL DEFAULT 'WARNING',
    -- CRITICAL, WARNING, INFO
    condition       JSONB        NOT NULL,
    -- 유연한 조건 구조:
    -- {
    --   "type": "LOG_COUNT",
    --   "level": "ERROR",
    --   "threshold": 10,
    --   "window_minutes": 5,
    --   "service_pattern": "order-*"
    -- }
    -- {
    --   "type": "LATENCY",
    --   "endpoint": "/api/orders",
    --   "threshold_ms": 3000,
    --   "window_minutes": 1
    -- }
    notify_channels TEXT[]       NOT NULL DEFAULT '{}',
    -- PostgreSQL 배열: {"slack", "email"}
    cooldown_minutes INT         NOT NULL DEFAULT 30,
    -- 같은 규칙이 연속 발화 방지 (30분 쿨다운)
    enabled         BOOLEAN      NOT NULL DEFAULT true,
    created_by      BIGINT       REFERENCES users(id),
    last_triggered  TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_alert_rules_enabled ON alert_rules(enabled) WHERE enabled = true;
-- partial index: enabled=true인 것만 인덱싱 → 규칙 평가 시 빠름
CREATE INDEX idx_alert_rules_condition ON alert_rules USING GIN (condition);
CREATE INDEX idx_alert_rules_severity ON alert_rules(severity);
