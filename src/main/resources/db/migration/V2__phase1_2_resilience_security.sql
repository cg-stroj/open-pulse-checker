CREATE TABLE scheduler_locks (
    lock_name VARCHAR(255) PRIMARY KEY,
    owner_id VARCHAR(128) NOT NULL,
    lease_until TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_scheduler_locks_lease_until ON scheduler_locks(lease_until);

CREATE TABLE dispatched_alerts (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    monitor_id UUID NOT NULL,
    incident_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    username VARCHAR(120) NOT NULL UNIQUE,
    password_hash VARCHAR(120) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL,
    role_name VARCHAR(40) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (user_id, role_name),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE
);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    username VARCHAR(120) NOT NULL,
    action VARCHAR(120) NOT NULL,
    target VARCHAR(255) NOT NULL,
    result VARCHAR(40) NOT NULL,
    details VARCHAR(2048),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_audit_events_occurred_at ON audit_events(occurred_at DESC);
CREATE INDEX idx_audit_events_username ON audit_events(username);
