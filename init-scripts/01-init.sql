-- DocuMind (Java) schema. Runs once on first Postgres start
-- (mounted into /docker-entrypoint-initdb.d).

CREATE EXTENSION IF NOT EXISTS vector;

-- Document metadata (owned by document-service via JPA).
CREATE TABLE IF NOT EXISTS documents (
    id             UUID PRIMARY KEY,
    filename       VARCHAR(512) NOT NULL,
    status         VARCHAR(32)  NOT NULL,
    uploaded_at    TIMESTAMPTZ  NOT NULL,
    chunk_count    INT          NOT NULL DEFAULT 0,
    failure_reason TEXT
);

-- The SHARED chunk + embedding store (written by document-service, read by
-- query-service, both via documind-common's PgVectorStore).
--
-- The embedding column is intentionally DIMENSIONLESS so the same schema works
-- with any provider (OpenAI 1536-d, Ollama nomic 768-d, ...) without a migration.
-- Trade-off: no ANN (HNSW) index, so search is a sequential scan — perfectly fine
-- at demo scale. At scale: pin one provider, ALTER the column to vector(N), and add
--   CREATE INDEX ... USING hnsw (embedding vector_cosine_ops);
CREATE TABLE IF NOT EXISTS document_chunks (
    id          UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index INT  NOT NULL,
    content     TEXT NOT NULL,
    embedding   vector NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_chunks_document_id ON document_chunks (document_id);

-- Sparse (keyword) arm of hybrid retrieval: full-text GIN index on the content.
CREATE INDEX IF NOT EXISTS idx_chunks_fts
    ON document_chunks USING gin (to_tsvector('english', content));
