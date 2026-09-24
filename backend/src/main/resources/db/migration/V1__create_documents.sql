CREATE TABLE documents (
    id             UUID PRIMARY KEY,
    file_name      VARCHAR(255) NOT NULL,
    content_type   VARCHAR(255),
    size_bytes     BIGINT       NOT NULL,
    storage_key    VARCHAR(512) NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    chunk_count    INTEGER,
    error_message  TEXT,
    created_at     TIMESTAMPTZ  NOT NULL,
    indexed_at     TIMESTAMPTZ
);

CREATE INDEX idx_documents_created_at ON documents (created_at DESC);
