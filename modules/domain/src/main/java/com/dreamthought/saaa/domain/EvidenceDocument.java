package com.dreamthought.saaa.domain;

import java.util.List;
import java.util.Objects;

/** One meaningful semantic/indexing unit, never an arbitrary fixed token chunk. */
public record EvidenceDocument(
        String stableId,
        String logicalId,
        String kind,
        String revision,
        String contentHash,
        String semanticText,
        EvidenceAuthority authority,
        String status,
        List<SourceReference> sources,
        List<EvidenceLink> links,
        List<HistoricalOutcome> historicalOutcomes
) {
    public EvidenceDocument(
            String stableId, String logicalId, String kind, String revision, String contentHash,
            String semanticText, EvidenceAuthority authority, String status,
            List<SourceReference> sources, List<HistoricalOutcome> historicalOutcomes) {
        this(stableId, logicalId, kind, revision, contentHash, semanticText, authority, status,
                sources, List.of(), historicalOutcomes);
    }

    public EvidenceDocument {
        stableId = Require.nonBlank(stableId, "stableId");
        logicalId = Require.nonBlank(logicalId, "logicalId");
        kind = Require.nonBlank(kind, "kind");
        revision = Require.nonBlank(revision, "revision");
        contentHash = Require.nonBlank(contentHash, "contentHash");
        semanticText = Require.nonBlank(semanticText, "semanticText");
        authority = Objects.requireNonNull(authority, "authority");
        status = Require.nonBlank(status, "status");
        sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        links = List.copyOf(Objects.requireNonNull(links, "links"));
        historicalOutcomes = List.copyOf(Objects.requireNonNull(historicalOutcomes, "historicalOutcomes"));
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("at least one source is required");
        }
    }

    public EvidenceSubject subject() {
        return new EvidenceSubject(stableId, logicalId, kind);
    }
}
