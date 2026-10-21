CREATE INDEX idx_outbox_capacity_status ON outbox_events (status, id);
