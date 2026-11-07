package com.dreamthought.saaa.deterministic;

import static com.dreamthought.saaa.domain.MutationScope.WORKFLOW_DEFINITION;
import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.WorkflowGraph;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class WholeFileContentValidatorTest {
    private final WholeFileContentValidator validator = new WholeFileContentValidator();
    private final WorkflowGraph baseline = new WorkflowGraph("workflow", "v1", "draft-check: skip\n");

    @ParameterizedTest
    @ValueSource(strings = {
            "--- baseline: toy\n+++ mutated\n",
            "preamble\n--- old\n+++ new\ncontent\n",
            "--- old\r\n+++ new\r\n",
            "@@ definition @@\n publish-policy: allow\n-draft-check: skip\n+draft-check: enforce\n",
            "draft-check: enforce\n@@ -1 +1 @@\n",
            "@@"
    })
    void rejectsDiffShapedContent(String content) {
        var result = validator.validate(baseline, mutation(content));

        assertThat(result.valid()).isFalse();
        assertThat(result.messages()).containsExactly(
                "diff-shaped content is not supported; provide the complete new workflow file, not a diff");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "publish-policy: allow\ndraft-check: enforce\n",
            "+literal-setting\n-literal-setting\n",
            "--- document separator\nnormal workflow content\n",
            "+++ literal line\n",
            "--- standalone\ncontent\n+++ separate literal line\n",
            "mention @@ inline\n",
            "---old\n+++new\n"
    })
    void acceptsWholeFileContentIncludingOrdinaryPlusAndMinusLines(String content) {
        var result = validator.validate(baseline, mutation(content));

        assertThat(result.valid()).isTrue();
        assertThat(result.messages()).isEmpty();
    }

    private static Mutation mutation(String content) {
        return new Mutation("mut-file", "rewrite workflow", WORKFLOW_DEFINITION, content);
    }
}
