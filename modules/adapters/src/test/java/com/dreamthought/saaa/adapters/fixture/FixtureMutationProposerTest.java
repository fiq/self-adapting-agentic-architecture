package com.dreamthought.saaa.adapters.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dreamthought.saaa.domain.MutationScope;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FixtureMutationProposerTest {
    private final WorkflowGraph baseline = new WorkflowGraph("toy", "v1", "old content");

    @Test
    void readsSummaryFromTheFirstLineAndPatchFromTheRest(@TempDir Path dir) throws IOException {
        Path fixture = dir.resolve("fixture-mutation.txt");
        Files.writeString(fixture, "tighten the publish guard\nline one\nline two\n");

        var mutation = new FixtureMutationProposer(fixture).proposeFor(baseline);

        assertThat(mutation.summary()).isEqualTo("tighten the publish guard");
        assertThat(mutation.patch()).isEqualTo("line one\nline two\n");
        assertThat(mutation.scope()).isEqualTo(MutationScope.WORKFLOW_DEFINITION);
        assertThat(mutation.id()).isNotBlank();
    }

    @Test
    void producesTheSameMutationEveryTimeForTheSameFixture(@TempDir Path dir) throws IOException {
        Path fixture = dir.resolve("fixture-mutation.txt");
        Files.writeString(fixture, "tighten the publish guard\nnew content\n");
        var proposer = new FixtureMutationProposer(fixture);

        assertThat(proposer.proposeFor(baseline)).isEqualTo(proposer.proposeFor(baseline));
    }

    @Test
    void failsClearlyWhenTheFixtureIsMissing(@TempDir Path dir) {
        assertThatThrownBy(() -> new FixtureMutationProposer(dir.resolve("absent.txt")).proposeFor(baseline))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("absent.txt");
    }
}
