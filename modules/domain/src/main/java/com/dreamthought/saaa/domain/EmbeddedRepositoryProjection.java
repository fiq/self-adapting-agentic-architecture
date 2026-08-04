package com.dreamthought.saaa.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A completely prepared vector projection, safe to publish atomically after embedding succeeds. */
public record EmbeddedRepositoryProjection(
        RepositoryProjection repositoryProjection,
        String embeddingModelId,
        int dimensions,
        Map<String, List<Float>> embeddingsByStableId
) {
    public EmbeddedRepositoryProjection {
        repositoryProjection = Objects.requireNonNull(repositoryProjection, "repositoryProjection");
        embeddingModelId = Require.nonBlank(embeddingModelId, "embeddingModelId");
        if (dimensions < 1) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        var copy = new LinkedHashMap<String, List<Float>>();
        Objects.requireNonNull(embeddingsByStableId, "embeddingsByStableId").forEach((id, vector) -> {
            List<Float> values = List.copyOf(vector);
            if (values.size() != dimensions || values.stream().anyMatch(value -> value == null || !Float.isFinite(value))) {
                throw new IllegalArgumentException("embedding has wrong dimensions or non-finite values: " + id);
            }
            copy.put(Require.nonBlank(id, "embedding stable id"), values);
        });
        if (!copy.keySet().equals(repositoryProjection.nodes().stream()
                .map(EvidenceDocument::stableId).collect(java.util.stream.Collectors.toSet()))) {
            throw new IllegalArgumentException("every projected evidence document requires exactly one embedding");
        }
        embeddingsByStableId = Map.copyOf(copy);
    }
}
