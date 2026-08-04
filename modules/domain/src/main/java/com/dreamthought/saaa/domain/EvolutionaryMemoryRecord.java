package com.dreamthought.saaa.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Observable proposal/evaluation facts only; never private reasoning or model-authored approval. */
public record EvolutionaryMemoryRecord(
        EvolutionContext evolutionContext,
        String memoryPolicyId,
        String mutationId,
        String mutationSummary,
        MutationScope mutationScope,
        String candidateId,
        String candidateCommit,
        RetrievalMode retrievalMode,
        String retrievalConfigurationId,
        List<String> changedPaths,
        List<String> retrievedEvidenceIds,
        List<CheckEvidence> checks,
        List<BenchmarkEvidence> benchmarks,
        double aggregateFitness,
        FitnessDecision decision,
        Instant evaluatedAt
) {
    public EvolutionaryMemoryRecord {
        evolutionContext = Objects.requireNonNull(evolutionContext, "evolutionContext");
        memoryPolicyId = Require.nonBlank(memoryPolicyId, "memoryPolicyId");
        mutationId = Require.nonBlank(mutationId, "mutationId");
        mutationSummary = Require.nonBlank(mutationSummary, "mutationSummary");
        mutationScope = Objects.requireNonNull(mutationScope, "mutationScope");
        candidateId = Require.nonBlank(candidateId, "candidateId");
        candidateCommit = Require.nonBlank(candidateCommit, "candidateCommit");
        retrievalMode = Objects.requireNonNull(retrievalMode, "retrievalMode");
        retrievalConfigurationId = Require.nonBlank(retrievalConfigurationId, "retrievalConfigurationId");
        changedPaths = List.copyOf(Objects.requireNonNull(changedPaths, "changedPaths"));
        retrievedEvidenceIds = List.copyOf(Objects.requireNonNull(retrievedEvidenceIds, "retrievedEvidenceIds"));
        checks = List.copyOf(Objects.requireNonNull(checks, "checks"));
        benchmarks = List.copyOf(Objects.requireNonNull(benchmarks, "benchmarks"));
        if (!Double.isFinite(aggregateFitness)) {
            throw new IllegalArgumentException("aggregateFitness must be finite");
        }
        decision = Objects.requireNonNull(decision, "decision");
        evaluatedAt = Objects.requireNonNull(evaluatedAt, "evaluatedAt");
    }

    public EvolutionaryMemoryRecord(
            EvolutionContext evolutionContext,
            String memoryPolicyId,
            String mutationId,
            String mutationSummary,
            MutationScope mutationScope,
            String candidateId,
            String candidateCommit,
            RetrievalMode retrievalMode,
            String retrievalConfigurationId,
            List<String> retrievedEvidenceIds,
            List<CheckEvidence> checks,
            List<BenchmarkEvidence> benchmarks,
            double aggregateFitness,
            FitnessDecision decision,
            Instant evaluatedAt) {
        this(evolutionContext, memoryPolicyId, mutationId, mutationSummary, mutationScope,
                candidateId, candidateCommit, retrievalMode, retrievalConfigurationId, List.of(),
                retrievedEvidenceIds, checks, benchmarks, aggregateFitness, decision, evaluatedAt);
    }

    public String baselineRepositoryRevision() {
        return evolutionContext.subjectRepositoryRevision();
    }
}
