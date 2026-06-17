CREATE TABLE stored_files (
    id UUID PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL CHECK (file_size >= 0),
    file_type VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    content BYTEA NOT NULL DEFAULT ''::bytea,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_stored_files_created_at
    ON stored_files (created_at DESC);

