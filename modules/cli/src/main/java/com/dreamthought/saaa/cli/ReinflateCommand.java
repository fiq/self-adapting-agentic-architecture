package com.dreamthought.saaa.cli;

import com.dreamthought.saaa.adapters.retrieval.LocalEvidenceIndex;
import com.dreamthought.saaa.adapters.retrieval.LocalEvolutionaryMemoryFactory;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "saaa-reinflate",
        description = "Explicitly rebuild a bounded graph projection for an historic Git revision.")
public final class ReinflateCommand implements Callable<Integer> {
    @Option(names = "--repository", defaultValue = ".", description = "Subject Git repository.")
    Path repository;

    @Option(names = "--revision", required = true, description = "Commit to project into the graph.")
    String revision;

    @Override
    public Integer call() {
        var projection = LocalEvidenceIndex.reinflate(repository, revision);
        var policy = LocalEvolutionaryMemoryFactory.policy();
        System.out.printf("reinflation:%n  repository: %s%n  revision: %s%n  schema_version: %s%n"
                        + "  memory_policy_id: %s%n  nodes: %d%n  relationships: %d%n",
                projection.repositoryId(), projection.repositoryRevision(), projection.schemaVersion(),
                policy.id(), projection.nodes().size(), projection.edges().size());
        return 0;
    }
}
