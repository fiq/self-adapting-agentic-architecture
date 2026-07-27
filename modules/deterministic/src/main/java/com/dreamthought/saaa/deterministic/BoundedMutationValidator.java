package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.MutationLimits;
import com.dreamthought.saaa.domain.ValidationResult;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BoundedMutationValidator implements MutationValidator {
    private static final String AUTHORITY_MESSAGE =
            "mutation must not contain approval, scoring, promotion, discard or rollback authority";

    @Override
    public ValidationResult validate(WorkflowGraph baseline, Mutation mutation) {
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(mutation, "mutation");

        List<String> messages = new ArrayList<>();
        requireMaxLength(messages, "id", mutation.id(), MutationLimits.MAX_ID_LENGTH);
        requireMaxLength(messages, "summary", mutation.summary(), MutationLimits.MAX_SUMMARY_LENGTH);
        requireMaxLength(messages, "scope", mutation.scope().name(), MutationLimits.MAX_SCOPE_LENGTH);
        requireMaxLength(messages, "patch", mutation.patch(), MutationLimits.MAX_PATCH_LENGTH);
        rejectAuthority(messages, mutation);

        if (messages.isEmpty()) {
            return ValidationResult.passed();
        }
        return new ValidationResult(false, messages);
    }

    private static void requireMaxLength(List<String> messages, String name, String value, int maxLength) {
        if (value.length() > maxLength) {
            messages.add(name + " must be at most " + maxLength + " characters");
        }
    }

    private static void rejectAuthority(List<String> messages, Mutation mutation) {
        String text = mutation.id() + "\n" + mutation.summary() + "\n" + mutation.patch();
        if (AuthorityLanguage.isPresentIn(text)) {
            messages.add(AUTHORITY_MESSAGE);
        }
    }
}
