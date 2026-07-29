package com.dreamthought.saaa.adapters.git;

import com.dreamthought.saaa.deterministic.RealizationInspector;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.RealizationSummary;
import java.util.Objects;

/** Measures the candidate commit against its first parent with {@code git diff --numstat}. */
public final class GitRealizationInspector implements RealizationInspector {
    @Override
    public RealizationSummary inspect(Candidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        // GitCandidateWorkspace commits .saaa/candidates/<id>.toon alongside the realized mutation
        // in the same commit; exclude that bookkeeping so only the real change is measured.
        // Both pathspecs are anchored at the repository root with :/ and (top,...) rather than left
        // relative to the working directory, so the measurement does not change if this ever runs
        // from a subdirectory of the candidate worktree.
        String output = GitCommand.run(
                        candidate.worktreePath(),
                        "diff", "--numstat", candidate.commitSha() + "^", candidate.commitSha(),
                        "--", ":/", ":(top,exclude).saaa")
                .requireSuccess("inspect candidate " + candidate.id());

        int files = 0;
        int lines = 0;
        for (String row : output.split("\n")) {
            String trimmed = row.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] columns = trimmed.split("\\s+");
            if (columns.length < 3) {
                continue;
            }
            files++;
            lines += parseCount(columns[0]) + parseCount(columns[1]);
        }
        return new RealizationSummary(files, lines);
    }

    /** Binary files report "-" instead of a count. */
    private static int parseCount(String column) {
        if ("-".equals(column)) {
            return 0;
        }
        return Integer.parseInt(column);
    }
}
