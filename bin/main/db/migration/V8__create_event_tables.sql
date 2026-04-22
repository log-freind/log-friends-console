-- =============================================
-- V8: 원시 이벤트 테이블 (SDK → 콘솔 직접 수신)
-- SDK가 HTTP POST /ingest 로 전송한 이벤트를 저장
-- =============================================

CREATE TABLE log_events (
    id              BIGSERIAL    PRIMARY KEY,
    worker_id       VARCHAR(100) NOT NULL,
    ts              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    level           VARCHAR(10)  NOT NULL DEFAULT 'INFO',
    logger_name     TEXT         NOT NULL DEFAULT '',
    thread_name     TEXT         NOT NULL DEFAULT '',
    message         TEXT         NOT NULL DEFAULT '',
    exception       TEXT         NOT NULL DEFAULT '',
    exception_stack TEXT         NOT NULL DEFAULT '',
    trace_id        VARCHAR(100) NOT NULL DEFAULT '',
    mdc             JSONB        NOT NULL DEFAULT '{}'
);

CREATE TABLE http_events (
    id              BIGSERIAL    PRIMARY KEY,
    worker_id       VARCHAR(100) NOT NULL,
    ts              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    method          VARCHAR(10)  NOT NULL DEFAULT '',
    uri             TEXT         NOT NULL DEFAULT '',
    status_code     INT          NOT NULL DEFAULT 0,
    duration_ms     BIGINT       NOT NULL DEFAULT 0,
    trace_id        VARCHAR(100) NOT NULL DEFAULT '',
    exception_stack TEXT         NOT NULL DEFAULT '',
    request_headers JSONB        NOT NULL DEFAULT '{}'
);

CREATE TABLE jdbc_events (
    id              BIGSERIAL    PRIMARY KEY,
    worker_id       VARCHAR(100) NOT NULL,
    ts              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sql_text        TEXT         NOT NULL DEFAULT '',
    duration_ms     BIGINT       NOT NULL DEFAULT 0,
    row_count       INT          NOT NULL DEFAULT 0,
    trace_id        VARCHAR(100) NOT NULL DEFAULT '',
    exception       TEXT         NOT NULL DEFAULT '',
    exception_stack TEXT         NOT NULL DEFAULT ''
);

CREATE TABLE method_trace_events (
    id              BIGSERIAL    PRIMARY KEY,
    worker_id       VARCHAR(100) NOT NULL,
    ts              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    class_name      TEXT         NOT NULL DEFAULT '',
    method_name     TEXT         NOT NULL DEFAULT '',
    duration_ms     BIGINT       NOT NULL DEFAULT 0,
    trace_id        VARCHAR(100) NOT NULL DEFAULT '',
    exception       TEXT         NOT NULL DEFAULT '',
    exception_stack TEXT         NOT NULL DEFAULT ''
);

CREATE TABLE custom_events (
    id          BIGSERIAL    PRIMARY KEY,
    worker_id   VARCHAR(100) NOT NULL,
    ts          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    event_name  TEXT         NOT NULL DEFAULT '',
    fields      JSONB        NOT NULL DEFAULT '{}'
);

CREATE INDEX idx_log_events_worker_ts      ON log_events(worker_id, ts DESC);
CREATE INDEX idx_log_events_level          ON log_events(level);
CREATE INDEX idx_http_events_worker_ts     ON http_events(worker_id, ts DESC);
CREATE INDEX idx_http_events_status        ON http_events(status_code);
CREATE INDEX idx_http_events_duration      ON http_events(duration_ms DESC);
CREATE INDEX idx_jdbc_events_worker_ts     ON jdbc_events(worker_id, ts DESC);
CREATE INDEX idx_jdbc_events_duration      ON jdbc_events(duration_ms DESC);
CREATE INDEX idx_method_trace_worker_ts    ON method_trace_events(worker_id, ts DESC);
CREATE INDEX idx_method_trace_duration     ON method_trace_events(duration_ms DESC);
CREATE INDEX idx_custom_events_worker_ts   ON custom_events(worker_id, ts DESC);
CREATE INDEX idx_custom_events_name        ON custom_events(event_name);
