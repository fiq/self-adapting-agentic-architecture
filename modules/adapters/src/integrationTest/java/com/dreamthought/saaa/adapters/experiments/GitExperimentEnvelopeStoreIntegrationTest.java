package com.dreamthought.saaa.adapters.experiments;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.BenchmarkEvidence;
import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.CheckStatus;
import com.dreamthought.saaa.domain.EvolutionContext;
import com.dreamthought.saaa.domain.EvolutionaryMemoryRecord;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.FitnessScore;
import com.dreamthought.saaa.domain.MutationScope;
import com.dreamthought.saaa.domain.RetrievalMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GitExperimentEnvelopeStoreIntegrationTest {
    @TempDir Path repository;

    @Test
    void roundTripsReviewableEnvelopeAndGeneratesANonAuthoritativeWikiProjection() throws Exception {
        var record = new EvolutionaryMemoryRecord(
                new EvolutionContext("subject", "base-1", "saaa", "process-1"),
                "lineage-novelty-v1", "mutation-1", "bounded, quoted \"change\"",
                MutationScope.WORKFLOW_DEFINITION, "candidate-1", "commit-1", RetrievalMode.HYBRID,
                "retrieval-config-v1", List.of("src/example/Loop.java"),
                List.of("ARCH-001", "type:example.Loop"),
                List.of(new CheckEvidence("tests", CheckStatus.FAILED, "one, useful failure")),
                List.of(new BenchmarkEvidence("latency", 3.0, "ms")),
                FitnessScore.of(0.3, FitnessDecision.DISCARD), Instant.parse("2026-08-02T00:00:00Z"));
        var store = new GitExperimentEnvelopeStore(repository);

        store.append(record);
        store.append(record);
        new WikiExperimentProjection(repository).render(store.records());

        assertThat(store.records()).containsExactly(record);
        assertThat(Files.readString(repository.resolve("experiments/ledger")
                        .resolve(GitExperimentEnvelopeStore.fileName("candidate-1"))))
                .contains("schema_version: \"saaa-experiment-envelope-v3\"")
                .contains("process_repository_revision: \"process-1\"")
                .contains("changed_paths[1]")
                .doesNotContain("prompt");
        assertThat(Files.readString(repository.resolve("docs/wiki/experiments.md")))
                .contains("human projection, not")
                .contains("retrieval-config-v1 / lineage-novelty-v1")
                .contains("src/example/Loop.java")
                .contains("candidate-1");
    }
}
