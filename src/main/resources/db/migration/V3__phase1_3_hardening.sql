CREATE TABLE service_api_keys (
    id UUID PRIMARY KEY,
    key_id VARCHAR(120) NOT NULL UNIQUE,
    secret_hash VARCHAR(128) NOT NULL,
    role_name VARCHAR(40) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    rotated_at TIMESTAMP WITH TIME ZONE,
    rotated_by VARCHAR(120),
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoked_by VARCHAR(120)
);

CREATE INDEX idx_service_api_keys_enabled ON service_api_keys(enabled);

CREATE TABLE alert_dead_letters (
    id UUID PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    monitor_id UUID NOT NULL,
    incident_id UUID,
    payload VARCHAR(4096) NOT NULL,
    failure_reason VARCHAR(2048) NOT NULL,
    attempts INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    replayed_at TIMESTAMP WITH TIME ZONE,
    replayed_by VARCHAR(120),
    replay_result VARCHAR(40)
);

CREATE INDEX idx_alert_dead_letters_created_at ON alert_dead_letters(created_at DESC);
