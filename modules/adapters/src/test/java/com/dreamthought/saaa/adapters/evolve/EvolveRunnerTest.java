package com.dreamthought.saaa.adapters.evolve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dreamthought.saaa.deterministic.EvolutionReporter;
import com.dreamthought.saaa.deterministic.AgentHarness;
import com.dreamthought.saaa.domain.AgentRunResult;
import com.dreamthought.saaa.domain.AgentRunStatus;
import com.dreamthought.saaa.domain.AgentUsage;
import com.dreamthought.saaa.domain.BenchmarkEvidence;
import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.MutationScope;
import com.dreamthought.saaa.domain.RetrievalMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class EvolveRunnerTest {
    @Test
    void refusesWorkflowFileSymlinkBeforeReadingBaseline(@TempDir Path dir) throws Exception {
        Files.createDirectory(dir.resolve(".git"));
        Path target = Files.createDirectory(dir.resolve("toy"));
        Path outside = Files.writeString(dir.resolve("secrets.env"), "SAAA_MODEL_API_KEY=sk-super-secret");
        Files.createSymbolicLink(target.resolve("workflow.txt"), outside);

        var request = new EvolveRunRequest(target, "fixture", "workflow.txt", List.of("workflow-check"), 80);

        assertThatThrownBy(() -> new EvolveRunner().run(request, EvolutionReporter.NO_OP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workflowFile")
                .hasMessageContaining("symlink");
    }

    @Test
    void routesPreparedRetrievalThroughAnAgentHarnessBeforeDeterministicEvaluation(@TempDir Path dir)
            throws Exception {
        Path repo = dir.resolve("repo");
        Path target = repo.resolve("toy");
        Files.createDirectories(target.resolve(".saaa"));
        Files.writeString(target.resolve("workflow.txt"), "draft-check: skip\n");
        Files.writeString(target.resolve("workflow-check.sh"), "#!/usr/bin/env bash\ngrep -q 'enforce' \"$(dirname \"$0\")/workflow.txt\"\n");
        target.resolve("workflow-check.sh").toFile().setExecutable(true);
        git(repo, "init", "-b", "main");
        git(repo, "add", ".");
        git(repo, "-c", "user.name=test", "-c", "user.email=test@example.invalid", "commit", "-m", "fixture");

        var observedRequest = new AtomicReference<com.dreamthought.saaa.domain.AgentRequest>();
        AgentHarness harness = request -> {
            observedRequest.set(request);
            return new AgentRunResult(
                    AgentRunStatus.COMPLETED,
                    request.route(),
                    Optional.of(new Mutation("harness-change", "enforce the check", MutationScope.WORKFLOW_DEFINITION,
                            "draft-check: enforce\n")),
                    Optional.of("session-1"), Optional.empty(), AgentUsage.none(), Optional.empty());
        };
        var registry = new ProposerProfileRegistry(
                ignored -> ignoredBaselineProposer(),
                ignored -> harness);

        var result = new EvolveRunner(
                registry,
                java.time.Clock.systemUTC(),
                (mode, root) -> com.dreamthought.saaa.deterministic.EvidenceRetriever.none("test-retrieval"),
                (mode, root) -> com.dreamthought.saaa.deterministic.EvolutionaryMemoryStore.disabled())
                .run(new EvolveRunRequest(target, "acp", "workflow.txt", List.of("workflow-check"), 20),
                        EvolutionReporter.NO_OP);

        assertThat(result.fitnessResult().decision()).isEqualTo(com.dreamthought.saaa.domain.FitnessDecision.PROMOTE);
        assertThat(observedRequest.get()).isNotNull();
        assertThat(observedRequest.get().retrieval()).isPresent();
        assertThat(observedRequest.get().retrieval().orElseThrow().configurationId()).isEqualTo("test-retrieval");
    }

    /**
     * C3: {@code cost_latency_budget} was always {@code 1.0} because {@code EvolveRunner} wired a
     * constant empty benchmark list and an empty budget map, so the objective could never see a
     * benchmark that exceeded its budget. An injected {@link com.dreamthought.saaa.deterministic.BenchmarkRunner}
     * plus a configured budget on the request must reach {@code PhenotypeBridgeScorer} through the
     * real loop, discriminating an over-budget run from an in-budget one exactly the way
     * {@code PhenotypeBridgeScorerTest.scoresCostLatencyFromTheWorstBenchmarkAgainstItsBudget}
     * proves the scorer already can, once it receives evidence.
     */
    @Test
    void discriminatesCandidatesByAConfiguredBenchmarkBudget(@TempDir Path dir) throws Exception {
        Path repo = dir.resolve("repo");
        Path target = repo.resolve("toy");
        Files.createDirectories(target.resolve(".saaa"));
        Files.writeString(target.resolve("workflow.txt"), "publish-policy: allow\ndraft-check: skip\n");
        Files.writeString(target.resolve("workflow-check.sh"),
                "#!/usr/bin/env bash\nset -euo pipefail\n"
                        + "workflow=\"$(dirname \"$0\")/workflow.txt\"\n"
                        + "if grep -q '^draft-check: skip$' \"$workflow\"; then exit 1; fi\n"
                        + "exit 0\n");
        target.resolve("workflow-check.sh").toFile().setExecutable(true);
        Files.writeString(target.resolve(".saaa/fixture-mutation.txt"),
                "enforce the draft check before publishing\npublish-policy: allow\ndraft-check: enforce\n");
        git(repo, "init", "-b", "main");
        git(repo, "add", ".");
        git(repo, "-c", "user.name=test", "-c", "user.email=test@example.invalid", "commit", "-m", "fixture");

        Map<String, Double> budgets = Map.of("publish-latency", 50.0);
        String objectiveKey = "subject.objective.cost_latency_budget";

        var withinBudget = new EvolveRunner(
                candidate -> List.of(BenchmarkEvidence.measurement("publish-latency", 40.0, "ms")))
                .run(new EvolveRunRequest(target, "fixture", "workflow.txt", List.of("workflow-check"), 80,
                                RetrievalMode.NONE, "task", Optional.of("within-budget-run"), budgets),
                        EvolutionReporter.NO_OP);

        var overBudget = new EvolveRunner(
                candidate -> List.of(BenchmarkEvidence.measurement("publish-latency", 100.0, "ms")))
                .run(new EvolveRunRequest(target, "fixture", "workflow.txt", List.of("workflow-check"), 80,
                                RetrievalMode.NONE, "task", Optional.of("over-budget-run"), budgets),
                        EvolutionReporter.NO_OP);

        assertThat(withinBudget.fitnessResult().objectives()).containsEntry(objectiveKey, 1.0);
        assertThat(overBudget.fitnessResult().objectives()).containsEntry(objectiveKey, 0.5);
        assertThat(overBudget.fitnessResult().fitnessScore().rawMagnitude())
                .isLessThan(withinBudget.fitnessResult().fitnessScore().rawMagnitude());
    }

    /**
     * S1 and S6 together, at the seam that produces both. A generation gives each candidate its own
     * namespace by position, and the position has to advance past a candidate that failed: the
     * failing candidate has usually already created its worktree, so handing its position to the
     * next one would send that one at a directory that exists and lose a second candidate to the
     * first one's failure.
     *
     * <p>The first candidate is made to fail at the proposer, which is the cheapest failure that is
     * still real, and the assertion is on the surviving candidates' names rather than on a count,
     * because a count cannot tell the difference between "positions advanced" and "the same position
     * happened to work twice".
     */
    @Test
    void aFailedCandidateStillConsumesItsPositionSoTheNextOneGetsAFreshNamespace(@TempDir Path dir)
            throws Exception {
        Path repo = dir.resolve("repo");
        Path target = repo.resolve("toy");
        Files.createDirectories(target.resolve(".saaa"));
        Files.writeString(target.resolve("workflow.txt"), "draft-check: skip\n");
        Files.writeString(target.resolve("workflow-check.sh"),
                "#!/usr/bin/env bash\ngrep -q 'enforce' \"$(dirname \"$0\")/workflow.txt\"\n");
        target.resolve("workflow-check.sh").toFile().setExecutable(true);
        git(repo, "init", "-b", "main");
        git(repo, "add", ".");
        git(repo, "-c", "user.name=test", "-c", "user.email=test@example.invalid", "commit", "-m", "fixture");

        // Varies per call, because a generation refuses to rank one mutation against itself. The
        // variance is incidental here; what this test is about is which namespace each surviving
        // candidate got.
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var registry = new ProposerProfileRegistry(ignored -> baseline -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                throw new IllegalStateException("the first candidate could not be proposed");
            }
            return new Mutation("enforce-check-" + call, "enforce the draft check",
                    MutationScope.WORKFLOW_DEFINITION, "draft-check: enforce\n" + "# call " + call + "\n");
        });

        var generation = new EvolveRunner(
                registry,
                java.time.Clock.systemUTC(),
                (mode, root) -> com.dreamthought.saaa.deterministic.EvidenceRetriever.none("test-retrieval"),
                (mode, root) -> com.dreamthought.saaa.deterministic.EvolutionaryMemoryStore.disabled())
                .runGeneration(
                        new EvolveRunRequest(target, "openai-compatible", "workflow.txt",
                                List.of("workflow-check"), 20, RetrievalMode.NONE, "vary the check",
                                Optional.of("fixed-run")),
                        EvolutionReporter.NO_OP, 3)
                .generation();

        assertThat(generation.requestedCount()).isEqualTo(3);
        assertThat(generation.evaluatedCount())
                .as("the generation lost only the candidate that failed")
                .isEqualTo(2);
        assertThat(generation.unevaluated())
                .singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.reference()).isEqualTo("attempt-1-of-3");
                    assertThat(candidate.reason()).contains("the first candidate could not be proposed");
                });
        assertThat(generation.ranked().stream().map(result -> result.candidate().id()).toList())
                .as("positions two and three were used, and the failed candidate's was not reused")
                .allSatisfy(id -> assertThat(id).doesNotContain("fixed-run-c1"))
                .anySatisfy(id -> assertThat(id).contains("fixed-run-c2"))
                .anySatisfy(id -> assertThat(id).contains("fixed-run-c3"));
    }

    /**
     * The question this slice exists to answer rather than assume: does ranking discriminate at all?
     * ADR-0002 names "population ships but ranking is not measurably useful" as a revisit trigger,
     * and a generation whose candidates all land on the same score is that trigger firing.
     *
     * <p>Run end to end through the real fixture proposer, the real Git workspace and the real
     * scorer, three candidates of one generation produce three distinct mutations and a spread above
     * zero. The spread comes from parsimony, which reads the size of the realised diff, so it is a
     * real objective discriminating on real evidence rather than a number the test arranged.
     *
     * <p>What it does not show: that a live model's candidates differ in ways the scorer can see. A
     * fixture proposer produces the variety it was written to produce, which is why the live
     * proposer is deliberately a separate change.
     */
    @Test
    void aGenerationFromTheFixtureProposerRanksCandidatesThatActuallyDiffer(@TempDir Path dir)
            throws Exception {
        Path repo = dir.resolve("repo");
        Path target = repo.resolve("toy");
        Files.createDirectories(target.resolve(".saaa"));
        Files.writeString(target.resolve("workflow.txt"), "draft-check: skip\n");
        Files.writeString(target.resolve(".saaa/fixture-mutation.txt"),
                "enforce the draft check\ndraft-check: enforce\n");
        Files.writeString(target.resolve("workflow-check.sh"),
                "#!/usr/bin/env bash\ngrep -q 'enforce' \"$(dirname \"$0\")/workflow.txt\"\n");
        target.resolve("workflow-check.sh").toFile().setExecutable(true);
        git(repo, "init", "-b", "main");
        git(repo, "add", ".");
        git(repo, "-c", "user.name=test", "-c", "user.email=test@example.invalid", "commit", "-m", "fixture");

        var generation = new EvolveRunner(
                new ProposerProfileRegistry(),
                java.time.Clock.systemUTC(),
                (mode, root) -> com.dreamthought.saaa.deterministic.EvidenceRetriever.none("test-retrieval"),
                (mode, root) -> com.dreamthought.saaa.deterministic.EvolutionaryMemoryStore.disabled())
                .runGeneration(
                        new EvolveRunRequest(target, "fixture", "workflow.txt", List.of("workflow-check"),
                                80, RetrievalMode.NONE, "vary the check", Optional.of("spread-run")),
                        EvolutionReporter.NO_OP, 3)
                .generation();

        assertThat(generation.evaluatedCount())
                .as("three distinct mutations, so none was refused as a repeat")
                .isEqualTo(3);
        assertThat(generation.ranked())
                .extracting(result -> result.candidate().mutationId())
                .doesNotHaveDuplicates();
        assertThat(generation.spread())
                .as("ranking discriminated; an equal-score generation is ADR-0002's revisit trigger")
                .get(org.assertj.core.api.InstanceOfAssertFactories.BIG_DECIMAL)
                .isGreaterThan(java.math.BigDecimal.ZERO);
        assertThat(generation.scoringFingerprint())
                .as("every candidate was measured under one scoring context, which is what makes "
                        + "them comparable at all")
                .isPresent();
    }

    /**
     * S1 and S5, at the seam independent review found broken. Retrieval is not a pure read: every
     * candidate projects its outcome into durable evolutionary memory when its evaluation finishes,
     * and for every retrieval mode except NONE the next retrieval reads that memory back —
     * {@code HybridEvidenceRetriever} gives documents carrying historical outcomes a ranking bonus.
     * Retrieving per candidate therefore let candidate one change the evidence candidate two was
     * proposed from, so the two were not comparable and the ranking was partly measuring the order
     * they happened to run in.
     *
     * <p>No existing test caught it, because every generation test runs with retrieval NONE, where
     * the retriever is inert and returns the same empty bundle however many times it is asked. This
     * one counts calls to a retriever that answers differently each time, which is what a live
     * GRAPH or HYBRID retriever does once memory has been written to.
     */
    @Test
    void aGenerationRetrievesOnceSoEveryCandidateIsProposedFromTheSameEvidence(@TempDir Path dir)
            throws Exception {
        Path repo = dir.resolve("repo");
        Path target = repo.resolve("toy");
        Files.createDirectories(target.resolve(".saaa"));
        Files.writeString(target.resolve("workflow.txt"), "draft-check: skip\n");
        Files.writeString(target.resolve("workflow-check.sh"),
                "#!/usr/bin/env bash\ngrep -q 'enforce' \"$(dirname \"$0\")/workflow.txt\"\n");
        target.resolve("workflow-check.sh").toFile().setExecutable(true);
        git(repo, "init", "-b", "main");
        git(repo, "add", ".");
        git(repo, "-c", "user.name=test", "-c", "user.email=test@example.invalid", "commit", "-m", "fixture");

        // Answers differently on every call, the way a retriever does once a candidate's outcome has
        // been projected into memory. If the generation retrieves per candidate, the proposers see
        // different context and the count is 3.
        var retrievals = new java.util.concurrent.atomic.AtomicInteger();
        var contextsSeen = new java.util.LinkedHashSet<String>();
        com.dreamthought.saaa.deterministic.EvidenceRetriever drifting = query ->
                new com.dreamthought.saaa.domain.RetrievalBundle(
                        com.dreamthought.saaa.domain.RetrievalMode.NONE, "drifting-retrieval",
                        query.repositoryRevision(), "none", "none", "none", "none", "none",
                        List.of(), com.dreamthought.saaa.domain.RetrievalDiagnostics.empty(),
                        "call-" + retrievals.incrementAndGet());

        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var registry = new ProposerProfileRegistry(ignored -> new com.dreamthought.saaa.deterministic.MutationProposer() {
            @Override
            public Mutation proposeFor(com.dreamthought.saaa.domain.WorkflowGraph baseline) {
                throw new AssertionError("the loop must propose from a prepared request");
            }

            @Override
            public Mutation proposeFor(
                    com.dreamthought.saaa.domain.PreparedMutationProposalRequest request) {
                contextsSeen.add(request.retrieval().flattenedContext());
                int call = calls.incrementAndGet();
                return new Mutation("enforce-" + call, "enforce the draft check",
                        MutationScope.WORKFLOW_DEFINITION,
                        "draft-check: enforce\n" + "# call " + call + "\n");
            }
        });

        var generation = new EvolveRunner(
                registry,
                java.time.Clock.systemUTC(),
                (mode, root) -> drifting,
                (mode, root) -> com.dreamthought.saaa.deterministic.EvolutionaryMemoryStore.disabled())
                .runGeneration(
                        new EvolveRunRequest(target, "openai-compatible", "workflow.txt",
                                List.of("workflow-check"), 20, RetrievalMode.NONE, "vary the check",
                                Optional.of("shared-evidence-run")),
                        EvolutionReporter.NO_OP, 3)
                .generation();

        assertThat(generation.evaluatedCount()).isEqualTo(3);
        assertThat(retrievals.get())
                .as("one retrieval for the generation, not one per candidate")
                .isEqualTo(1);
        assertThat(contextsSeen)
                .as("every candidate was proposed from the same evidence, which is what makes their "
                        + "scores comparable at all")
                .hasSize(1);
    }

    private static com.dreamthought.saaa.deterministic.MutationProposer ignoredBaselineProposer() {
        return baseline -> new Mutation("unused", "unused", MutationScope.WORKFLOW_DEFINITION, baseline.definition());
    }

    private static void git(Path directory, String... arguments) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add("git");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).directory(directory.toFile()).inheritIO().start();
        if (process.waitFor() != 0) {
            throw new IllegalStateException("git command failed: " + command);
        }
    }
}
