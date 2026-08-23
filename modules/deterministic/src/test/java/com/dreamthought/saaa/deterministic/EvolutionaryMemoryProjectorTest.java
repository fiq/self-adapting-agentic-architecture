package com.dreamthought.saaa.deterministic;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.BenchmarkEvidence;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.CheckStatus;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.EvolutionaryMemoryRecord;
import com.dreamthought.saaa.domain.EvolutionContext;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.FitnessScore;
import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.MutationScope;
import com.dreamthought.saaa.domain.RetrievalBundle;
import com.dreamthought.saaa.domain.RetrievalDiagnostics;
import com.dreamthought.saaa.domain.RetrievalMode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class EvolutionaryMemoryProjectorTest {
    @Test
    void projectsOnlyObservableOutcomeEvidenceWithBoundedHistoricalWeight() {
        var store = new RecordingStore();
        var projector = new EvolutionaryMemoryProjector(store, "lineage-novelty-v1",
                new EvolutionContext("subject", "base123", "saaa", "process123"),
                ignored -> List.of("src/Workflow.java"));
        var candidate = new Candidate("candidate-1", "mutation-1", "candidate/1", Path.of("/tmp/candidate"), "abc123");
        var evidence = new EvaluationEvidence(
                List.of(new CheckEvidence("tests", CheckStatus.FAILED, "one assertion failed")),
                List.of(new BenchmarkEvidence("latency", 12.0, "ms")),
                Instant.parse("2026-08-02T00:00:00Z"));
        var result = new FitnessResult(candidate, evidence, Map.of("quality", 0.4),
                FitnessScore.of(0.4, FitnessDecision.DISCARD));
        var retrieval = new RetrievalBundle(
                RetrievalMode.HYBRID, "retrieval-config-v1", "base123", "graph-schema-v1",
                "capsule-v1", "rrf-v1", "fixture-model", "lineage-novelty-v1", List.of(),
                new RetrievalDiagnostics(1, 2, 3, 3, 1, 2, 0.10, List.of("ARCH-001")), "");

        projector.project(new Mutation("mutation-1", "bounded change", MutationScope.WORKFLOW_DEFINITION, "patch"),
                retrieval, result);

        assertThat(store.record.mutationId()).isEqualTo("mutation-1");
        assertThat(store.record.candidateCommit()).isEqualTo("abc123");
        assertThat(store.record.evolutionContext().processRepositoryRevision()).isEqualTo("process123");
        assertThat(store.record.memoryPolicyId()).isEqualTo("lineage-novelty-v1");
        assertThat(store.record.changedPaths()).containsExactly("src/Workflow.java");
        assertThat(store.record.checks()).containsExactlyElementsOf(evidence.checks());
        assertThat(store.record.fitnessScore().rawMagnitude()).isEqualByComparingTo("0.4");
        assertThat(retrieval.diagnostics().historicalWeightCap()).isEqualTo(0.10);
    }

    private static final class RecordingStore implements EvolutionaryMemoryStore {
        EvolutionaryMemoryRecord record;
        public void append(EvolutionaryMemoryRecord value) { record = value; }
    }
}
