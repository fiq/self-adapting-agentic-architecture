package com.dreamthought.saaa.domain;

/**
 * Search controls recorded before realization for {@code hill-climb} and {@code exploratory-leap}.
 * The posture describes where the search starts and how far it may travel; it never decides fitness.
 */
public record SearchPosture(String parentCandidateId, String objectiveFocus, String expectedDelta, String riskBudget) {
    public SearchPosture {
        parentCandidateId = Require.nonBlank(parentCandidateId, "parentCandidateId");
        objectiveFocus = Require.nonBlank(objectiveFocus, "objectiveFocus");
        expectedDelta = Require.nonBlank(expectedDelta, "expectedDelta");
        riskBudget = Require.nonBlank(riskBudget, "riskBudget");
    }
}
