package com.dreamthought.saaa.adapters.sqlite;

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
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SqliteEvolutionaryMemoryStoreIntegrationTest {
    @TempDir Path temporaryDirectory;

    @Test
    void roundTripsObservableMemoryInTheExperimentLedger() {
        var store = new SqliteEvolutionaryMemoryStore(temporaryDirectory.resolve("experiments.sqlite"));
        var memory = new EvolutionaryMemoryRecord(
                new EvolutionContext("subject", "base-1", "saaa", "process-1"),
                "lineage-novelty-v1", "mutation-1", "bounded change",
                MutationScope.WORKFLOW_DEFINITION, "candidate-1", "commit-1", RetrievalMode.HYBRID,
                "retrieval-config-v1", List.of("src/example/Loop.java"), List.of("ARCH-001"),
                List.of(new CheckEvidence("tests", CheckStatus.FAILED, "failed")),
                List.of(new BenchmarkEvidence("latency", 3.0, "ms")),
                FitnessScore.of(0.5949, FitnessDecision.DISCARD), Instant.parse("2026-08-02T00:00:00Z"));

        store.append(memory);
        store.append(memory);

        assertThat(store.records()).containsExactly(memory);
    }
}
