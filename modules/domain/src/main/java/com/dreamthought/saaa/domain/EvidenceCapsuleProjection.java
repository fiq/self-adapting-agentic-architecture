package com.dreamthought.saaa.domain;

import java.util.List;
import java.util.Objects;

/** Query-independent materialisation of a capsule; selection reasons are attached per retrieval. */
public record EvidenceCapsuleProjection(
        EvidenceSubject subject,
        String revision,
        String projectionVersion,
        String summary,
        EvidenceAuthority authority,
        String status,
        List<EvidenceLink> links,
        List<HistoricalOutcome> historicalOutcomes,
        List<SourceReference> sources,
        int estimatedTokens
) {
    public EvidenceCapsuleProjection {
        subject = Objects.requireNonNull(subject, "subject");
        revision = Require.nonBlank(revision, "revision");
        projectionVersion = Require.nonBlank(projectionVersion, "projectionVersion");
        summary = Require.nonBlank(summary, "summary");
        authority = Objects.requireNonNull(authority, "authority");
        status = Require.nonBlank(status, "status");
        links = List.copyOf(Objects.requireNonNull(links, "links"));
        historicalOutcomes = List.copyOf(Objects.requireNonNull(historicalOutcomes, "historicalOutcomes"));
        sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        if (sources.isEmpty() || estimatedTokens < 1) {
            throw new IllegalArgumentException("capsule projections require sources and a positive token estimate");
        }
    }
}
