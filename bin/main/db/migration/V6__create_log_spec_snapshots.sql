-- =============================================
-- V6: LogSpec 스냅샷 테이블
-- SDK에서 등록된 LogSpec 정의를 Agent별로 저장
-- =============================================

CREATE TABLE log_spec_snapshots (
    id           BIGSERIAL    PRIMARY KEY,
    agent_id     BIGINT       NOT NULL REFERENCES agents(id) ON DELETE CASCADE,
    spec_name    VARCHAR(200) NOT NULL,
    description  TEXT         NOT NULL DEFAULT '',
    levels       JSONB        NOT NULL DEFAULT '[]',
    -- ["INFO", "WARN", "ERROR"]
    category     VARCHAR(50)  NOT NULL DEFAULT 'BUSINESS',
    fields       JSONB        NOT NULL DEFAULT '[]',
    -- [{"name": "orderId", "type": "STRING", "required": true, ...}]
    last_seen_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_log_spec_agent_name UNIQUE (agent_id, spec_name)
);

CREATE INDEX idx_log_spec_snapshots_agent_id ON log_spec_snapshots(agent_id);
CREATE INDEX idx_log_spec_snapshots_spec_name ON log_spec_snapshots(spec_name);
