ALTER TABLE log_spec_snapshots
    ADD COLUMN api_method VARCHAR(20),
    ADD COLUMN api_path TEXT,
    ADD COLUMN api_description TEXT;
