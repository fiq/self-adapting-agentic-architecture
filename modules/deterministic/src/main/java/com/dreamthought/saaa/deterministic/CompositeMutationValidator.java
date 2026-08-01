package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.ValidationResult;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CompositeMutationValidator implements MutationValidator {
    private final List<MutationValidator> validators;

    public CompositeMutationValidator(List<MutationValidator> validators) {
        this.validators = List.copyOf(validators);
        this.validators.forEach(validator -> Objects.requireNonNull(validator, "validator"));
    }

    @Override
    public ValidationResult validate(WorkflowGraph baseline, Mutation mutation) {
        List<String> messages = new ArrayList<>();
        for (MutationValidator validator : validators) {
            ValidationResult result = validator.validate(baseline, mutation);
            if (!result.valid()) {
                messages.addAll(result.messages());
            }
        }
        if (messages.isEmpty()) {
            return ValidationResult.passed();
        }
        return new ValidationResult(false, messages);
    }
}
