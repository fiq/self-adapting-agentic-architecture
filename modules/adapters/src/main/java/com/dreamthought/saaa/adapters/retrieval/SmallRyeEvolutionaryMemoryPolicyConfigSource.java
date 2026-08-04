package com.dreamthought.saaa.adapters.retrieval;

import com.dreamthought.saaa.domain.EvolutionaryMemoryPolicyConfig;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

public final class SmallRyeEvolutionaryMemoryPolicyConfigSource {
    private final SmallRyeConfig config;

    public SmallRyeEvolutionaryMemoryPolicyConfigSource() {
        this(new SmallRyeConfigBuilder().addDefaultSources().build());
    }

    SmallRyeEvolutionaryMemoryPolicyConfigSource(SmallRyeConfig config) {
        this.config = config;
    }

    public EvolutionaryMemoryPolicyConfig load() {
        EvolutionaryMemoryPolicyConfig defaults = EvolutionaryMemoryPolicyConfig.defaults();
        return new EvolutionaryMemoryPolicyConfig(
                string("saaa.memory.policy-id", defaults.id()),
                integer("saaa.memory.champion-slots", defaults.championSlots()),
                integer("saaa.memory.lineage-slots", defaults.lineageSlots()),
                integer("saaa.memory.failure-fingerprint-slots", defaults.failureFingerprintSlots()),
                integer("saaa.memory.novelty-slots", defaults.noveltySlots()),
                integer("saaa.memory.exploration-slots", defaults.explorationSlots()),
                integer("saaa.memory.max-active-evaluations", defaults.maxActiveEvaluations()));
    }

    private String string(String name, String fallback) {
        return config.getOptionalValue(name, String.class).orElse(fallback);
    }

    private int integer(String name, int fallback) {
        return config.getOptionalValue(name, Integer.class).orElse(fallback);
    }
}
