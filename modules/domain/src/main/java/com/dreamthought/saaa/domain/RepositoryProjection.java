package com.dreamthought.saaa.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record RepositoryProjection(
        String repositoryId,
        String repositoryRevision,
        String schemaVersion,
        List<EvidenceDocument> nodes,
        List<GraphEdge> edges
) {
    public RepositoryProjection {
        repositoryId = Require.nonBlank(repositoryId, "repositoryId");
        repositoryRevision = Require.nonBlank(repositoryRevision, "repositoryRevision");
        schemaVersion = Require.nonBlank(schemaVersion, "schemaVersion");
        nodes = Objects.requireNonNull(nodes, "nodes").stream()
                .sorted(Comparator.comparing(EvidenceDocument::stableId))
                .toList();
        edges = Objects.requireNonNull(edges, "edges").stream()
                .sorted(Comparator.comparing(GraphEdge::sourceId)
                        .thenComparing(edge -> edge.type().name())
                        .thenComparing(GraphEdge::targetId))
                .toList();
        long distinctNodes = nodes.stream().map(EvidenceDocument::stableId).distinct().count();
        if (distinctNodes != nodes.size()) {
            throw new IllegalArgumentException("repository projection contains duplicate stable node ids");
        }
    }
}
