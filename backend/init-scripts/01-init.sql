-- Runs automatically on the FIRST start of the postgres container (mounted
-- into /docker-entrypoint-initdb.d by docker-compose.yml).
--
-- This script owns the relational schema. Hibernate cannot generate it because
-- it has no built-in understanding of the pgvector column type, which is why
-- application.yml sets spring.jpa.hibernate.ddl-auto=none.

CREATE EXTENSION IF NOT EXISTS vector;

-- Document metadata: one row per uploaded PDF, tracking its journey through
-- the ingestion pipeline (UPLOADED -> PROCESSING -> READY | FAILED).
CREATE TABLE IF NOT EXISTS documents (
    id             UUID PRIMARY KEY,
    filename       VARCHAR(512) NOT NULL,
    status         VARCHAR(32)  NOT NULL,
    uploaded_at    TIMESTAMP    NOT NULL,
    chunk_count    INTEGER      NOT NULL DEFAULT 0,
    failure_reason TEXT
);

-- One row per text chunk. The embedding dimension (1536) must match the
-- output of text-embedding-3-small configured in application.yml.
CREATE TABLE IF NOT EXISTS document_chunks (
    id          UUID PRIMARY KEY,
    document_id UUID    NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    content     TEXT    NOT NULL,
    embedding   vector(1536) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_chunks_document_id ON document_chunks (document_id);

-- Approximate nearest-neighbour (ANN) index for cosine similarity search.
--
-- WHY IVFFLAT (vs HNSW):
--   * IVFFlat partitions the vectors into 'lists' clusters via k-means at
--     index-build time; a query only scans the few closest clusters instead
--     of every row. It is cheap to build, light on memory, and entirely
--     adequate up to the low hundreds of thousands of vectors.
--   * HNSW builds a multi-layer proximity graph instead: better recall and
--     latency at large scale and no "training" step (it works well even when
--     rows are inserted after index creation), but builds are slower and the
--     index uses significantly more memory.
--   * IVFFlat caveat worth knowing: the clusters reflect the data present at
--     CREATE INDEX time. We create it up-front for simplicity (with demo-sized
--     data Postgres will often just sequential-scan anyway, which is fine);
--     after a real bulk load you would REINDEX so the clusters match the
--     actual data distribution.
--
-- WHEN TO SWITCH TO HNSW: millions of vectors, recall problems traceable to
-- unlucky cluster boundaries, or a write-heavy workload where most vectors
-- arrive after the index is created. The swap is a one-line change:
--     CREATE INDEX ... USING hnsw (embedding vector_cosine_ops);
--
-- lists = 100 follows the pgvector rule of thumb (roughly rows/1000, with a
-- sensible floor) for a table expected to stay under ~100k rows.
CREATE INDEX IF NOT EXISTS idx_chunks_embedding
    ON document_chunks USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
