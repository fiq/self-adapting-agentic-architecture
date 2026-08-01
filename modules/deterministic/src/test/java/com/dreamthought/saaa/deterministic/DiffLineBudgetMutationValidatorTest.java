package com.dreamthought.saaa.deterministic;

import static com.dreamthought.saaa.domain.MutationScope.WORKFLOW_DEFINITION;
import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.WorkflowGraph;
import org.junit.jupiter.api.Test;

final class DiffLineBudgetMutationValidatorTest {
    private final WorkflowGraph baseline = new WorkflowGraph("workflow", "v1", """
            alpha
            beta
            gamma
            """);

    @Test
    void acceptsReplacementWhoseDiffFitsTheLineBudget() {
        var validator = new DiffLineBudgetMutationValidator(2);
        var mutation = new Mutation("mut-small", "change one line", WORKFLOW_DEFINITION, """
                alpha
                beta-updated
                gamma
                """);

        var result = validator.validate(baseline, mutation);

        assertThat(result.valid()).isTrue();
    }

    @Test
    void rejectsReplacementWhoseDiffExceedsTheLineBudget() {
        var validator = new DiffLineBudgetMutationValidator(1);
        var mutation = new Mutation("mut-large", "change one line", WORKFLOW_DEFINITION, """
                alpha
                beta-updated
                gamma
                """);

        var result = validator.validate(baseline, mutation);

        assertThat(result.valid()).isFalse();
        assertThat(result.messages())
                .containsExactly("mutation diff changes 2 lines, exceeding maxLinesChanged 1");
    }
}
