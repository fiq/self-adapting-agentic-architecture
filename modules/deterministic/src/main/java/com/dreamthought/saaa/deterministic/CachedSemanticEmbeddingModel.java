package com.dreamthought.saaa.deterministic;

import java.util.List;
import java.util.Objects;

public final class CachedSemanticEmbeddingModel {
    private final SemanticEmbeddingModel model;
    private final EmbeddingCache cache;

    public CachedSemanticEmbeddingModel(SemanticEmbeddingModel model, EmbeddingCache cache) {
        this.model = Objects.requireNonNull(model, "model");
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    public EmbeddingResult embed(String contentHash, String semanticText) {
        var cached = cache.find(model.modelId(), contentHash, model.dimensions());
        if (cached.isPresent()) {
            return new EmbeddingResult(cached.get(), true);
        }
        List<Float> embedding = List.copyOf(model.embed(semanticText));
        validate(embedding);
        cache.put(model.modelId(), contentHash, embedding);
        return new EmbeddingResult(embedding, false);
    }

    public String modelId() {
        return model.modelId();
    }

    public int dimensions() {
        return model.dimensions();
    }

    private void validate(List<Float> embedding) {
        if (embedding.size() != model.dimensions()
                || embedding.stream().anyMatch(value -> value == null || !Float.isFinite(value))) {
            throw new IllegalStateException("embedding provider returned invalid dimensions or values");
        }
    }

    public record EmbeddingResult(List<Float> vector, boolean cacheHit) {
        public EmbeddingResult {
            vector = List.copyOf(vector);
        }
    }
}
