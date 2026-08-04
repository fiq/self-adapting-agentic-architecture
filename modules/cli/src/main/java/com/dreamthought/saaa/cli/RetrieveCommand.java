package com.dreamthought.saaa.cli;

import com.dreamthought.saaa.adapters.git.GitRepositoryRevision;
import com.dreamthought.saaa.adapters.retrieval.LocalRetrievalFactory;
import com.dreamthought.saaa.domain.RetrievalMode;
import com.dreamthought.saaa.domain.RetrievalQuery;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "saaa-retrieve", description = "Inspect one bounded retrieval treatment without invoking a model.")
public final class RetrieveCommand implements Callable<Integer> {
    @Option(names = "--repository", defaultValue = ".", description = "Indexed Git repository.")
    private Path repository;

    @Option(names = "--task", required = true, description = "Mutation task/hypothesis used for discovery.")
    private String task;

    @Option(names = "--mode", defaultValue = "HYBRID", description = "${COMPLETION-CANDIDATES}")
    private RetrievalMode mode;

    @Option(names = "--exact", description = "Exact repository id, symbol or path seed; repeatable.")
    private List<String> exact = new ArrayList<>();

    @Override
    public Integer call() {
        Path root = com.dreamthought.saaa.adapters.git.GitRepositoryRevision.root(repository);
        String revision = GitRepositoryRevision.workingTree(root);
        var baseline = new WorkflowGraph(root.getFileName().toString(), revision, task);
        var query = new RetrievalQuery(mode, task, baseline, revision, exact, Optional.empty());
        var bundle = LocalRetrievalFactory.forMode(mode, root).retrieve(query);

        System.out.printf("retrieval:%n  mode: %s%n  configuration_id: %s%n  repository_revision: %s%n"
                        + "  graph_schema_version: %s%n  embedding_model_id: %s%n  evidence_count: %d%n"
                        + "  memory_policy_id: %s%n  estimated_tokens: %d%n"
                        + "  graph_nodes_considered: %d%n  evidence:%n",
                bundle.mode(),
                bundle.configurationId(),
                bundle.repositoryRevision(),
                bundle.graphSchemaVersion(),
                bundle.embeddingModelId(),
                bundle.capsules().size(),
                bundle.memoryPolicyId(),
                bundle.estimatedTokens(),
                bundle.diagnostics().graphNodesConsidered());
        bundle.capsules().forEach(capsule -> System.out.printf(
                "    - id: %s%n      kind: %s%n      authority: %s%n      reasons: %s%n      source: %s%n",
                capsule.subject().stableId(),
                capsule.subject().kind(),
                capsule.authority(),
                capsule.selectionReasons(),
                capsule.sources().getFirst().path()));
        return 0;
    }
}
