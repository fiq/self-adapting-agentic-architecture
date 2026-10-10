package com.dreamthought.saaa.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MutationLoopCliTest {

    @Test
    void aWrappedFailureKeepsWhatFailedAsWellAsWhy() {
        var failure = new UncheckedIOException("failed to read ablation corpus",
                new NoSuchFileException("/missing.tsv"));

        assertThat(MutationLoopCli.describe(failure))
                .isEqualTo("failed to read ablation corpus: /missing.tsv");
    }

    @Test
    void aWrapperThatCopiesItsCausesMessageIsNotRepeated() {
        var cause = new IllegalStateException("generation cannot be ranked");
        var failure = new RuntimeException(cause.getMessage(), cause);

        assertThat(MutationLoopCli.describe(failure)).isEqualTo("generation cannot be ranked");
    }

    @Test
    void aCauseCycleLongerThanOneDoesNotHangTheErrorPath() {
        var first = new RuntimeException("first");
        var second = new RuntimeException("second", first);
        first.initCause(second);

        assertThat(MutationLoopCli.describe(first)).isEqualTo("first: second");
    }

    @Test
    void aMissingAblationCorpusSaysWhatFailedNotOnlyThePath(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("missing-corpus.tsv");
        var err = new StringWriter();
        var command = MutationLoopCli.commandLine();
        command.setErr(new PrintWriter(err, true));

        int exitCode = command.execute(
                "saaa-ablate", "retrieval", "--experiment-id", "x", "--corpus", missing.toString());

        assertThat(exitCode).isEqualTo(1);
        assertThat(err.toString().strip())
                .isEqualTo("error: failed to read ablation corpus: " + missing);
    }

    @Test
    void anUnknownOperatorIsABadOptionValueAndRendersAsOne(@TempDir Path tempDir) throws IOException {
        var err = new StringWriter();
        var command = MutationLoopCli.commandLine();
        command.setErr(new PrintWriter(err, true));

        int exitCode = command.execute(
                "saaa-evolve", tempDir.toString(), "--behaviour-case", "x", "--operator", "bogus");

        assertThat(exitCode).isEqualTo(2);
        assertThat(err.toString())
                .startsWith("unsupported mutation operator: bogus")
                .contains("Usage:");
    }
}
