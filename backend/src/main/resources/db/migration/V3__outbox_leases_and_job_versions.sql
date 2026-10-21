UPDATE images SET version = 0 WHERE version IS NULL;
ALTER TABLE images MODIFY COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE outbox_events
    ADD COLUMN aggregate_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN lease_until DATETIME(6) NULL,
    ADD COLUMN next_attempt_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

CREATE INDEX idx_outbox_retry_lease ON outbox_events (status, next_attempt_at, lease_until, created_at);
CREATE INDEX idx_images_owner_status_time_id ON images (user_id, status, upload_time, id);
