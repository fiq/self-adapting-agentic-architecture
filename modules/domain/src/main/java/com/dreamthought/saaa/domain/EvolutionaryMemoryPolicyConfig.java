package com.dreamthought.saaa.domain;

public record EvolutionaryMemoryPolicyConfig(
        String id,
        int championSlots,
        int lineageSlots,
        int failureFingerprintSlots,
        int noveltySlots,
        int explorationSlots,
        int maxActiveEvaluations
) {
    public EvolutionaryMemoryPolicyConfig {
        id = Require.nonBlank(id, "id");
        if (championSlots < 1 || lineageSlots < 0 || failureFingerprintSlots < 0 || noveltySlots < 0
                || explorationSlots < 0 || maxActiveEvaluations < 1
                || championSlots + lineageSlots + failureFingerprintSlots + noveltySlots + explorationSlots
                        > maxActiveEvaluations) {
            throw new IllegalArgumentException("evolutionary memory policy bounds are invalid");
        }
    }

    public static EvolutionaryMemoryPolicyConfig defaults() {
        return new EvolutionaryMemoryPolicyConfig("lineage-novelty-v1", 12, 12, 24, 32, 16, 96);
    }
}
