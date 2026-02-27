CREATE TABLE maintenance_windows (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    scope_type VARCHAR(24) NOT NULL,
    scope_ref_id UUID,
    type VARCHAR(16) NOT NULL,
    policy VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL,
    start_at TIMESTAMP WITH TIME ZONE,
    end_at TIMESTAMP WITH TIME ZONE,
    timezone VARCHAR(64),
    recurring_days VARCHAR(128),
    recurring_start_time VARCHAR(8),
    recurring_end_time VARCHAR(8),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_maintenance_scope CHECK (
        (scope_type = 'GLOBAL' AND scope_ref_id IS NULL)
        OR (scope_type = 'MONITOR' AND scope_ref_id IS NOT NULL)
    ),
    CONSTRAINT ck_maintenance_type_fields CHECK (
        (type = 'ONE_TIME' AND start_at IS NOT NULL AND end_at IS NOT NULL AND timezone IS NULL AND recurring_days IS NULL AND recurring_start_time IS NULL AND recurring_end_time IS NULL)
        OR (type = 'RECURRING' AND start_at IS NULL AND end_at IS NULL AND timezone IS NOT NULL AND recurring_days IS NOT NULL AND recurring_start_time IS NOT NULL AND recurring_end_time IS NOT NULL)
    ),
    CONSTRAINT ck_maintenance_one_time_order CHECK (end_at IS NULL OR start_at IS NULL OR end_at > start_at)
);

CREATE INDEX idx_maintenance_windows_scope_enabled ON maintenance_windows(scope_type, scope_ref_id, enabled);
