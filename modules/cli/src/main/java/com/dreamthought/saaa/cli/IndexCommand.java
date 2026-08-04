package com.dreamthought.saaa.cli;

import com.dreamthought.saaa.adapters.retrieval.LocalEvidenceIndex;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import com.dreamthought.saaa.domain.RepositoryRole;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "saaa-index",
        description = "Build, update or inspect the rebuildable local evidence projection.",
        subcommands = {IndexCommand.Build.class, IndexCommand.Update.class, IndexCommand.Status.class}
)
public final class IndexCommand implements Callable<Integer> {
    @Override
    public Integer call() {
        return 0;
    }

    abstract static class RepositoryCommand implements Callable<Integer> {
        @Option(names = "--repository", defaultValue = ".", description = "Git repository to project.")
        Path repository;

        @Option(names = "--role", defaultValue = "SUBJECT",
                description = "Repository role in evolution contexts: ${COMPLETION-CANDIDATES}")
        RepositoryRole role;
    }

    @Command(name = "build", description = "Replace the repository-owned graph projection atomically.")
    static final class Build extends RepositoryCommand {
        @Option(names = "--vectors", description = "Embed meaningful evidence units before atomically publishing the graph.")
        boolean vectors;

        @Override
        public Integer call() {
            if (vectors) {
                var embedded = LocalEvidenceIndex.buildWithVectors(repository, role);
                var projection = embedded.repositoryProjection();
                System.out.printf("index:%n  operation: build%n  repository: %s%n  revision: %s%n"
                                + "  schema_version: %s%n  nodes: %d%n  relationships: %d%n"
                                + "  embedding_model: %s%n  dimensions: %d%n",
                        projection.repositoryId(), projection.repositoryRevision(), projection.schemaVersion(),
                        projection.nodes().size(), projection.edges().size(), embedded.embeddingModelId(),
                        embedded.dimensions());
                return 0;
            }
            var projection = LocalEvidenceIndex.build(repository, role);
            System.out.printf("index:%n  operation: build%n  repository: %s%n  revision: %s%n"
                            + "  schema_version: %s%n  nodes: %d%n  relationships: %d%n",
                    projection.repositoryId(),
                    projection.repositoryRevision(),
                    projection.schemaVersion(),
                    projection.nodes().size(),
                    projection.edges().size());
            return 0;
        }
    }

    @Command(name = "update", description = "Deterministically replace changed facts and remove stale facts.")
    static final class Update extends RepositoryCommand {
        @Option(names = "--vectors", description = "Embed changed meaningful evidence units with cache reuse.")
        boolean vectors;

        @Override
        public Integer call() {
            if (vectors) {
                var embedded = LocalEvidenceIndex.buildWithVectors(repository, role);
                var projection = embedded.repositoryProjection();
                System.out.printf("index:%n  operation: update%n  repository: %s%n  revision: %s%n"
                                + "  schema_version: %s%n  nodes: %d%n  relationships: %d%n"
                                + "  embedding_model: %s%n  dimensions: %d%n",
                        projection.repositoryId(), projection.repositoryRevision(), projection.schemaVersion(),
                        projection.nodes().size(), projection.edges().size(), embedded.embeddingModelId(),
                        embedded.dimensions());
                return 0;
            }
            var projection = LocalEvidenceIndex.build(repository, role);
            System.out.printf("index:%n  operation: update%n  repository: %s%n  revision: %s%n"
                            + "  schema_version: %s%n  nodes: %d%n  relationships: %d%n",
                    projection.repositoryId(),
                    projection.repositoryRevision(),
                    projection.schemaVersion(),
                    projection.nodes().size(),
                    projection.edges().size());
            return 0;
        }
    }

    @Command(name = "status", description = "Show the current local graph projection revision.")
    static final class Status extends RepositoryCommand {
        @Override
        public Integer call() {
            var status = LocalEvidenceIndex.status(repository);
            var memory = LocalEvidenceIndex.memoryStatus(repository);
            System.out.printf("index:%n  repository: %s%n  revision: %s%n  schema_version: %s%n"
                            + "  nodes: %d%n  relationships: %d%n  memory_policy_id: %s%n"
                            + "  active_evaluations: %d%n",
                    status.repositoryId(),
                    status.repositoryRevision().orElse("not-built"),
                    status.schemaVersion().orElse("not-built"),
                    status.nodeCount(),
                    status.relationshipCount(),
                    memory.policyId().orElse("not-built"),
                    memory.activeEvaluations());
            return 0;
        }
    }
}
