package com.dreamthought.saaa.domain;

/** Identifies both the implementation under evolution and the SAAA process executing it. */
public record EvolutionContext(
        String subjectRepositoryId,
        String subjectRepositoryRevision,
        String processRepositoryId,
        String processRepositoryRevision
) {
    public EvolutionContext {
        subjectRepositoryId = Require.nonBlank(subjectRepositoryId, "subjectRepositoryId");
        subjectRepositoryRevision = Require.nonBlank(subjectRepositoryRevision, "subjectRepositoryRevision");
        processRepositoryId = Require.nonBlank(processRepositoryId, "processRepositoryId");
        processRepositoryRevision = Require.nonBlank(processRepositoryRevision, "processRepositoryRevision");
    }
}
