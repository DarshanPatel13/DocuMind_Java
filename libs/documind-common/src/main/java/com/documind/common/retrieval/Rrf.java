package com.documind.common.retrieval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reciprocal Rank Fusion: merges several ranked lists into one without needing
 * the arms' scores to be calibrated. Score for a chunk = sum over lists of
 * {@code 1 / (K + rank)}, so a chunk near the top of either arm rises, and being
 * in both arms compounds. {@code K = 60} is the standard damping constant.
 */
public final class Rrf {

    private static final int K = 60;

    private Rrf() {
    }

    public static List<ChunkMatch> fuse(List<List<ChunkMatch>> rankedLists) {
        Map<UUID, Double> scores = new java.util.HashMap<>();
        Map<UUID, ChunkMatch> keep = new LinkedHashMap<>();
        for (List<ChunkMatch> list : rankedLists) {
            for (int rank = 0; rank < list.size(); rank++) {
                ChunkMatch m = list.get(rank);
                scores.merge(m.chunkId(), 1.0 / (K + rank + 1), Double::sum);
                keep.putIfAbsent(m.chunkId(), m);
            }
        }
        return keep.values().stream()
                .sorted((a, b) -> Double.compare(scores.get(b.chunkId()), scores.get(a.chunkId())))
                .collect(Collectors.toList());
    }
}
