package com.dreamthought.saaa.domain;

import java.util.Objects;
import java.util.Set;

public record RetrievalConfig(
        String id,
        int graphDepth,
        int maxFanOut,
        int maxEvidence,
        int maxContextTokens,
        int reciprocalRankConstant,
        double historicalWeightCap,
        Set<RelationshipType> allowedRelationships,
        String graphSchemaVersion,
        String capsuleProjectionVersion,
        String rankingVersion,
        String embeddingModelId,
        String memoryPolicyId
) {
    public RetrievalConfig {
        id = Require.nonBlank(id, "id");
        graphSchemaVersion = Require.nonBlank(graphSchemaVersion, "graphSchemaVersion");
        capsuleProjectionVersion = Require.nonBlank(capsuleProjectionVersion, "capsuleProjectionVersion");
        rankingVersion = Require.nonBlank(rankingVersion, "rankingVersion");
        embeddingModelId = Require.nonBlank(embeddingModelId, "embeddingModelId");
        memoryPolicyId = Require.nonBlank(memoryPolicyId, "memoryPolicyId");
        allowedRelationships = Set.copyOf(Objects.requireNonNull(allowedRelationships, "allowedRelationships"));
        if (graphDepth < 1 || graphDepth > 2 || maxFanOut < 1 || maxEvidence < 1
                || maxContextTokens < 1 || reciprocalRankConstant < 1) {
            throw new IllegalArgumentException("retrieval bounds must be positive and graphDepth must be 1-2");
        }
        if (!Double.isFinite(historicalWeightCap) || historicalWeightCap < 0.0 || historicalWeightCap > 0.25) {
            throw new IllegalArgumentException("historicalWeightCap must be between 0 and 0.25");
        }
    }

    public static RetrievalConfig defaults() {
        return new RetrievalConfig(
                "retrieval-config-v1",
                1,
                12,
                8,
                1200,
                60,
                0.10,
                Set.of(RelationshipType.DEPENDS_ON, RelationshipType.TESTS, RelationshipType.VERIFIES,
                        RelationshipType.GOVERNS, RelationshipType.RELATES_TO),
                "graph-schema-v1",
                "capsule-v1",
                "rrf-v1",
                "unconfigured",
                "lineage-novelty-v1");
    }

    public RetrievalConfig withEmbeddingModelId(String value) {
        return new RetrievalConfig(id, graphDepth, maxFanOut, maxEvidence, maxContextTokens,
                reciprocalRankConstant, historicalWeightCap, allowedRelationships, graphSchemaVersion,
                capsuleProjectionVersion, rankingVersion, value, memoryPolicyId);
    }

    public RetrievalConfig withMemoryPolicyId(String value) {
        return new RetrievalConfig(id, graphDepth, maxFanOut, maxEvidence, maxContextTokens,
                reciprocalRankConstant, historicalWeightCap, allowedRelationships, graphSchemaVersion,
                capsuleProjectionVersion, rankingVersion, embeddingModelId, value);
    }
}
