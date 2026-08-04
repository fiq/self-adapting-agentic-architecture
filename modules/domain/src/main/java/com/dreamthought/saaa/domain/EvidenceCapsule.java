package com.dreamthought.saaa.domain;

import java.util.List;
import java.util.Objects;

/** A deterministic, bounded context projection compiled from graph evidence. */
public record EvidenceCapsule(
        EvidenceSubject subject,
        String revision,
        String projectionVersion,
        String summary,
        EvidenceAuthority authority,
        String status,
        List<EvidenceLink> links,
        List<HistoricalOutcome> historicalOutcomes,
        List<SourceReference> sources,
        List<String> selectionReasons,
        int estimatedTokens
) {
    public EvidenceCapsule {
        subject = Objects.requireNonNull(subject, "subject");
        revision = Require.nonBlank(revision, "revision");
        projectionVersion = Require.nonBlank(projectionVersion, "projectionVersion");
        summary = Require.nonBlank(summary, "summary");
        authority = Objects.requireNonNull(authority, "authority");
        status = Require.nonBlank(status, "status");
        links = List.copyOf(Objects.requireNonNull(links, "links"));
        historicalOutcomes = List.copyOf(Objects.requireNonNull(historicalOutcomes, "historicalOutcomes"));
        sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        selectionReasons = List.copyOf(Objects.requireNonNull(selectionReasons, "selectionReasons"));
        if (sources.isEmpty() || selectionReasons.isEmpty()) {
            throw new IllegalArgumentException("capsules require sources and selection reasons");
        }
        if (estimatedTokens < 1) {
            throw new IllegalArgumentException("estimatedTokens must be positive");
        }
    }
}
