package com.org.documind.repository;

/**
 * Renders a float[] as the pgvector text literal {@code [v1,v2,...]} consumed
 * by the {@code CAST(:param AS vector)} native queries in
 * {@link DocumentChunkRepository}.
 */
public final class VectorLiterals {

    private VectorLiterals() {
    }

    public static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 12).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
