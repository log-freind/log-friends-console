CREATE TABLE ingest_failed_events (
    id           BIGINT       GENERATED ALWAYS AS IDENTITY,
    worker_id    VARCHAR(100),
    event_type   VARCHAR(50),
    reason_code  VARCHAR(50)  NOT NULL,
    reason       TEXT         NOT NULL DEFAULT '',
    received_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    failed_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    payload      JSONB        NOT NULL DEFAULT '{}',
    PRIMARY KEY (id, failed_at)
);

CREATE TABLE ingest_failure_stats (
    id           BIGSERIAL    PRIMARY KEY,
    worker_id    VARCHAR(100) NOT NULL,
    reason_code  VARCHAR(50)  NOT NULL,
    window_start TIMESTAMPTZ  NOT NULL,
    count        BIGINT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_ingest_failure_stats_worker_reason_window UNIQUE (worker_id, reason_code, window_start)
);

CREATE INDEX idx_ingest_failed_events_worker_failed_at ON ingest_failed_events(worker_id, failed_at DESC);
CREATE INDEX idx_ingest_failed_events_event_type_failed_at ON ingest_failed_events(event_type, failed_at DESC);
CREATE INDEX idx_ingest_failed_events_reason_code ON ingest_failed_events(reason_code);
CREATE INDEX idx_ingest_failure_stats_worker_window ON ingest_failure_stats(worker_id, window_start DESC);
CREATE INDEX idx_ingest_failure_stats_reason_code ON ingest_failure_stats(reason_code);
