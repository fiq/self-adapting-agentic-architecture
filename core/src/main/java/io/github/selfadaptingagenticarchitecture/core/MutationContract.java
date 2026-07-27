package io.github.selfadaptingagenticarchitecture.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A bounded behavioral variation proposed for one individual. The contract is the mutation; a Git
 * diff is only the realization produced later inside an isolated candidate worktree.
 */
public record MutationContract(
        String id,
        MutationOperatorType operator,
        String hypothesis,
        MutationTarget target,
        List<String> loci,
        MutationBounds bounds,
        List<String> requiredEvidence,
        List<String> hardGates,
        List<FitnessObjective> objectives,
        Optional<SearchPosture> searchPosture,
        List<ParentTrait> parentTraits
) {
    public MutationContract {
        id = Require.nonBlank(id, "id");
        operator = Objects.requireNonNull(operator, "operator");
        hypothesis = Require.nonBlank(hypothesis, "hypothesis");
        target = Objects.requireNonNull(target, "target");
        loci = List.copyOf(Objects.requireNonNull(loci, "loci"));
        bounds = Objects.requireNonNull(bounds, "bounds");
        requiredEvidence = List.copyOf(Objects.requireNonNull(requiredEvidence, "requiredEvidence"));
        hardGates = List.copyOf(Objects.requireNonNull(hardGates, "hardGates"));
        objectives = List.copyOf(Objects.requireNonNull(objectives, "objectives"));
        searchPosture = Objects.requireNonNull(searchPosture, "searchPosture");
        parentTraits = List.copyOf(Objects.requireNonNull(parentTraits, "parentTraits"));
    }
}
