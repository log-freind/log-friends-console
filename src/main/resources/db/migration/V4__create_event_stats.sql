CREATE TABLE event_stats (
    id              BIGSERIAL    PRIMARY KEY,
    agent_id        BIGINT       NOT NULL REFERENCES agents(id) ON DELETE CASCADE,
    event_type      VARCHAR(20)  NOT NULL,
    window_start    TIMESTAMPTZ  NOT NULL,
    count           BIGINT       NOT NULL DEFAULT 0,
    error_count     BIGINT       NOT NULL DEFAULT 0,
    avg_duration_ms DOUBLE PRECISION,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_event_stats_agent_type_window UNIQUE (agent_id, event_type, window_start)
);

CREATE INDEX idx_event_stats_agent_window ON event_stats(agent_id, window_start DESC);
CREATE INDEX idx_event_stats_event_type ON event_stats(event_type);
CREATE INDEX idx_event_stats_window_start ON event_stats(window_start DESC);
