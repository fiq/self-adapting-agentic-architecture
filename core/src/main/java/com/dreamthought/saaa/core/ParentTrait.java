package com.dreamthought.saaa.core;

/**
 * One evidence-backed technique carried forward from an evaluated parent candidate into a
 * conceptual crossover child contract. The trait is prose; the evidence id anchors it to a
 * recorded fitness result rather than to a raw diff hunk.
 */
public record ParentTrait(String parentCandidateId, String trait, String evidenceId) {
    public ParentTrait {
        parentCandidateId = Require.nonBlank(parentCandidateId, "parentCandidateId");
        trait = Require.nonBlank(trait, "trait");
        evidenceId = Require.nonBlank(evidenceId, "evidenceId");
    }
}
