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
-- IVFFLAT vs HNSW — and why this schema uses HNSW:
--   * IVFFlat partitions vectors into 'lists' clusters via k-means AT INDEX-BUILD
--     TIME, then a query scans only the closest clusters. It is cheap and light,
--     but its clusters reflect the data present when the index is built. This
--     script runs against an EMPTY database at container init, so an IVFFlat
--     index would be trained on no data and would not find rows inserted later
--     (you would have to REINDEX after loading). That actually bit us: unscoped
--     similarity search returned no matches until the index was rebuilt as HNSW.
--   * HNSW builds a multi-layer proximity graph incrementally and needs no
--     training step, so it works correctly even though the index is created
--     before any rows exist and documents are ingested continuously afterward.
--     It also gives better recall/latency at scale, at the cost of slower builds
--     and more memory.
--
-- For this app (index created up-front, rows inserted continuously by the
-- ingestion consumer) HNSW is the correct choice. You would only prefer IVFFlat
-- if you bulk-load the vectors first and then build/REINDEX the index, typically
-- at very large scale where its smaller memory footprint matters.
CREATE INDEX IF NOT EXISTS idx_chunks_embedding
    ON document_chunks USING hnsw (embedding vector_cosine_ops);
