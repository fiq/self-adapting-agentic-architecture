package com.dreamthought.saaa.adapters.retrieval;

import com.dreamthought.saaa.adapters.git.GitRepositoryRevision;
import com.dreamthought.saaa.adapters.git.GitRevisionWorkspace;
import com.dreamthought.saaa.adapters.neo4j.Neo4jEvidenceGraph;
import com.dreamthought.saaa.adapters.neo4j.SmallRyeNeo4jConfigSource;
import com.dreamthought.saaa.adapters.repository.RepositoryEvidenceExtractor;
import com.dreamthought.saaa.adapters.repository.RepositoryEvidenceIndexer;
import com.dreamthought.saaa.adapters.repository.RepositoryEmbeddingIndexer;
import com.dreamthought.saaa.adapters.sqlite.SqliteRetrievalProjectionStore;
import com.dreamthought.saaa.adapters.langchain4j.LangChain4jEmbeddingAdapter;
import com.dreamthought.saaa.adapters.langchain4j.SmallRyeEmbeddingEndpointConfigSource;
import com.dreamthought.saaa.deterministic.CachedSemanticEmbeddingModel;
import com.dreamthought.saaa.domain.EmbeddedRepositoryProjection;
import com.dreamthought.saaa.domain.ProjectionStatus;
import com.dreamthought.saaa.domain.RepositoryProjection;
import com.dreamthought.saaa.domain.RepositoryRole;
import com.dreamthought.saaa.domain.EvolutionaryMemoryProjectionStatus;
import java.nio.file.Path;

public final class LocalEvidenceIndex {
    private LocalEvidenceIndex() { }

    public static RepositoryProjection build(Path repositoryRoot) {
        return build(repositoryRoot, RepositoryRole.SUBJECT);
    }

    public static RepositoryProjection build(Path repositoryRoot, RepositoryRole role) {
        Path root = GitRepositoryRevision.root(repositoryRoot);
        try (var graph = Neo4jEvidenceGraph.connect(new SmallRyeNeo4jConfigSource().load(root, role))) {
            RepositoryProjection projection = new RepositoryEvidenceIndexer(new RepositoryEvidenceExtractor(), graph)
                    .build(root, GitRepositoryRevision.workingTree(root));
            replayMemory(root, graph);
            return projection;
        }
    }

    public static EmbeddedRepositoryProjection buildWithVectors(Path repositoryRoot) {
        return buildWithVectors(repositoryRoot, RepositoryRole.SUBJECT);
    }

    public static EmbeddedRepositoryProjection buildWithVectors(Path repositoryRoot, RepositoryRole role) {
        Path root = GitRepositoryRevision.root(repositoryRoot);
        var config = new SmallRyeEmbeddingEndpointConfigSource().load();
        var cache = new SqliteRetrievalProjectionStore(root.resolve(".saaa/retrieval.sqlite"));
        var model = new CachedSemanticEmbeddingModel(
                LangChain4jEmbeddingAdapter.openAiCompatible(config), cache);
        try (var graph = Neo4jEvidenceGraph.connect(new SmallRyeNeo4jConfigSource().load(root, role))) {
            EmbeddedRepositoryProjection projection = new RepositoryEmbeddingIndexer(
                    new RepositoryEvidenceExtractor(), model, graph)
                    .build(root, GitRepositoryRevision.workingTree(root));
            replayMemory(root, graph);
            return projection;
        }
    }

    public static ProjectionStatus status(Path repositoryRoot) {
        Path root = GitRepositoryRevision.root(repositoryRoot);
        try (var graph = Neo4jEvidenceGraph.connect(new SmallRyeNeo4jConfigSource().load(root))) {
            return graph.status();
        }
    }

    public static EvolutionaryMemoryProjectionStatus memoryStatus(Path repositoryRoot) {
        Path root = GitRepositoryRevision.root(repositoryRoot);
        try (var graph = Neo4jEvidenceGraph.connect(new SmallRyeNeo4jConfigSource().load(root))) {
            return graph.memoryStatus();
        }
    }

    public static RepositoryProjection reinflate(Path repositoryRoot, String revision) {
        Path root = GitRepositoryRevision.root(repositoryRoot);
        var archive = LocalEvolutionaryMemoryFactory.archive(root);
        var policy = LocalEvolutionaryMemoryFactory.policy();
        try (var historic = GitRevisionWorkspace.open(root, revision);
             var graph = Neo4jEvidenceGraph.connect(new SmallRyeNeo4jConfigSource().load(root))) {
            RepositoryProjection projection = new RepositoryEvidenceIndexer(new RepositoryEvidenceExtractor(), graph)
                    .build(historic.path(), historic.revision(), GitRepositoryRevision.repositoryId(root));
            graph.replaceEvolutionaryMemory(
                    policy.selectForRevision(archive.records(), historic.revision()), policy.id());
            return projection;
        }
    }

    private static void replayMemory(Path root, Neo4jEvidenceGraph graph) {
        var archive = LocalEvolutionaryMemoryFactory.archive(root);
        var policy = LocalEvolutionaryMemoryFactory.policy();
        graph.replaceEvolutionaryMemory(policy.select(archive.records()), policy.id());
    }
}
