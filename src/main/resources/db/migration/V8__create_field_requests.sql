CREATE TABLE field_requests (
    id                   BIGSERIAL    PRIMARY KEY,
    app_name             VARCHAR(100) NOT NULL,
    event_name           VARCHAR(200) NOT NULL,
    requested_field_name VARCHAR(200) NOT NULL,
    requested_type       VARCHAR(20)  NOT NULL,
    reason               TEXT         NOT NULL,
    requested_by         VARCHAR(100),
    status               VARCHAR(20)  NOT NULL DEFAULT 'REQUESTED',
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_field_requests_app_event
    ON field_requests(app_name, event_name);

CREATE INDEX idx_field_requests_status
    ON field_requests(status);

CREATE INDEX idx_field_requests_open_unique
    ON field_requests(app_name, event_name, requested_field_name)
    WHERE status IN ('REQUESTED', 'ACCEPTED');
