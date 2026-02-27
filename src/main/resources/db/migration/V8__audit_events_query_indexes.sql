CREATE INDEX IF NOT EXISTS idx_audit_events_action ON audit_events(action);
CREATE INDEX IF NOT EXISTS idx_audit_events_result ON audit_events(result);
CREATE INDEX IF NOT EXISTS idx_audit_events_target ON audit_events(target);
CREATE INDEX IF NOT EXISTS idx_audit_events_occurred_at_id_desc ON audit_events(occurred_at DESC, id DESC);
