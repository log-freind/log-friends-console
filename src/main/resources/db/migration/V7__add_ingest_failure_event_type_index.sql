CREATE INDEX IF NOT EXISTS idx_ingest_failed_events_event_type_failed_at
    ON ingest_failed_events(event_type, failed_at DESC);
