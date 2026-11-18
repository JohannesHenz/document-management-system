CREATE TABLE documents (
    id                UUID                     PRIMARY KEY,
    title             VARCHAR(255)             NOT NULL,
    original_filename VARCHAR(255)             NOT NULL,
    content_type      VARCHAR(255)             NOT NULL,
    size_bytes        BIGINT                   NOT NULL,
    storage_key       VARCHAR(64)              NOT NULL UNIQUE,
    uploaded_at       TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_documents_uploaded_at ON documents (uploaded_at);
