package com.documind.common.retrieval;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The SHARED pgvector store, accessed with plain {@link JdbcTemplate} + native
 * SQL (Java equivalent of {@code documind_common/vector_store.py} +
 * {@code retrieval.py}'s SQL arms). document-service writes via
 * {@link #insertChunk}; query-service reads via the search methods. Both go
 * through this one class so writer and reader can never diverge on the schema.
 *
 * <p>Embeddings are passed in as vector literals (see {@link VectorLiterals}),
 * so this module needs no Spring AI dependency — the caller owns the
 * {@code EmbeddingModel}.</p>
 */
@Component
public class PgVectorStore {

    /** chunk_index ASC reading order for whole-document mode. */
    private static final String SELECT_COLS = """
            SELECT c.id          AS chunk_id,
                   c.document_id AS document_id,
                   c.chunk_index AS chunk_index,
                   c.content     AS content,
                   d.filename    AS filename
            """;

    private final JdbcTemplate jdbc;

    public PgVectorStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<ChunkMatch> ROW = (rs, n) -> new ChunkMatch(
            rs.getObject("chunk_id", UUID.class),
            rs.getObject("document_id", UUID.class),
            rs.getInt("chunk_index"),
            rs.getString("content"),
            rs.getString("filename"),
            hasDistance(rs) ? rs.getDouble("distance") : 0.0
    );

    private static boolean hasDistance(java.sql.ResultSet rs) {
        try {
            rs.findColumn("distance");
            return true;
        } catch (java.sql.SQLException e) {
            return false;
        }
    }

    /** Idempotent write: insert one embedded chunk (id is deterministic upstream). */
    public void insertChunk(UUID id, UUID documentId, int chunkIndex, String content, String embeddingLiteral) {
        jdbc.update("""
                INSERT INTO document_chunks (id, document_id, chunk_index, content, embedding)
                VALUES (?, ?, ?, ?, CAST(? AS vector))
                ON CONFLICT (id) DO UPDATE SET content = EXCLUDED.content, embedding = EXCLUDED.embedding
                """, id, documentId, chunkIndex, content, embeddingLiteral);
    }

    /** Lets a re-run after a partial failure start clean (consumer idempotency). */
    public void deleteByDocumentId(UUID documentId) {
        jdbc.update("DELETE FROM document_chunks WHERE document_id = ?", documentId);
    }

    /** Dense arm: cosine nearest-neighbour, optionally scoped to one document. */
    public List<ChunkMatch> vectorSearch(String embeddingLiteral, UUID documentId, int limit) {
        StringBuilder sql = new StringBuilder(SELECT_COLS)
                .append(", c.embedding <=> CAST(? AS vector) AS distance ")
                .append("FROM document_chunks c JOIN documents d ON d.id = c.document_id ");
        List<Object> args = new ArrayList<>();
        args.add(embeddingLiteral);                 // SELECT distance
        if (documentId != null) {
            sql.append("WHERE c.document_id = ? ");
            args.add(documentId);                   // WHERE
        }
        sql.append("ORDER BY c.embedding <=> CAST(? AS vector) LIMIT ?");
        args.add(embeddingLiteral);                 // ORDER BY
        args.add(limit);
        return jdbc.query(sql.toString(), ROW, args.toArray());
    }

    /** Sparse arm: Postgres full-text search (exact terms / acronyms / IDs). */
    public List<ChunkMatch> keywordSearch(String query, UUID documentId, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder(SELECT_COLS)
                .append(", 0.0 AS distance ")
                .append("FROM document_chunks c JOIN documents d ON d.id = c.document_id ")
                .append("WHERE to_tsvector('english', c.content) @@ websearch_to_tsquery('english', ?) ");
        List<Object> args = new ArrayList<>();
        args.add(query);                            // WHERE match
        if (documentId != null) {
            sql.append("AND c.document_id = ? ");
            args.add(documentId);
        }
        sql.append("ORDER BY ts_rank(to_tsvector('english', c.content), websearch_to_tsquery('english', ?)) DESC LIMIT ?");
        args.add(query);                            // ORDER BY rank
        args.add(limit);
        return jdbc.query(sql.toString(), ROW, args.toArray());
    }

    /** Whole-document mode: every chunk of one document in reading order. */
    public List<ChunkMatch> fetchDocumentChunks(UUID documentId, int limit) {
        String sql = SELECT_COLS +
                "FROM document_chunks c JOIN documents d ON d.id = c.document_id " +
                "WHERE c.document_id = ? ORDER BY c.chunk_index ASC LIMIT ?";
        return jdbc.query(sql, ROW, documentId, limit);
    }
}
