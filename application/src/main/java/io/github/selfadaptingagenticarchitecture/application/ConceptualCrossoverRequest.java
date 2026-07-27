package io.github.selfadaptingagenticarchitecture.application;

import io.github.selfadaptingagenticarchitecture.core.MutationBounds;
import io.github.selfadaptingagenticarchitecture.core.MutationOperatorType;
import io.github.selfadaptingagenticarchitecture.core.MutationTarget;
import io.github.selfadaptingagenticarchitecture.core.ParentTrait;
import java.util.List;
import java.util.Objects;

/**
 * A request to recombine evidence-backed traits from evaluated parents into one child mutation
 * contract. The operator is one of the closed enum values; conceptual crossover is the policy that
 * builds the request, not an operator of its own.
 */
public record ConceptualCrossoverRequest(
        String childId,
        MutationOperatorType operator,
        String hypothesis,
        MutationTarget target,
        List<String> loci,
        MutationBounds bounds,
        List<ParentTrait> parentTraits
) {
    public ConceptualCrossoverRequest {
        Objects.requireNonNull(childId, "childId");
        operator = Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(hypothesis, "hypothesis");
        target = Objects.requireNonNull(target, "target");
        loci = List.copyOf(Objects.requireNonNull(loci, "loci"));
        bounds = Objects.requireNonNull(bounds, "bounds");
        parentTraits = List.copyOf(Objects.requireNonNull(parentTraits, "parentTraits"));
    }
}
