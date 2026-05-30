CREATE TABLE discovered_log_events (
    id              BIGSERIAL    PRIMARY KEY,
    agent_id        BIGINT       NOT NULL REFERENCES agents(id) ON DELETE CASCADE,
    event_name      VARCHAR(200) NOT NULL,
    source_class    VARCHAR(500) NOT NULL,
    source_method   VARCHAR(200) NOT NULL,
    parameter_names JSONB        NOT NULL DEFAULT '[]'::jsonb,
    app_version     VARCHAR(100),
    status          VARCHAR(20)  NOT NULL DEFAULT 'DISCOVERED',
    first_seen_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_discovered_log_events_source
        UNIQUE (agent_id, event_name, source_class, source_method)
);

CREATE INDEX idx_discovered_log_events_agent_status
    ON discovered_log_events(agent_id, status);

CREATE INDEX idx_discovered_log_events_event_name
    ON discovered_log_events(event_name);
