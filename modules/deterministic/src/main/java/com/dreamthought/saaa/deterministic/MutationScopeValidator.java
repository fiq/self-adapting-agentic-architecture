package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.MutationScope;
import com.dreamthought.saaa.domain.ValidationResult;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.util.Objects;
import java.util.Set;

public final class MutationScopeValidator implements MutationValidator {
    private final Set<MutationScope> allowedScopes;

    public MutationScopeValidator(Set<MutationScope> allowedScopes) {
        this.allowedScopes = Set.copyOf(allowedScopes);
    }

    @Override
    public ValidationResult validate(WorkflowGraph baseline, Mutation mutation) {
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(mutation, "mutation");
        if (allowedScopes.contains(mutation.scope())) {
            return ValidationResult.passed();
        }
        return ValidationResult.invalid(
                "mutation scope " + mutation.scope() + " is not supported by this realization path");
    }
}
