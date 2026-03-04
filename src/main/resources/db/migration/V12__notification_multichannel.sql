ALTER TABLE notification_route_rules ADD COLUMN email_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE notification_route_rules ADD COLUMN telegram_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE notification_route_rules ADD COLUMN slack_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE notification_route_rules ADD COLUMN discord_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE notification_route_rules ADD COLUMN teams_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE notification_escalation_steps ADD COLUMN email_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE notification_escalation_steps ADD COLUMN telegram_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE notification_escalation_steps ADD COLUMN slack_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE notification_escalation_steps ADD COLUMN discord_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE notification_escalation_steps ADD COLUMN teams_enabled BOOLEAN NOT NULL DEFAULT FALSE;
