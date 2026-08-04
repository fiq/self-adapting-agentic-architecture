package com.dreamthought.saaa.deterministic;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.EvidenceAuthority;
import com.dreamthought.saaa.domain.EvidenceCapsuleProjection;
import com.dreamthought.saaa.domain.EvidenceDocument;
import com.dreamthought.saaa.domain.EvidenceSubject;
import com.dreamthought.saaa.domain.HistoricalOutcome;
import com.dreamthought.saaa.domain.SourceReference;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class EvidenceCapsuleCompilerTest {
    @Test
    void attachesCurrentHistoricalOutcomesInsteadOfAStaleCachedCopy() {
        var stale = new HistoricalOutcome("old-evaluation", "PROMOTE", 0.9, "old outcome");
        var current = new HistoricalOutcome("new-evaluation", "DISCARD", 0.2, "current outcome");
        var source = new SourceReference("docs/evidence.md", "subject");
        EvidenceCapsuleCache cache = new EvidenceCapsuleCache() {
            @Override
            public Optional<EvidenceCapsuleProjection> find(
                    String logicalSubject, String subjectRevision, String projectionVersion) {
                return Optional.of(new EvidenceCapsuleProjection(
                        new EvidenceSubject("subject", "subject", "KnowledgeEntry"), "revision-1",
                        "capsule-v1", "cached summary", EvidenceAuthority.CANONICAL, "active",
                        List.of(), List.of(stale), List.of(source), 4));
            }

            @Override public void put(EvidenceCapsuleProjection projection) { }
        };
        var document = new EvidenceDocument(
                "subject", "subject", "KnowledgeEntry", "revision-1", "hash-1", "current document",
                EvidenceAuthority.CANONICAL, "active", List.of(source), List.of(current));

        var compilation = new EvidenceCapsuleCompiler(cache, "capsule-v1")
                .compile(document, List.of("exact identifier #1"));

        assertThat(compilation.cacheHit()).isTrue();
        assertThat(compilation.capsule().historicalOutcomes()).containsExactly(current);
        assertThat(compilation.capsule().summary()).isEqualTo("cached summary");
    }
}
