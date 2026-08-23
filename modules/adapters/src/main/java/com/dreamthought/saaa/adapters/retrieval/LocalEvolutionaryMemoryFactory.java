package com.dreamthought.saaa.adapters.retrieval;

import com.dreamthought.saaa.adapters.neo4j.Neo4jEvidenceGraph;
import com.dreamthought.saaa.adapters.neo4j.SmallRyeNeo4jConfigSource;
import com.dreamthought.saaa.adapters.sqlite.SqliteEvolutionaryMemoryStore;
import com.dreamthought.saaa.adapters.experiments.GitExperimentEnvelopeStore;
import com.dreamthought.saaa.adapters.experiments.WikiExperimentProjection;
import com.dreamthought.saaa.deterministic.EvolutionaryMemoryArchive;
import com.dreamthought.saaa.deterministic.EvolutionaryMemoryPolicy;
import com.dreamthought.saaa.deterministic.EvolutionaryMemoryStore;
import com.dreamthought.saaa.deterministic.LineageNoveltyMemoryPolicy;
import com.dreamthought.saaa.domain.RetrievalMode;
import com.dreamthought.saaa.adapters.git.GitRepositoryRevision;
import java.nio.file.Path;

/** Persists memory locally; graph write-back is immediate only when that treatment already requires Neo4j. */
public final class LocalEvolutionaryMemoryFactory {
    private LocalEvolutionaryMemoryFactory() { }

    public static EvolutionaryMemoryStore forMode(RetrievalMode mode, Path repositoryRoot) {
        Path root = GitRepositoryRevision.root(repositoryRoot);
        var sqlite = archive(root);
        var envelopes = new GitExperimentEnvelopeStore(root);
        var wiki = new WikiExperimentProjection(root);
        EvolutionaryMemoryPolicy policy = policy();
        if (mode == RetrievalMode.NONE) {
            return record -> {
                envelopes.append(record);
                sqlite.append(record);
                wiki.render(envelopes.records());
            };
        }
        return record -> {
            envelopes.append(record);
            sqlite.append(record);
            wiki.render(envelopes.records());
            try (var graph = Neo4jEvidenceGraph.connect(
                    new SmallRyeNeo4jConfigSource().load(root))) {
                // The record just appended is the run in flight, so its fingerprint is what
                // "current" means here; the working set is inflated around this evaluation.
                graph.replaceEvolutionaryMemory(
                        policy.select(sqlite.records(), record.scoringFingerprint()), policy.id());
            }
        };
    }

    public static EvolutionaryMemoryArchive archive(Path repositoryRoot) {
        Path root = GitRepositoryRevision.root(repositoryRoot);
        var sqlite = new SqliteEvolutionaryMemoryStore(root.resolve(".saaa/experiments.sqlite"));
        var envelopes = new GitExperimentEnvelopeStore(root);
        var records = envelopes.records();
        records.forEach(sqlite::append);
        new WikiExperimentProjection(root).render(records);
        return sqlite;
    }

    public static EvolutionaryMemoryPolicy policy() {
        return new LineageNoveltyMemoryPolicy(
                new SmallRyeEvolutionaryMemoryPolicyConfigSource().load());
    }
}
