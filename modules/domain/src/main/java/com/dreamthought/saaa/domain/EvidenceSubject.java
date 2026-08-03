package com.dreamthought.saaa.domain;

public record EvidenceSubject(String stableId, String logicalId, String kind) {
    public EvidenceSubject {
        stableId = Require.nonBlank(stableId, "stableId");
        logicalId = Require.nonBlank(logicalId, "logicalId");
        kind = Require.nonBlank(kind, "kind");
    }
}
