package io.github.selfadaptingagenticarchitecture.application;

import io.github.selfadaptingagenticarchitecture.core.MutationContract;
import io.github.selfadaptingagenticarchitecture.core.ParentTrait;
import io.github.selfadaptingagenticarchitecture.core.ValidationResult;
import java.util.Objects;
import java.util.Optional;

/**
 * Recombines lessons, not diffs. Two evaluated parents contribute evidence-backed traits, and the
 * policy emits one bounded child mutation contract using the closed operator enum. Raw Git diff
 * merging and multi-locus recombination stay deferred: splicing two nondeterministic edits produces
 * a child no deterministic gate can explain.
 */
public final class ConceptualCrossoverPolicy {
    private static final int MINIMUM_PARENTS = 2;

    private final MutationContractValidator validator;

    public ConceptualCrossoverPolicy() {
        this(new MutationContractValidator());
    }

    public ConceptualCrossoverPolicy(MutationContractValidator validator) {
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    public MutationContract createChildContract(ConceptualCrossoverRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.loci().size() != 1) {
            throw new IllegalArgumentException("conceptual crossover child must target one primary locus");
        }
        long distinctParents = request.parentTraits().stream()
                .map(ParentTrait::parentCandidateId)
                .distinct()
                .count();
        if (distinctParents < MINIMUM_PARENTS) {
            throw new IllegalArgumentException(
                    "conceptual crossover requires traits from at least " + MINIMUM_PARENTS + " evaluated parents"
            );
        }

        MutationOperatorDefaults defaults = MutationOperatorPolicy.defaultsFor(request.operator());
        MutationContract child = new MutationContract(
                request.childId(),
                request.operator(),
                request.hypothesis(),
                request.target(),
                request.loci(),
                request.bounds(),
                defaults.requiredEvidence(),
                defaults.hardGates(),
                defaults.objectives(),
                Optional.empty(),
                request.parentTraits()
        );

        // The policy validates its own output: a child that the deterministic gate would reject —
        // unclamped bounds, or a search operator whose posture crossover cannot supply — is never emitted.
        ValidationResult validation = validator.validate(child);
        if (!validation.valid()) {
            throw new IllegalArgumentException(
                    "conceptual crossover child contract is not valid: " + String.join("; ", validation.messages())
            );
        }
        return child;
    }
}
