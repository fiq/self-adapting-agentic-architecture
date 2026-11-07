package com.dreamthought.saaa.domain;

/** A candidate whose committed files repeat the first candidate with that tree in this generation. */
public record DuplicateCandidate(String candidateId, String sameAsCandidateId) {
    public DuplicateCandidate {
        candidateId = Require.nonBlank(candidateId, "candidateId");
        sameAsCandidateId = Require.nonBlank(sameAsCandidateId, "sameAsCandidateId");
    }
}
