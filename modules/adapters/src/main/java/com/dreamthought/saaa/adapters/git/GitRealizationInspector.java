package com.dreamthought.saaa.adapters.git;

import com.dreamthought.saaa.deterministic.RealizationInspector;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.RealizationSummary;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;

/** Measures the candidate commit against its first parent with {@code git diff --numstat}. */
public final class GitRealizationInspector implements RealizationInspector {
    @Override
    public RealizationSummary inspect(Candidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        String output = run(
                candidate,
                "git", "diff", "--numstat", candidate.commitSha() + "^", candidate.commitSha());

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

    private static String run(Candidate candidate, String... command) {
        try {
            Process process = new ProcessBuilder(List.of(command))
                    .directory(candidate.worktreePath().toFile())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes());
            if (process.waitFor() != 0) {
                throw new IllegalStateException(
                        "git diff failed for candidate " + candidate.id() + ": " + output);
            }
            return output;
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to inspect candidate " + candidate.id(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted inspecting candidate " + candidate.id(), exception);
        }
    }
}
