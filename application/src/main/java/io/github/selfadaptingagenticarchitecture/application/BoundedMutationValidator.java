package io.github.selfadaptingagenticarchitecture.application;

import io.github.selfadaptingagenticarchitecture.core.Mutation;
import io.github.selfadaptingagenticarchitecture.core.MutationLimits;
import io.github.selfadaptingagenticarchitecture.core.ValidationResult;
import io.github.selfadaptingagenticarchitecture.core.WorkflowGraph;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class BoundedMutationValidator implements MutationValidator {
    private static final String AUTHORITY_TARGET = "(candidate|mutation|result)";
    private static final String SCORING_TERM = "(score|scoring|fitness)";
    private static final String UP_TO_THREE_WORDS = "(?:\\W+\\w+){0,3}\\W+";
    private static final Pattern AUTHORITY_TEXT = Pattern.compile(
            "\\b(approve|approval|promote|promotion|discard|rollback)\\b"
                    + "|\\b" + SCORING_TERM + "\\b" + UP_TO_THREE_WORDS + "\\b" + AUTHORITY_TARGET + "\\b"
                    + "|\\b" + AUTHORITY_TARGET + "\\b" + UP_TO_THREE_WORDS + "\\b" + SCORING_TERM + "\\b",
            Pattern.CASE_INSENSITIVE
    );
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
        if (AUTHORITY_TEXT.matcher(text).find()) {
            messages.add(AUTHORITY_MESSAGE);
        }
    }
}
