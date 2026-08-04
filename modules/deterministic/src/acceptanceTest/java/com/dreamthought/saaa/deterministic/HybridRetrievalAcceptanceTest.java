package com.dreamthought.saaa.deterministic;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.CheckStatus;
import com.dreamthought.saaa.domain.EvidenceAuthority;
import com.dreamthought.saaa.domain.EvidenceDocument;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.MutationProposalRequest;
import com.dreamthought.saaa.domain.MutationScope;
import com.dreamthought.saaa.domain.RelationshipType;
import com.dreamthought.saaa.domain.RetrievalConfig;
import com.dreamthought.saaa.domain.RetrievalMode;
import com.dreamthought.saaa.domain.RetrievalQuery;
import com.dreamthought.saaa.domain.SourceReference;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class HybridRetrievalAcceptanceTest {
    private static final Instant NOW = Instant.parse("2026-08-02T00:00:00Z");

    @Test
    void suppliesSemanticAndStructurallyConnectedEvidenceWithinBudgetToTheProposer() {
        var implementation = evidence(
                "type:MutationEvaluationLoop",
                "Type",
                "Coordinates mutation evaluation through deterministic ports.",
                "modules/deterministic/src/main/java/example/MutationEvaluationLoop.java");
        var connectedConstraint = evidence(
                "ARCH-001",
                "ArchitectureDecision",
                "Models may propose but deterministic fitness decides survival.",
                ".agents/knowledge/architecture/ARCH-001-deterministic-model-boundary.md");
        var connectedTest = evidence(
                "test:MutationEvaluationLoopAcceptanceTest",
                "Test",
                "Proves the evaluation loop keeps promotion deterministic.",
                "modules/deterministic/src/acceptanceTest/java/example/MutationEvaluationLoopAcceptanceTest.java");
        var unrelated = evidence(
                "Q-009",
                "Question",
                "Whether a future HTTP runtime should use Quarkus.",
                ".agents/knowledge/questions/Q-009-quarkus-runtime-shell.md");

        EvidenceSearch search = new EvidenceSearch() {
            @Override
            public List<EvidenceDocument> resolveExact(List<String> identifiers) {
                return List.of(connectedConstraint);
            }

            @Override
            public List<EvidenceDocument> vectorSearch(String semanticQuery, int limit) {
                return List.of(implementation, unrelated);
            }

            @Override
            public List<EvidenceDocument> expand(
                    List<String> seedIds,
                    Set<RelationshipType> relationships,
                    int depth,
                    int maxFanOut
            ) {
                assertThat(depth).isEqualTo(1);
                assertThat(maxFanOut).isEqualTo(4);
                assertThat(relationships).containsExactlyInAnyOrder(RelationshipType.TESTS, RelationshipType.GOVERNS);
                return List.of(connectedTest, connectedConstraint);
            }
        };

        RetrievalConfig config = new RetrievalConfig(
                "retrieval-config-v1",
                1,
                4,
                3,
                180,
                60,
                0.10,
                Set.of(RelationshipType.TESTS, RelationshipType.GOVERNS),
                "graph-schema-v1",
                "capsule-v1",
                "rrf-v1",
                "fake-embedding-v1",
                "lineage-novelty-v1");
        EvidenceRetriever retriever = new HybridEvidenceRetriever(search, config);

        var baseline = new WorkflowGraph("MutationEvaluationLoop", "abc123", "class MutationEvaluationLoop {}");
        var query = new RetrievalQuery(
                RetrievalMode.HYBRID,
                "Preserve deterministic promotion while improving mutation proposal context",
                baseline,
                "abc123",
                List.of("ARCH-001", "type:MutationEvaluationLoop"),
                Optional.empty());
        var recordingProposer = new RecordingProposer();
        var scorer = new RecordingScorer();
        var loop = new MutationEvaluationLoop(
                recordingProposer,
                (workflow, mutation) -> com.dreamthought.saaa.domain.ValidationResult.passed(),
                (workflow, mutation) -> candidate(),
                candidate -> List.of(new CheckEvidence("tests", CheckStatus.PASSED, "passed")),
                candidate -> List.of(),
                scorer,
                new NoOpMetadata(),
                new NoOpDecisionSink(),
                EvolutionReporter.NO_OP,
                Clock.fixed(NOW, ZoneOffset.UTC),
                retriever);

        FitnessResult result = loop.evaluate(new MutationProposalRequest(baseline, query));

        assertThat(recordingProposer.request).isNotNull();
        assertThat(recordingProposer.request.retrieval().mode()).isEqualTo(RetrievalMode.HYBRID);
        assertThat(recordingProposer.request.retrieval().capsules())
                .extracting(capsule -> capsule.subject().stableId())
                .containsExactlyInAnyOrder(
                        "ARCH-001",
                        "type:MutationEvaluationLoop",
                        "test:MutationEvaluationLoopAcceptanceTest")
                .doesNotContain("Q-009");
        assertThat(recordingProposer.request.retrieval().estimatedTokens()).isLessThanOrEqualTo(180);
        assertThat(recordingProposer.request.retrieval().capsules())
                .allSatisfy(capsule -> {
                    assertThat(capsule.selectionReasons()).isNotEmpty();
                    assertThat(capsule.sources()).isNotEmpty();
                    assertThat(capsule.authority()).isNotNull();
                });
        assertThat(scorer.seenCandidate).isEqualTo(candidate());
        assertThat(result.decision()).isEqualTo(FitnessDecision.DISCARD);
    }

    @Test
    void noneSuppliesAnEmptyBundleWithoutGraphAccess() {
        var baseline = new WorkflowGraph("workflow", "abc123", "definition");
        var query = new RetrievalQuery(
                RetrievalMode.NONE,
                "keep existing behaviour",
                baseline,
                "abc123",
                List.of(),
                Optional.empty());
        var proposer = new RecordingProposer();
        var loop = new MutationEvaluationLoop(
                proposer,
                (workflow, mutation) -> com.dreamthought.saaa.domain.ValidationResult.passed(),
                (workflow, mutation) -> candidate(),
                candidate -> List.of(new CheckEvidence("tests", CheckStatus.PASSED, "passed")),
                candidate -> List.of(),
                new RecordingScorer(),
                new NoOpMetadata(),
                new NoOpDecisionSink(),
                EvolutionReporter.NO_OP,
                Clock.fixed(NOW, ZoneOffset.UTC),
                EvidenceRetriever.none("retrieval-config-v1"));

        loop.evaluate(new MutationProposalRequest(baseline, query));

        assertThat(proposer.request.retrieval().mode()).isEqualTo(RetrievalMode.NONE);
        assertThat(proposer.request.retrieval().capsules()).isEmpty();
    }

    private static EvidenceDocument evidence(String id, String kind, String summary, String path) {
        return new EvidenceDocument(
                id,
                id,
                kind,
                "abc123",
                "hash-" + id,
                summary,
                EvidenceAuthority.CANONICAL,
                "active",
                List.of(new SourceReference(path, id)),
                List.of());
    }

    private static Candidate candidate() {
        return new Candidate("candidate-1", "mutation-1", "candidate/workflow-1", Path.of("/tmp/candidate-1"), "def456");
    }

    private static final class RecordingProposer implements MutationProposer {
        private com.dreamthought.saaa.domain.PreparedMutationProposalRequest request;

        @Override
        public Mutation proposeFor(WorkflowGraph baseline) {
            throw new AssertionError("the retrieval-aware proposal seam must be used");
        }

        @Override
        public Mutation proposeFor(com.dreamthought.saaa.domain.PreparedMutationProposalRequest request) {
            this.request = request;
            return new Mutation("mutation-1", "bounded change", MutationScope.WORKFLOW_DEFINITION, "replacement");
        }
    }

    private static final class RecordingScorer implements FitnessScorer {
        private Candidate seenCandidate;

        @Override
        public FitnessResult score(Candidate candidate, com.dreamthought.saaa.domain.EvaluationEvidence evidence) {
            seenCandidate = candidate;
            return new FitnessResult(candidate, evidence, Map.of("score", 0.1), 0.1, FitnessDecision.DISCARD);
        }
    }

    private static final class NoOpMetadata implements ExperimentMetadataStore {
        @Override public void recordCandidate(Candidate candidate) { }
        @Override public void recordFitness(FitnessResult result) { }
    }

    private static final class NoOpDecisionSink implements CandidateDecisionSink {
        @Override public void recordPromotedCandidateBranch(com.dreamthought.saaa.domain.CandidateBranchRef ref, FitnessResult result) { }
        @Override public void recordDiscardedCandidateBranch(com.dreamthought.saaa.domain.CandidateBranchRef ref, FitnessResult result) { }
    }
}
