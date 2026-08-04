package com.dreamthought.saaa.adapters.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.deterministic.EvidenceProjectionStore;
import com.dreamthought.saaa.domain.ProjectionStatus;
import com.dreamthought.saaa.domain.RepositoryProjection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RepositoryEvidenceIndexerTest {
    /**
     * Arranged rather than resolved from Git: the unit under test is projection building, so
     * repository identity is an input. Letting it fall through to {@code GitRepositoryRevision}
     * would make the result depend on whichever repository happens to enclose the temp directory.
     */
    private static final String REPOSITORY_ID = "repo";

    @TempDir Path temp;

    @Test
    void isIdempotentAndRemovesStaleProjectionFacts() throws IOException {
        Path root = temp.resolve("repo");
        Path knowledge = root.resolve(".agents/knowledge/architecture");
        Files.createDirectories(knowledge);
        Files.writeString(knowledge.resolve("ARCH-001-boundary.md"), """
                ---
                id: ARCH-001
                type: architecture
                title: Deterministic boundary
                status: canonical
                summary: Models propose and fitness decides.
                relates_to:
                  - SYS-001
                ---
                """);
        Path source = root.resolve("modules/deterministic/src/main/java/example/Loop.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package example;\npublic final class Loop {}\n");

        var store = new ReplacingStore(REPOSITORY_ID);
        var indexer = new RepositoryEvidenceIndexer(new RepositoryEvidenceExtractor(), store);

        RepositoryProjection first = indexer.build(root, "rev-1", REPOSITORY_ID);
        RepositoryProjection second = indexer.build(root, "rev-1", REPOSITORY_ID);

        assertThat(second.nodes()).isEqualTo(first.nodes());
        assertThat(second.edges()).isEqualTo(first.edges());
        assertThat(second.nodes()).extracting(node -> node.stableId())
                .contains("ARCH-001", "type:example.Loop");

        Files.delete(source);
        RepositoryProjection third = indexer.build(root, "rev-2", REPOSITORY_ID);

        assertThat(third.nodes()).extracting(node -> node.stableId())
                .contains("ARCH-001")
                .doesNotContain("type:example.Loop", "file:modules/deterministic/src/main/java/example/Loop.java");
        assertThat(store.current).isEqualTo(third);
    }

    private static final class ReplacingStore implements EvidenceProjectionStore {
        private final String repositoryId;
        private RepositoryProjection current;

        private ReplacingStore(String repositoryId) {
            this.repositoryId = repositoryId;
        }

        @Override
        public void replaceRepositoryProjection(RepositoryProjection projection) {
            current = projection;
        }

        @Override
        public ProjectionStatus status() {
            return current == null
                    ? new ProjectionStatus(repositoryId, Optional.empty(), Optional.empty(), 0, 0)
                    : new ProjectionStatus(
                            repositoryId,
                            Optional.of(current.repositoryRevision()),
                            Optional.of(current.schemaVersion()),
                            current.nodes().size(),
                            current.edges().size());
        }
    }
}
