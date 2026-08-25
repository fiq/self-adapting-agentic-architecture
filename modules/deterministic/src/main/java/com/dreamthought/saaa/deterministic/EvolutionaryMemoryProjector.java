package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.EvolutionaryMemoryRecord;
import com.dreamthought.saaa.domain.EvolutionContext;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.RetrievalBundle;
import java.util.Objects;

/** Converts an evaluated attempt into durable, observable search memory. */
public final class EvolutionaryMemoryProjector {
    private final EvolutionaryMemoryStore store;
    private final String memoryPolicyId;
    private final EvolutionContext evolutionContext;
    private final ChangedPathInspector changedPathInspector;
    private final boolean enabled;

    public EvolutionaryMemoryProjector(
            EvolutionaryMemoryStore store, String memoryPolicyId, EvolutionContext evolutionContext) {
        this(store, memoryPolicyId, evolutionContext, ChangedPathInspector.disabled());
    }

    public EvolutionaryMemoryProjector(
            EvolutionaryMemoryStore store,
            String memoryPolicyId,
            EvolutionContext evolutionContext,
            ChangedPathInspector changedPathInspector) {
        this(store, memoryPolicyId, evolutionContext, changedPathInspector, true);
    }

    private EvolutionaryMemoryProjector(
            EvolutionaryMemoryStore store,
            String memoryPolicyId,
            EvolutionContext evolutionContext,
            ChangedPathInspector changedPathInspector,
            boolean enabled) {
        this.store = Objects.requireNonNull(store, "store");
        this.memoryPolicyId = Objects.requireNonNull(memoryPolicyId, "memoryPolicyId");
        this.evolutionContext = Objects.requireNonNull(evolutionContext, "evolutionContext");
        this.changedPathInspector = Objects.requireNonNull(changedPathInspector, "changedPathInspector");
        this.enabled = enabled;
    }

    public void project(Mutation mutation, RetrievalBundle retrieval, FitnessResult result) {
        Objects.requireNonNull(mutation, "mutation");
        Objects.requireNonNull(retrieval, "retrieval");
        Objects.requireNonNull(result, "result");
        if (!enabled) return;
        if (!evolutionContext.subjectRepositoryRevision().equals(retrieval.repositoryRevision())) {
            throw new IllegalStateException("evolution context and retrieval repository revisions differ");
        }
        store.append(new EvolutionaryMemoryRecord(
                evolutionContext, memoryPolicyId, mutation.id(), mutation.summary(), mutation.scope(),
                result.candidate().id(), result.candidate().commitSha(),
                retrieval.mode(), retrieval.configurationId(),
                changedPathInspector.inspect(result.candidate()),
                retrieval.capsules().stream().map(capsule -> capsule.subject().stableId()).toList(),
                result.evidence().checks(), result.evidence().benchmarks(), result.fitnessScore(),
                // Carried from the result rather than defaulted. A projected record without the
                // run's own fingerprint would claim comparability it never had, and nothing
                // downstream could tell.
                result.scoringFingerprint(),
                result.evidence().evaluatedAt()));
    }

    public static EvolutionaryMemoryProjector disabled() {
        return new EvolutionaryMemoryProjector(EvolutionaryMemoryStore.disabled(), "disabled",
                new EvolutionContext("disabled", "disabled", "disabled", "disabled"),
                ChangedPathInspector.disabled(), false);
    }
}
