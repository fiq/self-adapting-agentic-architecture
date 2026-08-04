package com.dreamthought.saaa.deterministic;

import java.util.List;
import java.util.Optional;

/** Memoises embeddings only by model identity and exact semantic-content hash. */
public interface EmbeddingCache {
    Optional<List<Float>> find(String modelId, String contentHash, int dimensions);

    void put(String modelId, String contentHash, List<Float> embedding);
}
