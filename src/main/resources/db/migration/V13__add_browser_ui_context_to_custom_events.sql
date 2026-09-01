-- Browser UI location is query metadata, not a business-event payload field.
ALTER TABLE custom_events ADD COLUMN IF NOT EXISTS page_path TEXT;
ALTER TABLE custom_events ADD COLUMN IF NOT EXISTS component_name TEXT;
ALTER TABLE custom_events ADD COLUMN IF NOT EXISTS parent_component_name TEXT;
ALTER TABLE custom_events ADD COLUMN IF NOT EXISTS component_path JSONB NOT NULL DEFAULT '{}';

CREATE INDEX IF NOT EXISTS idx_custom_events_page_ts ON custom_events(page_path, ts DESC)
    WHERE page_path IS NOT NULL;
