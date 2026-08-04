package com.dreamthought.saaa.domain;

import java.util.Objects;

public record EvidenceLink(RelationshipType relationship, String targetId, String description) {
    public EvidenceLink {
        relationship = Objects.requireNonNull(relationship, "relationship");
        targetId = Require.nonBlank(targetId, "targetId");
        description = Require.nonBlank(description, "description");
    }
}
