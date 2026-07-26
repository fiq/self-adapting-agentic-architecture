package io.github.selfadaptingagenticarchitecture.application;

import static io.github.selfadaptingagenticarchitecture.core.MutationScope.WORKFLOW_DEFINITION;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.selfadaptingagenticarchitecture.core.Mutation;
import io.github.selfadaptingagenticarchitecture.core.MutationLimits;
import io.github.selfadaptingagenticarchitecture.core.WorkflowGraph;
import org.junit.jupiter.api.Test;

final class BoundedMutationValidatorTest {
    private final BoundedMutationValidator validator = new BoundedMutationValidator();
    private final WorkflowGraph baseline = new WorkflowGraph("baseline", "v1", "agent -> tool -> answer");

    @Test
    void acceptsBoundedWorkflowMutation() {
        var mutation = new Mutation(
                "mut-001",
                "tighten tool selection",
                WORKFLOW_DEFINITION,
                "replace tool policy with deterministic selection criteria"
        );

        var result = validator.validate(baseline, mutation);

        assertThat(result.valid()).isTrue();
        assertThat(result.messages()).isEmpty();
    }

    @Test
    void rejectsOversizedMutationFields() {
        var mutation = new Mutation(
                "x".repeat(MutationLimits.MAX_ID_LENGTH + 1),
                "x".repeat(MutationLimits.MAX_SUMMARY_LENGTH + 1),
                WORKFLOW_DEFINITION,
                "x".repeat(MutationLimits.MAX_PATCH_LENGTH + 1)
        );

        var result = validator.validate(baseline, mutation);

        assertThat(result.valid()).isFalse();
        assertThat(result.messages())
                .containsExactly(
                        "id must be at most " + MutationLimits.MAX_ID_LENGTH + " characters",
                        "summary must be at most " + MutationLimits.MAX_SUMMARY_LENGTH + " characters",
                        "patch must be at most " + MutationLimits.MAX_PATCH_LENGTH + " characters"
                );
    }

    @Test
    void rejectsAuthorityBearingMutationText() {
        var mutation = new Mutation(
                "promote-candidate",
                "discard this candidate",
                WORKFLOW_DEFINITION,
                "score this candidate after applying patch"
        );

        var result = validator.validate(baseline, mutation);

        assertThat(result.valid()).isFalse();
        assertThat(result.messages())
                .containsExactly(
                        "mutation must not contain approval, scoring, promotion, discard or rollback authority"
                );
    }

    @Test
    void allowsWorkflowDecisionNodeTextWithoutCandidateAuthority() {
        var mutation = new Mutation(
                "mut-decision-node",
                "adjust decision node routing",
                WORKFLOW_DEFINITION,
                "replace decision node routing when tool output is unavailable"
        );

        var result = validator.validate(baseline, mutation);

        assertThat(result.valid()).isTrue();
        assertThat(result.messages()).isEmpty();
    }
}
