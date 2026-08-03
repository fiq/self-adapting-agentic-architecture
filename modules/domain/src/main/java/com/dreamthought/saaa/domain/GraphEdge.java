package com.dreamthought.saaa.domain;

import java.util.Objects;

public record GraphEdge(String sourceId, RelationshipType type, String targetId, String reason) {
    public GraphEdge {
        sourceId = Require.nonBlank(sourceId, "sourceId");
        type = Objects.requireNonNull(type, "type");
        targetId = Require.nonBlank(targetId, "targetId");
        reason = Require.nonBlank(reason, "reason");
    }
}
