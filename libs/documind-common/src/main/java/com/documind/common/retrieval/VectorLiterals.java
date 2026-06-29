package com.documind.common.retrieval;

/**
 * Renders an embedding as the text literal pgvector expects — {@code "[0.12,-0.03,...]"}
 * — which is then {@code CAST(... AS vector)} in SQL. Hibernate/JDBC has no
 * native pgvector type, so a literal per row keeps the mapping dead simple and
 * is perfectly fine at this scale.
 */
public final class VectorLiterals {

    private VectorLiterals() {
    }

    public static String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder(embedding.length * 8).append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        return sb.append(']').toString();
    }
}
