CREATE EXTENSION IF NOT EXISTS timescaledb;

SELECT create_hypertable('http_events',          'ts',        if_not_exists => TRUE);
SELECT create_hypertable('logs',                 'ts',        if_not_exists => TRUE);
SELECT create_hypertable('jdbc_events',          'ts',        if_not_exists => TRUE);
SELECT create_hypertable('method_traces',        'ts',        if_not_exists => TRUE);
SELECT create_hypertable('custom_events',        'ts',        if_not_exists => TRUE);
SELECT create_hypertable('ingest_failed_events', 'failed_at', if_not_exists => TRUE);
