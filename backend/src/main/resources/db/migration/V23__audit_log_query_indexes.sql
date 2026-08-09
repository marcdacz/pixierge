CREATE INDEX audit_events_sequence_desc_idx ON audit_events (sequence DESC);
CREATE INDEX audit_events_actor_created_idx ON audit_events (actor_user_id, created_at DESC);
CREATE INDEX audit_events_event_type_idx ON audit_events (event_type);
