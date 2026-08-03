package com.dreamthought.saaa.domain;

import java.util.List;
import java.util.Objects;

public record RetrievalDiagnostics(
        int exactCandidates,
        int vectorCandidates,
        int graphNodesConsidered,
        int deduplicatedCandidates,
        int cacheHits,
        int cacheMisses,
        double historicalWeightCap,
        List<String> consideredEvidenceIds
) {
    public RetrievalDiagnostics {
        consideredEvidenceIds = List.copyOf(Objects.requireNonNull(consideredEvidenceIds, "consideredEvidenceIds"));
    }

    public static RetrievalDiagnostics empty() {
        return new RetrievalDiagnostics(0, 0, 0, 0, 0, 0, 0.0, List.of());
    }
}
