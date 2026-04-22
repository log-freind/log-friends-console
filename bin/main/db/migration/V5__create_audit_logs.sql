-- =============================================
-- V5: 감사 로그 테이블 (파티셔닝)
-- =============================================

-- 월별 파티션 테이블 (PostgreSQL 선언적 파티셔닝)
CREATE TABLE audit_logs (
    id          BIGSERIAL,
    user_id     BIGINT       NOT NULL,
    user_email  VARCHAR(255) NOT NULL,
    action      VARCHAR(50)  NOT NULL,
    -- CREATE, UPDATE, DELETE, STATUS_CHANGE, LOGIN
    entity_type VARCHAR(50)  NOT NULL,
    -- AGENT, ALERT_RULE, INCIDENT, USER
    entity_id   BIGINT,
    changes     JSONB        NOT NULL DEFAULT '{}',
    -- 변경 상세:
    -- {"before": {"status": "TRIGGERED"}, "after": {"status": "ACKNOWLEDGED"}}
    ip_address  VARCHAR(45),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (id, created_at)
    -- 파티션 키가 PK에 포함되어야 함 (PostgreSQL 제약)
) PARTITION BY RANGE (created_at);

-- 초기 파티션 생성 (2024년 4분기 + 2025년)
CREATE TABLE audit_logs_2025_q1 PARTITION OF audit_logs
    FOR VALUES FROM ('2025-01-01') TO ('2025-04-01');
CREATE TABLE audit_logs_2025_q2 PARTITION OF audit_logs
    FOR VALUES FROM ('2025-04-01') TO ('2025-07-01');
CREATE TABLE audit_logs_2025_q3 PARTITION OF audit_logs
    FOR VALUES FROM ('2025-07-01') TO ('2025-10-01');
CREATE TABLE audit_logs_2025_q4 PARTITION OF audit_logs
    FOR VALUES FROM ('2025-10-01') TO ('2026-01-01');
CREATE TABLE audit_logs_2026_q1 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-01-01') TO ('2026-04-01');
CREATE TABLE audit_logs_2026_q2 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-04-01') TO ('2026-07-01');
CREATE TABLE audit_logs_2026_q3 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-07-01') TO ('2026-10-01');
CREATE TABLE audit_logs_2026_q4 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-10-01') TO ('2027-01-01');
CREATE TABLE audit_logs_default  PARTITION OF audit_logs DEFAULT;

CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);
CREATE INDEX idx_audit_logs_changes ON audit_logs USING GIN (changes);
