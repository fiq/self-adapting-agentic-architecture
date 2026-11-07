package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.ValidationResult;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.util.Objects;

/** Rejects unified diff syntax where the realizer expects complete replacement file content. */
public final class WholeFileContentValidator implements MutationValidator {
    @Override
    public ValidationResult validate(WorkflowGraph baseline, Mutation mutation) {
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(mutation, "mutation");
        boolean previousLineIsOldHeader = false;
        for (String line : mutation.patch().lines().toList()) {
            if (line.startsWith("@@") || (previousLineIsOldHeader && line.startsWith("+++ "))) {
                return ValidationResult.invalid(
                        "diff-shaped content is not supported; provide the complete new workflow file, not a diff");
            }
            previousLineIsOldHeader = line.startsWith("--- ");
        }
        return ValidationResult.passed();
    }
}
