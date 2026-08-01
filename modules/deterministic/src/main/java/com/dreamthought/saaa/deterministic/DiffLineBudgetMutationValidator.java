package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.ValidationResult;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.util.List;
import java.util.Objects;

public final class DiffLineBudgetMutationValidator implements MutationValidator {
    private final int maxLinesChanged;

    public DiffLineBudgetMutationValidator(int maxLinesChanged) {
        if (maxLinesChanged < 0) {
            throw new IllegalArgumentException("maxLinesChanged must not be negative");
        }
        this.maxLinesChanged = maxLinesChanged;
    }

    @Override
    public ValidationResult validate(WorkflowGraph baseline, Mutation mutation) {
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(mutation, "mutation");

        int linesChanged = linesChanged(baseline.definition(), mutation.patch());
        if (linesChanged <= maxLinesChanged) {
            return ValidationResult.passed();
        }
        return ValidationResult.invalid(
                "mutation diff changes "
                        + linesChanged
                        + " lines, exceeding maxLinesChanged "
                        + maxLinesChanged);
    }

    static int linesChanged(String baseline, String replacement) {
        List<String> before = baseline.lines().toList();
        List<String> after = replacement.lines().toList();
        // Candidate worktrees do not exist yet, so this mirrors Git's text numstat
        // additions+deletions with a pure LCS calculation at the proposal boundary.
        int unchanged = longestCommonSubsequenceLength(before, after);
        return before.size() + after.size() - (2 * unchanged);
    }

    private static int longestCommonSubsequenceLength(List<String> before, List<String> after) {
        int[] previous = new int[after.size() + 1];
        int[] current = new int[after.size() + 1];
        for (String beforeLine : before) {
            for (int afterIndex = 0; afterIndex < after.size(); afterIndex++) {
                if (beforeLine.equals(after.get(afterIndex))) {
                    current[afterIndex + 1] = previous[afterIndex] + 1;
                } else {
                    current[afterIndex + 1] = Math.max(previous[afterIndex + 1], current[afterIndex]);
                }
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[after.size()];
    }
}
