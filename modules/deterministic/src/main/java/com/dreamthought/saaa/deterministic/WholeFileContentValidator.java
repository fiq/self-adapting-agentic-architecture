package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.ValidationResult;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.util.Objects;

/** Rejects diff syntax where the realizer expects complete replacement file content. */
public final class WholeFileContentValidator implements MutationValidator {
    @Override
    public ValidationResult validate(WorkflowGraph baseline, Mutation mutation) {
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(mutation, "mutation");
        boolean previousLineIsOldHeader = false;
        boolean previousLineIsContextHeader = false;
        for (String rawLine : mutation.patch().lines().toList()) {
            String line = rawLine.replaceFirst("^[ \\t]+", "");
            if (line.startsWith("@@")
                    || line.startsWith("diff --git ")
                    || line.equals("***************")
                    || (previousLineIsOldHeader && line.startsWith("+++ "))
                    || (previousLineIsContextHeader && line.startsWith("--- "))) {
                return ValidationResult.invalid(
                        "the proposal is a diff, not the complete workflow file (diff-shaped content); no candidate was created");
            }
            previousLineIsOldHeader = line.startsWith("--- ");
            previousLineIsContextHeader = line.startsWith("*** ");
        }
        return ValidationResult.passed();
    }
}
