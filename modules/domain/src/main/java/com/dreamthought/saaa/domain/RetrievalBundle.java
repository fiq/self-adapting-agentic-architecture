package com.dreamthought.saaa.domain;

import java.util.List;
import java.util.Objects;

public record RetrievalBundle(
        RetrievalMode mode,
        String configurationId,
        String repositoryRevision,
        String graphSchemaVersion,
        String capsuleProjectionVersion,
        String rankingVersion,
        String embeddingModelId,
        String memoryPolicyId,
        List<EvidenceCapsule> capsules,
        RetrievalDiagnostics diagnostics,
        String flattenedContext
) {
    public RetrievalBundle {
        mode = Objects.requireNonNull(mode, "mode");
        configurationId = Require.nonBlank(configurationId, "configurationId");
        repositoryRevision = Require.nonBlank(repositoryRevision, "repositoryRevision");
        graphSchemaVersion = Require.nonBlank(graphSchemaVersion, "graphSchemaVersion");
        capsuleProjectionVersion = Require.nonBlank(capsuleProjectionVersion, "capsuleProjectionVersion");
        rankingVersion = Require.nonBlank(rankingVersion, "rankingVersion");
        embeddingModelId = Require.nonBlank(embeddingModelId, "embeddingModelId");
        memoryPolicyId = Require.nonBlank(memoryPolicyId, "memoryPolicyId");
        capsules = List.copyOf(Objects.requireNonNull(capsules, "capsules"));
        diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        flattenedContext = Objects.requireNonNull(flattenedContext, "flattenedContext");
    }

    public int estimatedTokens() {
        return capsules.stream().mapToInt(EvidenceCapsule::estimatedTokens).sum();
    }
}
