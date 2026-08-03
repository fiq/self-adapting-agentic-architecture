package com.dreamthought.saaa.domain;

import java.util.Objects;

/** One comparable mutation attempt in a retrieval ablation. */
public record RetrievalAttemptMetrics(
        String taskId,
        RetrievalMode mode,
        int attempt,
        boolean accepted,
        double fitness,
        double fitnessDelta,
        int hardGateFailures,
        int invalidMutations,
        int regressions,
        int filesChanged,
        int symbolsChanged,
        int modelInputTokens,
        int modelOutputTokens,
        double providerCost,
        long wallClockMillis,
        long retrievalMillis,
        int graphNodesConsidered,
        int evidenceCount,
        int evidenceContextTokens,
        int cacheHits,
        int cacheMisses,
        String retrievalConfigurationId,
        String memoryPolicyId
) {
    public RetrievalAttemptMetrics {
        taskId = Require.nonBlank(taskId, "taskId");
        mode = Objects.requireNonNull(mode, "mode");
        retrievalConfigurationId = Require.nonBlank(retrievalConfigurationId, "retrievalConfigurationId");
        memoryPolicyId = Require.nonBlank(memoryPolicyId, "memoryPolicyId");
        if (attempt < 1 || !Double.isFinite(fitness) || !Double.isFinite(fitnessDelta)
                || !Double.isFinite(providerCost) || providerCost < 0 || wallClockMillis < 0
                || retrievalMillis < 0 || retrievalMillis > wallClockMillis) {
            throw new IllegalArgumentException("invalid ablation attempt values");
        }
        if (hardGateFailures < 0 || invalidMutations < 0 || regressions < 0 || filesChanged < 0
                || symbolsChanged < 0 || modelInputTokens < 0 || modelOutputTokens < 0
                || graphNodesConsidered < 0 || evidenceCount < 0 || evidenceContextTokens < 0
                || cacheHits < 0 || cacheMisses < 0) {
            throw new IllegalArgumentException("ablation counters must not be negative");
        }
    }

    public double mutationCost() {
        return providerCost > 0 ? providerCost : modelInputTokens + modelOutputTokens;
    }
}
