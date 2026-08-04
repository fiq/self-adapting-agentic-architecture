package com.dreamthought.saaa.adapters.neo4j;

import java.util.Objects;
import com.dreamthought.saaa.domain.RepositoryRole;

public record Neo4jConfig(
        String uri,
        String user,
        String password,
        String database,
        String repositoryId,
        RepositoryRole repositoryRole
) {
    public Neo4jConfig {
        uri = requireNonBlank(uri, "uri");
        user = requireNonBlank(user, "user");
        password = requireNonBlank(password, "password");
        database = requireNonBlank(database, "database");
        repositoryId = requireNonBlank(repositoryId, "repositoryId");
        repositoryRole = Objects.requireNonNull(repositoryRole, "repositoryRole");
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
