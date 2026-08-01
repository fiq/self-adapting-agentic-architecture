package com.dreamthought.saaa.domain;

/**
 * A promoted candidate is a branch pointer, never a merge target. Keeping the full ref in a value
 * type means callers cannot smuggle in {@code main} or another integration branch as a parameter.
 */
public record CandidateBranchRef(String value) {
    private static final String PREFIX = "refs/heads/candidate/";

    public CandidateBranchRef {
        value = Require.nonBlank(value, "value");
        if (!value.startsWith(PREFIX)) {
            throw new IllegalArgumentException("candidate branch ref must start with " + PREFIX);
        }
        if (value.length() == PREFIX.length()) {
            throw new IllegalArgumentException("candidate branch ref must include a branch name");
        }
    }

    public static CandidateBranchRef fromCandidate(Candidate candidate) {
        String branchName = Require.nonBlank(candidate.branchName(), "candidate.branchName");
        if (branchName.startsWith("refs/heads/")) {
            return new CandidateBranchRef(branchName);
        }
        return new CandidateBranchRef("refs/heads/" + branchName);
    }
}
