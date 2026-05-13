CREATE TABLE http_events (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY,
    worker_id       VARCHAR(100) NOT NULL,
    ts              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    method          VARCHAR(10)  NOT NULL DEFAULT '',
    uri             TEXT         NOT NULL DEFAULT '',
    status_code     INT          NOT NULL DEFAULT 0,
    duration_ms     BIGINT       NOT NULL DEFAULT 0,
    trace_id        VARCHAR(100) NOT NULL DEFAULT '',
    exception_stack TEXT         NOT NULL DEFAULT '',
    request_headers JSONB        NOT NULL DEFAULT '{}',
    PRIMARY KEY (id, ts)
);

CREATE TABLE logs (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY,
    worker_id       VARCHAR(100) NOT NULL,
    ts              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    level           VARCHAR(10)  NOT NULL DEFAULT 'INFO',
    logger_name     TEXT         NOT NULL DEFAULT '',
    thread_name     TEXT         NOT NULL DEFAULT '',
    message         TEXT         NOT NULL DEFAULT '',
    exception       TEXT         NOT NULL DEFAULT '',
    exception_stack TEXT         NOT NULL DEFAULT '',
    trace_id        VARCHAR(100) NOT NULL DEFAULT '',
    mdc             JSONB        NOT NULL DEFAULT '{}',
    PRIMARY KEY (id, ts)
);

CREATE TABLE jdbc_events (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY,
    worker_id       VARCHAR(100) NOT NULL,
    ts              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sql_text        TEXT         NOT NULL DEFAULT '',
    duration_ms     BIGINT       NOT NULL DEFAULT 0,
    row_count       INT          NOT NULL DEFAULT 0,
    trace_id        VARCHAR(100) NOT NULL DEFAULT '',
    exception       TEXT         NOT NULL DEFAULT '',
    exception_stack TEXT         NOT NULL DEFAULT '',
    PRIMARY KEY (id, ts)
);

CREATE TABLE method_traces (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY,
    worker_id       VARCHAR(100) NOT NULL,
    ts              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    class_name      TEXT         NOT NULL DEFAULT '',
    method_name     TEXT         NOT NULL DEFAULT '',
    duration_ms     BIGINT       NOT NULL DEFAULT 0,
    trace_id        VARCHAR(100) NOT NULL DEFAULT '',
    exception       TEXT         NOT NULL DEFAULT '',
    exception_stack TEXT         NOT NULL DEFAULT '',
    PRIMARY KEY (id, ts)
);

CREATE TABLE custom_events (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY,
    worker_id   VARCHAR(100) NOT NULL,
    ts          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    event_name  TEXT         NOT NULL,
    payload     JSONB        NOT NULL DEFAULT '{}',
    PRIMARY KEY (id, ts)
);

CREATE INDEX idx_http_events_worker_ts ON http_events(worker_id, ts DESC);
CREATE INDEX idx_http_events_status ON http_events(status_code);
CREATE INDEX idx_http_events_duration ON http_events(duration_ms DESC);
CREATE INDEX idx_logs_worker_ts ON logs(worker_id, ts DESC);
CREATE INDEX idx_logs_level ON logs(level);
CREATE INDEX idx_jdbc_events_worker_ts ON jdbc_events(worker_id, ts DESC);
CREATE INDEX idx_jdbc_events_duration ON jdbc_events(duration_ms DESC);
CREATE INDEX idx_method_traces_worker_ts ON method_traces(worker_id, ts DESC);
CREATE INDEX idx_method_traces_duration ON method_traces(duration_ms DESC);
CREATE INDEX idx_custom_events_worker_ts ON custom_events(worker_id, ts DESC);
CREATE INDEX idx_custom_events_event_name ON custom_events(event_name);
CREATE INDEX idx_custom_events_payload ON custom_events USING GIN (payload);
