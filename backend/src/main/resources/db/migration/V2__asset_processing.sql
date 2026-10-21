SET @schema_name = DATABASE();

SET @has_object_key = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'images' AND column_name = 'object_key');
SET @sql = IF(@has_object_key = 0, 'ALTER TABLE images ADD COLUMN object_key VARCHAR(500) NULL', 'SELECT 1');
PREPARE migration_stmt FROM @sql; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

SET @has_sha256 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'images' AND column_name = 'sha256');
SET @sql = IF(@has_sha256 = 0, 'ALTER TABLE images ADD COLUMN sha256 VARCHAR(64) NULL', 'SELECT 1');
PREPARE migration_stmt FROM @sql; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

SET @has_status = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'images' AND column_name = 'status');
SET @sql = IF(@has_status = 0, 'ALTER TABLE images ADD COLUMN status VARCHAR(20) NULL', 'SELECT 1');
PREPARE migration_stmt FROM @sql; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

SET @has_version = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'images' AND column_name = 'version');
SET @sql = IF(@has_version = 0, 'ALTER TABLE images ADD COLUMN version BIGINT NULL', 'SELECT 1');
PREPARE migration_stmt FROM @sql; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

SET @file_path_nullable = (SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'images' AND column_name = 'file_path');
SET @sql = IF(@file_path_nullable = 'NO', 'ALTER TABLE images MODIFY COLUMN file_path VARCHAR(500) NULL', 'SELECT 1');
PREPARE migration_stmt FROM @sql; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

CREATE TABLE IF NOT EXISTS upload_receipts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    image_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_upload_receipt_owner_key UNIQUE (owner_id, idempotency_key),
    INDEX idx_upload_receipt_image (image_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS outbox_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    aggregate_id BIGINT NOT NULL,
    type VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    sent_at DATETIME(6) NULL,
    attempts INT NOT NULL DEFAULT 0,
    INDEX idx_outbox_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @has_images_owner_sha = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'images' AND index_name = 'uk_images_user_sha256'
);
SET @add_images_owner_sha = IF(@has_images_owner_sha = 0,
    'CREATE UNIQUE INDEX uk_images_user_sha256 ON images (user_id, sha256)', 'SELECT 1');
PREPARE add_images_owner_sha FROM @add_images_owner_sha;
EXECUTE add_images_owner_sha;
DEALLOCATE PREPARE add_images_owner_sha;

SET @has_images_owner_time = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'images' AND index_name = 'idx_images_owner_time_id'
);
SET @add_images_owner_time = IF(@has_images_owner_time = 0,
    'CREATE INDEX idx_images_owner_time_id ON images (user_id, upload_time, id)', 'SELECT 1');
PREPARE add_images_owner_time FROM @add_images_owner_time;
EXECUTE add_images_owner_time;
DEALLOCATE PREPARE add_images_owner_time;
