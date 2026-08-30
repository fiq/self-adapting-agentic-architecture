package com.dreamthought.saaa.domain;

public record UnevaluatedCandidate(String candidateId, String reason) {
    public UnevaluatedCandidate {
        candidateId = Require.nonBlank(candidateId, "candidateId");
        reason = Require.nonBlank(reason, "reason");
    }
}
