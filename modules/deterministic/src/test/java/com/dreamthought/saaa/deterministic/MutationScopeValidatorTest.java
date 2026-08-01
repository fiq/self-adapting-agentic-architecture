package com.dreamthought.saaa.deterministic;

import static com.dreamthought.saaa.domain.MutationScope.TOOL_CONFIGURATION;
import static com.dreamthought.saaa.domain.MutationScope.WORKFLOW_DEFINITION;
import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class MutationScopeValidatorTest {
    private final MutationScopeValidator validator = new MutationScopeValidator(Set.of(WORKFLOW_DEFINITION));
    private final WorkflowGraph baseline = new WorkflowGraph("workflow", "v1", "agent -> answer");

    @Test
    void acceptsMutationScopeSupportedByTheRealizationPath() {
        var mutation = new Mutation("mut-workflow", "rewrite workflow", WORKFLOW_DEFINITION, "agent -> tool");

        assertThat(validator.validate(baseline, mutation).valid()).isTrue();
    }

    @Test
    void rejectsMutationScopeThatTheRealizationPathCannotApply() {
        var mutation = new Mutation("mut-tool", "rewrite tool config", TOOL_CONFIGURATION, "tool = deterministic");

        var result = validator.validate(baseline, mutation);

        assertThat(result.valid()).isFalse();
        assertThat(result.messages())
                .containsExactly("mutation scope TOOL_CONFIGURATION is not supported by this realization path");
    }
}
