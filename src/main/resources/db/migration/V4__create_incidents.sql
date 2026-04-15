-- =============================================
-- V4: 인시던트 테이블 (상태 머신)
-- =============================================

CREATE TABLE incidents (
    id              BIGSERIAL    PRIMARY KEY,
    rule_id         BIGINT       NOT NULL REFERENCES alert_rules(id),
    title           VARCHAR(300) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'TRIGGERED',
    -- TRIGGERED → ACKNOWLEDGED → RESOLVED
    severity        VARCHAR(20)  NOT NULL,
    triggered_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    acknowledged_at TIMESTAMPTZ,
    resolved_at     TIMESTAMPTZ,
    acknowledged_by BIGINT       REFERENCES users(id),
    resolved_by     BIGINT       REFERENCES users(id),
    summary         TEXT,
    -- 해결 요약 (풀이 노트)
    timeline        JSONB        NOT NULL DEFAULT '[]',
    -- 이벤트 타임라인:
    -- [
    --   {"at": "2024-04-01T10:00:00Z", "action": "TRIGGERED", "detail": "ERROR 15건/5분"},
    --   {"at": "2024-04-01T10:02:00Z", "action": "ACKNOWLEDGED", "by": "user@example.com"},
    --   {"at": "2024-04-01T10:30:00Z", "action": "RESOLVED", "by": "user@example.com", "summary": "DB 연결 풀 증가로 해결"}
    -- ]
    version         INT          NOT NULL DEFAULT 0,
    -- Optimistic Locking: 동시 상태 변경 방지
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_incidents_status ON incidents(status);
CREATE INDEX idx_incidents_rule_id ON incidents(rule_id);
CREATE INDEX idx_incidents_triggered_at ON incidents(triggered_at DESC);
CREATE INDEX idx_incidents_severity_status ON incidents(severity, status);
