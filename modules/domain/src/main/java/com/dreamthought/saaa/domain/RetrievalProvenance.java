package com.dreamthought.saaa.domain;

import java.util.List;
import java.util.Objects;

public record RetrievalProvenance(
        RetrievalMode mode,
        String configurationId,
        String repositoryRevision,
        String graphSchemaVersion,
        String capsuleProjectionVersion,
        String rankingVersion,
        String embeddingModelId,
        String memoryPolicyId,
        List<String> evidenceIds,
        String flattenedContext,
        RetrievalDiagnostics diagnostics
) {
    public RetrievalProvenance {
        mode = Objects.requireNonNull(mode, "mode");
        configurationId = Require.nonBlank(configurationId, "configurationId");
        repositoryRevision = Require.nonBlank(repositoryRevision, "repositoryRevision");
        graphSchemaVersion = Require.nonBlank(graphSchemaVersion, "graphSchemaVersion");
        capsuleProjectionVersion = Require.nonBlank(capsuleProjectionVersion, "capsuleProjectionVersion");
        rankingVersion = Require.nonBlank(rankingVersion, "rankingVersion");
        embeddingModelId = Require.nonBlank(embeddingModelId, "embeddingModelId");
        memoryPolicyId = Require.nonBlank(memoryPolicyId, "memoryPolicyId");
        evidenceIds = List.copyOf(Objects.requireNonNull(evidenceIds, "evidenceIds"));
        flattenedContext = Objects.requireNonNull(flattenedContext, "flattenedContext");
        diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    public static RetrievalProvenance from(RetrievalBundle bundle) {
        return new RetrievalProvenance(
                bundle.mode(),
                bundle.configurationId(),
                bundle.repositoryRevision(),
                bundle.graphSchemaVersion(),
                bundle.capsuleProjectionVersion(),
                bundle.rankingVersion(),
                bundle.embeddingModelId(),
                bundle.memoryPolicyId(),
                bundle.capsules().stream().map(capsule -> capsule.subject().stableId()).toList(),
                bundle.flattenedContext(),
                bundle.diagnostics());
    }
}
