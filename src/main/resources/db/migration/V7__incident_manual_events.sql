CREATE TABLE incident_manual_events (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL,
    action VARCHAR(40) NOT NULL,
    actor VARCHAR(120) NOT NULL,
    reason VARCHAR(2048) NOT NULL,
    from_state VARCHAR(20) NOT NULL,
    to_state VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_manual_event_incident FOREIGN KEY (incident_id) REFERENCES incidents(id) ON DELETE CASCADE
);

CREATE INDEX idx_incident_manual_events_incident_occurred ON incident_manual_events(incident_id, occurred_at ASC);
