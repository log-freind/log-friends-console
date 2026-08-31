-- V12: Add sourceType to agents and client event metadata to custom_events

-- 1. Add source_type to agents table with default 'JVM'
ALTER TABLE agents ADD COLUMN IF NOT EXISTS source_type VARCHAR(20) NOT NULL DEFAULT 'JVM';
CREATE INDEX IF NOT EXISTS idx_agents_source_type ON agents(source_type);

-- 2. Add client event metadata columns to custom_events hypertable
ALTER TABLE custom_events ADD COLUMN IF NOT EXISTS event_id VARCHAR(100);
ALTER TABLE custom_events ADD COLUMN IF NOT EXISTS session_id VARCHAR(100);
ALTER TABLE custom_events ADD COLUMN IF NOT EXISTS app_instance_id VARCHAR(100);
ALTER TABLE custom_events ADD COLUMN IF NOT EXISTS received_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- 3. Create indexes for efficient session & event queries on custom_events
CREATE INDEX IF NOT EXISTS idx_custom_events_session_ts ON custom_events(session_id, ts DESC);
CREATE INDEX IF NOT EXISTS idx_custom_events_event_id ON custom_events(event_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_custom_events_event_id_ts ON custom_events(event_id, ts) WHERE event_id IS NOT NULL;
