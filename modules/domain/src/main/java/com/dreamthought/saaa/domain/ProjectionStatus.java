package com.dreamthought.saaa.domain;

import java.util.Objects;
import java.util.Optional;

public record ProjectionStatus(
        String repositoryId,
        Optional<String> repositoryRevision,
        Optional<String> schemaVersion,
        int nodeCount,
        int relationshipCount
) {
    public ProjectionStatus {
        repositoryId = Require.nonBlank(repositoryId, "repositoryId");
        repositoryRevision = Objects.requireNonNull(repositoryRevision, "repositoryRevision");
        schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion");
        if (nodeCount < 0 || relationshipCount < 0) {
            throw new IllegalArgumentException("projection counts must not be negative");
        }
    }
}
