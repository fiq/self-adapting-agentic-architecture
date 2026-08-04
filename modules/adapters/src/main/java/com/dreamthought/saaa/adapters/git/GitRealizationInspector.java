package com.dreamthought.saaa.adapters.git;

import com.dreamthought.saaa.deterministic.RealizationInspector;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.RealizationSummary;
import java.util.Objects;

/** Measures the candidate commit against its first parent through the JGit API. */
public final class GitRealizationInspector implements RealizationInspector {
    @Override
    public RealizationSummary inspect(Candidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        var changes = JGitCandidateDiff.inspect(candidate);
        return new RealizationSummary(
                changes.size(), changes.stream().mapToInt(JGitCandidateDiff.CommittedChange::linesChanged).sum());
    }
}
