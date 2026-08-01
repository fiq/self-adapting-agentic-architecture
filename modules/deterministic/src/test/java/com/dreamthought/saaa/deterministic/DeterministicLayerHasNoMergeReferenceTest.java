package com.dreamthought.saaa.deterministic;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class DeterministicLayerHasNoMergeReferenceTest {
    @Test
    void deterministicSourcesContainNoGitMergeReference() throws Exception {
        Path sourceRoot = Path.of("src/main/java");
        String sourceText;
        try (var sources = Files.walk(sourceRoot)) {
            sourceText = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(DeterministicLayerHasNoMergeReferenceTest::readString)
                    .reduce("", (left, right) -> left + "\n" + right);
        }

        assertThat(sourceText)
                .doesNotContain("git merge")
                .doesNotContain("merge --ff")
                .doesNotContain("merge --no-ff");
    }

    private static String readString(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception exception) {
            throw new IllegalStateException("failed to read " + path, exception);
        }
    }
}
