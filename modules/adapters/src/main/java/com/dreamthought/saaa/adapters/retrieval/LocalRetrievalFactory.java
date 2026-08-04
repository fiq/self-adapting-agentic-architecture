package com.dreamthought.saaa.adapters.retrieval;

import com.dreamthought.saaa.adapters.neo4j.Neo4jEvidenceGraph;
import com.dreamthought.saaa.adapters.neo4j.SmallRyeNeo4jConfigSource;
import com.dreamthought.saaa.adapters.sqlite.SqliteRetrievalProjectionStore;
import com.dreamthought.saaa.adapters.langchain4j.LangChain4jEmbeddingAdapter;
import com.dreamthought.saaa.adapters.langchain4j.SmallRyeEmbeddingEndpointConfigSource;
import com.dreamthought.saaa.adapters.git.GitRepositoryRevision;
import com.dreamthought.saaa.deterministic.CachedSemanticEmbeddingModel;
import com.dreamthought.saaa.deterministic.EmbeddingEvidenceSearch;
import com.dreamthought.saaa.deterministic.EvidenceRetriever;
import com.dreamthought.saaa.deterministic.HybridEvidenceRetriever;
import com.dreamthought.saaa.domain.RetrievalConfig;
import com.dreamthought.saaa.domain.RetrievalMode;
import com.dreamthought.saaa.domain.RetrievalProvenance;
import com.dreamthought.saaa.domain.ProjectionStatus;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class LocalRetrievalFactory {
    private LocalRetrievalFactory() { }

    public static EvidenceRetriever forMode(RetrievalMode mode, Path repositoryRoot) {
        Path root = GitRepositoryRevision.root(repositoryRoot);
        if (mode == RetrievalMode.NONE) {
            return EvidenceRetriever.none(RetrievalConfig.defaults().id(),
                    LocalEvolutionaryMemoryFactory.policy().id());
        }
        var projectionStore = new SqliteRetrievalProjectionStore(
                root.resolve(".saaa/retrieval.sqlite"));
        RetrievalConfig retrievalConfig = RetrievalConfig.defaults().withMemoryPolicyId(
                LocalEvolutionaryMemoryFactory.policy().id());
        com.dreamthought.saaa.deterministic.SemanticEmbeddingModel embeddingModel = null;
        if (mode == RetrievalMode.VECTOR || mode == RetrievalMode.HYBRID) {
            var embeddingConfig = new SmallRyeEmbeddingEndpointConfigSource().load();
            embeddingModel = LangChain4jEmbeddingAdapter.openAiCompatible(embeddingConfig);
            retrievalConfig = retrievalConfig.withEmbeddingModelId(embeddingConfig.modelId());
        }
        RetrievalConfig resolvedConfig = retrievalConfig;
        var resolvedEmbeddingModel = embeddingModel;
        return query -> {
            if (query.mode() != mode) {
                throw new IllegalStateException(
                        "configured retrieval treatment " + mode + " does not match query treatment " + query.mode());
            }
            try (var graph = Neo4jEvidenceGraph.connect(
                    new SmallRyeNeo4jConfigSource().load(root))) {
                requireCurrentProjection(mode, query.repositoryRevision(),
                        GitRepositoryRevision.workingTree(root), graph.status());
                var search = resolvedEmbeddingModel == null
                        ? graph
                        : new EmbeddingEvidenceSearch(graph, graph,
                                new CachedSemanticEmbeddingModel(resolvedEmbeddingModel, projectionStore));
                var bundle = new HybridEvidenceRetriever(search, resolvedConfig, projectionStore)
                        .retrieve(query);
                projectionStore.record(fingerprint(query.semanticText()), RetrievalProvenance.from(bundle));
                return bundle;
            }
        };
    }

    static void requireCurrentProjection(
            RetrievalMode mode,
            String queryRevision,
            String workingTreeRevision,
            ProjectionStatus status
    ) {
        if (!queryRevision.equals(workingTreeRevision)) {
            throw new IllegalStateException(
                    "retrieval query revision " + queryRevision
                            + " does not match current repository revision " + workingTreeRevision
                            + "; reconstruct the query before " + mode + " retrieval; retrieval treatment was not changed");
        }
        String projectionRevision = status.repositoryRevision().orElse("<not indexed>");
        if (!projectionRevision.equals(queryRevision)) {
            throw new IllegalStateException(
                    "Neo4j projection revision " + projectionRevision
                            + " does not match repository revision " + queryRevision
                            + "; run saaa-index update before " + mode
                            + " retrieval; retrieval treatment was not changed");
        }
    }

    private static String fingerprint(String value) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
