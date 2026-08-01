package com.dreamthought.saaa.domain;

import java.util.regex.Pattern;

/**
 * A promoted candidate is a branch pointer, never a merge target. Keeping the full ref in a value
 * type means callers cannot smuggle in {@code main} or another integration branch as a parameter.
 */
public record CandidateBranchRef(String value) {
    private static final String PREFIX = "refs/heads/candidate/";
    private static final Pattern CANDIDATE_REF_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    public CandidateBranchRef {
        value = Require.nonBlank(value, "value");
        if (!value.startsWith(PREFIX)) {
            throw new IllegalArgumentException("candidate branch ref must start with " + PREFIX);
        }
        String refName = value.substring(PREFIX.length());
        if (!CANDIDATE_REF_NAME.matcher(refName).matches()) {
            throw new IllegalArgumentException(
                    "candidate branch ref must end with a single safe branch segment: " + value);
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
