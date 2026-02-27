CREATE TABLE notification_policies (
    id UUID PRIMARY KEY,
    scope_type VARCHAR(32) NOT NULL,
    scope_ref_id UUID,
    enabled BOOLEAN NOT NULL,
    cooldown_seconds INTEGER NOT NULL,
    dedup_seconds INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_notification_policy_scope UNIQUE (scope_type, scope_ref_id),
    CONSTRAINT ck_notification_policy_scope_ref CHECK (
        (scope_type = 'GLOBAL' AND scope_ref_id IS NULL)
        OR (scope_type IN ('MONITOR', 'STATUS_PAGE') AND scope_ref_id IS NOT NULL)
    )
);

CREATE TABLE notification_route_rules (
    id UUID PRIMARY KEY,
    policy_id UUID NOT NULL,
    severity VARCHAR(16) NOT NULL,
    webhook_enabled BOOLEAN NOT NULL,
    CONSTRAINT fk_notification_route_policy FOREIGN KEY (policy_id) REFERENCES notification_policies(id) ON DELETE CASCADE,
    CONSTRAINT uq_notification_route UNIQUE (policy_id, severity)
);

CREATE TABLE notification_escalation_steps (
    id UUID PRIMARY KEY,
    policy_id UUID NOT NULL,
    step_order INTEGER NOT NULL,
    after_seconds INTEGER NOT NULL,
    min_severity VARCHAR(16) NOT NULL,
    webhook_enabled BOOLEAN NOT NULL,
    CONSTRAINT fk_notification_escalation_policy FOREIGN KEY (policy_id) REFERENCES notification_policies(id) ON DELETE CASCADE,
    CONSTRAINT uq_notification_escalation_step UNIQUE (policy_id, step_order)
);

ALTER TABLE dispatched_alerts ADD COLUMN severity VARCHAR(16);
ALTER TABLE dispatched_alerts ADD COLUMN channel VARCHAR(24);
ALTER TABLE dispatched_alerts ADD COLUMN policy_id UUID;

CREATE INDEX idx_notification_policies_scope ON notification_policies(scope_type, scope_ref_id);
CREATE INDEX idx_dispatched_alerts_monitor_channel_severity_created ON dispatched_alerts(monitor_id, channel, severity, created_at DESC);
