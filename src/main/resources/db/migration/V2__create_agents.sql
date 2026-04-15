-- =============================================
-- V2: 에이전트 테이블 (JSONB metadata)
-- =============================================

CREATE TABLE agents (
    id              BIGSERIAL    PRIMARY KEY,
    worker_id       VARCHAR(100) NOT NULL UNIQUE,
    app_name        VARCHAR(100) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN',
    -- RUNNING, STOPPED, UNKNOWN
    sdk_version     VARCHAR(50),
    java_version    VARCHAR(50),
    hostname        VARCHAR(255),
    metadata        JSONB        NOT NULL DEFAULT '{}',
    -- 유연한 추가 정보: {"spring.profiles": "prod", "k8s.pod": "order-abc"}
    last_heartbeat  TIMESTAMPTZ,
    registered_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_agents_worker_id ON agents(worker_id);
CREATE INDEX idx_agents_status ON agents(status);
CREATE INDEX idx_agents_metadata ON agents USING GIN (metadata);
-- GIN 인덱스: JSONB 내 키/값 검색에 최적화
-- 예: WHERE metadata @> '{"spring.profiles": "prod"}'
