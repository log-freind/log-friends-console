-- =============================================
-- V7: 이벤트 통계 테이블
-- Agent별 이벤트 타입 1분 윈도우 집계
-- =============================================

CREATE TABLE event_stats (
    id              BIGSERIAL    PRIMARY KEY,
    agent_id        BIGINT       NOT NULL REFERENCES agents(id) ON DELETE CASCADE,
    event_type      VARCHAR(20)  NOT NULL,
    -- HTTP, LOG, JDBC, METHOD_TRACE, LOG_EVENT
    window_start    TIMESTAMPTZ  NOT NULL,
    count           BIGINT       NOT NULL DEFAULT 0,
    error_count     BIGINT       NOT NULL DEFAULT 0,
    avg_duration_ms DOUBLE PRECISION,
    -- HTTP/JDBC/METHOD_TRACE 평균 응답시간, LOG/LOG_EVENT는 NULL
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_event_stats_agent_window ON event_stats(agent_id, window_start DESC);
CREATE INDEX idx_event_stats_event_type ON event_stats(event_type);
CREATE INDEX idx_event_stats_window_start ON event_stats(window_start DESC);
