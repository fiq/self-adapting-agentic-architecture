package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.CandidateBranchRef;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.MutationProposalRequest;
import com.dreamthought.saaa.domain.PreparedMutationProposalRequest;
import com.dreamthought.saaa.domain.ValidationResult;
import com.dreamthought.saaa.domain.WorkflowGraph;
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
    private final EvolutionReporter reporter;
    private final Clock clock;
    private final EvidenceRetriever evidenceRetriever;
    private final EvolutionaryMemoryProjector memoryProjector;

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
                EvolutionReporter.NO_OP,
                Clock.systemUTC(),
                EvidenceRetriever.none("retrieval-config-v1"),
                EvolutionaryMemoryProjector.disabled()
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
        this(
                mutationProposer,
                mutationValidator,
                candidateWorkspace,
                checkRunner,
                benchmarkRunner,
                fitnessScorer,
                metadataStore,
                decisionSink,
                EvolutionReporter.NO_OP,
                clock,
                EvidenceRetriever.none("retrieval-config-v1"),
                EvolutionaryMemoryProjector.disabled()
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
            EvolutionReporter reporter,
            Clock clock
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
                reporter,
                clock,
                EvidenceRetriever.none("retrieval-config-v1"),
                EvolutionaryMemoryProjector.disabled()
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
            EvolutionReporter reporter,
            Clock clock,
            EvidenceRetriever evidenceRetriever
    ) {
        this(mutationProposer, mutationValidator, candidateWorkspace, checkRunner, benchmarkRunner,
                fitnessScorer, metadataStore, decisionSink, reporter, clock, evidenceRetriever,
                EvolutionaryMemoryProjector.disabled());
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
            EvolutionReporter reporter,
            Clock clock,
            EvidenceRetriever evidenceRetriever,
            EvolutionaryMemoryProjector memoryProjector
    ) {
        this.mutationProposer = Objects.requireNonNull(mutationProposer, "mutationProposer");
        this.mutationValidator = Objects.requireNonNull(mutationValidator, "mutationValidator");
        this.candidateWorkspace = Objects.requireNonNull(candidateWorkspace, "candidateWorkspace");
        this.checkRunner = Objects.requireNonNull(checkRunner, "checkRunner");
        this.benchmarkRunner = Objects.requireNonNull(benchmarkRunner, "benchmarkRunner");
        this.fitnessScorer = Objects.requireNonNull(fitnessScorer, "fitnessScorer");
        this.metadataStore = Objects.requireNonNull(metadataStore, "metadataStore");
        this.decisionSink = Objects.requireNonNull(decisionSink, "decisionSink");
        this.reporter = Objects.requireNonNull(reporter, "reporter");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.evidenceRetriever = Objects.requireNonNull(evidenceRetriever, "evidenceRetriever");
        this.memoryProjector = Objects.requireNonNull(memoryProjector, "memoryProjector");
    }

    public FitnessResult evaluate(WorkflowGraph baseline) {
        Objects.requireNonNull(baseline, "baseline");

        Mutation mutation = Objects.requireNonNull(mutationProposer.proposeFor(baseline), "mutation");
        return evaluateProposed(baseline, mutation);
    }

    public FitnessResult evaluate(MutationProposalRequest request) {
        Objects.requireNonNull(request, "request");
        var retrieval = Objects.requireNonNull(
                evidenceRetriever.retrieve(request.retrievalQuery()), "retrieval bundle");
        reporter.retrievalPrepared(retrieval);
        Mutation mutation = Objects.requireNonNull(
                mutationProposer.proposeFor(new PreparedMutationProposalRequest(
                        request.baseline(), request.retrievalQuery(), retrieval)),
                "mutation");
        return evaluateProposed(request.baseline(), mutation, retrieval);
    }

    private FitnessResult evaluateProposed(WorkflowGraph baseline, Mutation mutation) {
        return evaluateProposed(baseline, mutation, null);
    }

    private FitnessResult evaluateProposed(
            WorkflowGraph baseline, Mutation mutation, com.dreamthought.saaa.domain.RetrievalBundle retrieval) {
        reporter.proposed(mutation);
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
        reporter.candidateCreated(candidate);

        EvaluationEvidence evidence = new EvaluationEvidence(
                checkRunner.runChecks(candidate),
                benchmarkRunner.runBenchmarks(candidate),
                Instant.now(clock)
        );
        reporter.evidenceCollected(evidence);
        FitnessResult result = Objects.requireNonNull(fitnessScorer.score(candidate, evidence), "result");
        if (!result.candidate().equals(candidate)) {
            throw new IllegalStateException("fitness result candidate does not match evaluated candidate");
        }
        metadataStore.recordFitness(result);
        reporter.scored(result);

        CandidateBranchRef candidateBranchRef = CandidateBranchRef.fromCandidate(candidate);
        switch (result.decision()) {
            case PROMOTE -> decisionSink.recordPromotedCandidateBranch(candidateBranchRef, result);
            case DISCARD -> decisionSink.recordDiscardedCandidateBranch(candidateBranchRef, result);
        }
        if (retrieval != null) {
            memoryProjector.project(mutation, retrieval, result);
        }

        return result;
    }
}
