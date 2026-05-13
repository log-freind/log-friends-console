CREATE TABLE agents (
    id              BIGSERIAL    PRIMARY KEY,
    worker_id       VARCHAR(100) NOT NULL UNIQUE,
    app_name        VARCHAR(100) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN',
    sdk_version     VARCHAR(50),
    java_version    VARCHAR(50),
    hostname        VARCHAR(255),
    metadata        JSONB        NOT NULL DEFAULT '{}',
    last_heartbeat  TIMESTAMPTZ,
    registered_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_agents_worker_id ON agents(worker_id);
CREATE INDEX idx_agents_app_name ON agents(app_name);
CREATE INDEX idx_agents_status ON agents(status);
CREATE INDEX idx_agents_metadata ON agents USING GIN (metadata);
