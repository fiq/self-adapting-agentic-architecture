package io.github.selfadaptingagenticarchitecture.application;

import io.github.selfadaptingagenticarchitecture.core.Candidate;
import io.github.selfadaptingagenticarchitecture.core.EvaluationEvidence;
import io.github.selfadaptingagenticarchitecture.core.FitnessResult;
import io.github.selfadaptingagenticarchitecture.core.Mutation;
import io.github.selfadaptingagenticarchitecture.core.ValidationResult;
import io.github.selfadaptingagenticarchitecture.core.WorkflowGraph;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class MutationEvaluationLoop {
    private final MutationProposer mutationProposer;
    private final MutationValidator mutationValidator;
    private final CandidateWorkspace candidateWorkspace;
    private final CheckRunner checkRunner;
    private final BenchmarkRunner benchmarkRunner;
    private final FitnessScorer fitnessScorer;
    private final ExperimentMetadataStore metadataStore;
    private final CandidateDecisionSink decisionSink;
    private final Clock clock;

    public MutationEvaluationLoop(
            MutationProposer mutationProposer,
            MutationValidator mutationValidator,
            CandidateWorkspace candidateWorkspace,
            CheckRunner checkRunner,
            BenchmarkRunner benchmarkRunner,
            FitnessScorer fitnessScorer,
            ExperimentMetadataStore metadataStore,
            CandidateDecisionSink decisionSink
    ) {
        this(
                mutationProposer,
                mutationValidator,
                candidateWorkspace,
                checkRunner,
                benchmarkRunner,
                fitnessScorer,
                metadataStore,
                decisionSink,
                Clock.systemUTC()
        );
    }

    public MutationEvaluationLoop(
            MutationProposer mutationProposer,
            MutationValidator mutationValidator,
            CandidateWorkspace candidateWorkspace,
            CheckRunner checkRunner,
            BenchmarkRunner benchmarkRunner,
            FitnessScorer fitnessScorer,
            ExperimentMetadataStore metadataStore,
            CandidateDecisionSink decisionSink,
            Clock clock
    ) {
        this.mutationProposer = Objects.requireNonNull(mutationProposer, "mutationProposer");
        this.mutationValidator = Objects.requireNonNull(mutationValidator, "mutationValidator");
        this.candidateWorkspace = Objects.requireNonNull(candidateWorkspace, "candidateWorkspace");
        this.checkRunner = Objects.requireNonNull(checkRunner, "checkRunner");
        this.benchmarkRunner = Objects.requireNonNull(benchmarkRunner, "benchmarkRunner");
        this.fitnessScorer = Objects.requireNonNull(fitnessScorer, "fitnessScorer");
        this.metadataStore = Objects.requireNonNull(metadataStore, "metadataStore");
        this.decisionSink = Objects.requireNonNull(decisionSink, "decisionSink");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public FitnessResult evaluate(WorkflowGraph baseline) {
        Objects.requireNonNull(baseline, "baseline");

        Mutation mutation = Objects.requireNonNull(mutationProposer.proposeFor(baseline), "mutation");
        ValidationResult validation = Objects.requireNonNull(
                mutationValidator.validate(baseline, mutation),
                "validation"
        );
        if (!validation.valid()) {
            throw new IllegalStateException(
                    "mutation validation failed: " + String.join("; ", validation.messages())
            );
        }

        Candidate candidate = Objects.requireNonNull(
                candidateWorkspace.createCommittedCandidate(baseline, mutation),
                "candidate"
        );
        metadataStore.recordCandidate(candidate);

        EvaluationEvidence evidence = new EvaluationEvidence(
                checkRunner.runChecks(candidate),
                benchmarkRunner.runBenchmarks(candidate),
                Instant.now(clock)
        );
        FitnessResult result = Objects.requireNonNull(fitnessScorer.score(candidate, evidence), "result");
        if (!result.candidate().equals(candidate)) {
            throw new IllegalStateException("fitness result candidate does not match evaluated candidate");
        }
        metadataStore.recordFitness(result);

        switch (result.decision()) {
            case PROMOTE -> decisionSink.promote(candidate, result);
            case DISCARD -> decisionSink.discard(candidate, result);
        }

        return result;
    }
}
