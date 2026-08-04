package com.dreamthought.saaa.adapters.neo4j;

import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.nio.file.Path;
import com.dreamthought.saaa.adapters.git.GitRepositoryRevision;
import com.dreamthought.saaa.domain.RepositoryRole;

public final class SmallRyeNeo4jConfigSource {
    private final SmallRyeConfig config;

    public SmallRyeNeo4jConfigSource() {
        this(new SmallRyeConfigBuilder().addDefaultSources().build());
    }

    SmallRyeNeo4jConfigSource(SmallRyeConfig config) {
        this.config = config;
    }

    public Neo4jConfig load(Path repositoryRoot) {
        return new Neo4jConfig(
                optional("saaa.neo4j.uri", "bolt://127.0.0.1:7687"),
                optional("saaa.neo4j.user", "neo4j"),
                required("saaa.neo4j.password", "SAAA_NEO4J_PASSWORD"),
                optional("saaa.neo4j.database", "neo4j"),
                optional("saaa.neo4j.repository-id", GitRepositoryRevision.repositoryId(repositoryRoot)),
                config.getOptionalValue("saaa.neo4j.repository-role", RepositoryRole.class)
                        .orElse(RepositoryRole.SUBJECT));
    }

    public Neo4jConfig load(Path repositoryRoot, RepositoryRole role) {
        Neo4jConfig resolved = load(repositoryRoot);
        return new Neo4jConfig(resolved.uri(), resolved.user(), resolved.password(), resolved.database(),
                resolved.repositoryId(), role);
    }

    private String optional(String name, String fallback) {
        return config.getOptionalValue(name, String.class).orElse(fallback);
    }

    private String required(String name, String environmentName) {
        return config.getOptionalValue(name, String.class)
                .orElseThrow(() -> new IllegalStateException(
                        "missing required Neo4j configuration property: " + name
                                + " (environment: " + environmentName + ")"));
    }
}
