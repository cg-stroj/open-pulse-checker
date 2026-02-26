CREATE TABLE monitors (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    type VARCHAR(20) NOT NULL,
    target_url VARCHAR(1024) NOT NULL,
    interval_sec INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL,
    timeout_ms INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE check_results (
    id UUID PRIMARY KEY,
    monitor_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    status_code INTEGER,
    latency_ms BIGINT,
    checked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    error VARCHAR(2048),
    CONSTRAINT fk_check_result_monitor FOREIGN KEY (monitor_id) REFERENCES monitors(id)
);

CREATE INDEX idx_check_results_monitor_checked_at ON check_results(monitor_id, checked_at DESC);

CREATE TABLE incidents (
    id UUID PRIMARY KEY,
    monitor_id UUID NOT NULL,
    state VARCHAR(20) NOT NULL,
    opened_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE,
    reason VARCHAR(1024) NOT NULL,
    CONSTRAINT fk_incident_monitor FOREIGN KEY (monitor_id) REFERENCES monitors(id)
);

CREATE INDEX idx_incidents_monitor_state_opened ON incidents(monitor_id, state, opened_at DESC);
