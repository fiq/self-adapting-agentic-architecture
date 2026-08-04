package com.dreamthought.saaa.adapters.git;

import com.dreamthought.saaa.domain.Candidate;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

/** Shared API-based view of one candidate commit relative to its first parent. */
final class JGitCandidateDiff {
    private JGitCandidateDiff() { }

    static List<CommittedChange> inspect(Candidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        try (var repository = GitRepositoryRevision.open(candidate.worktreePath());
             var walk = new RevWalk(repository);
             var formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            var commit = walk.parseCommit(repository.resolve(candidate.commitSha()));
            if (commit.getParentCount() == 0) return List.of();
            var parent = walk.parseCommit(commit.getParent(0));
            formatter.setRepository(repository);
            formatter.setDetectRenames(true);
            var changes = new java.util.ArrayList<CommittedChange>();
            for (DiffEntry entry : formatter.scan(parent.getTree(), commit.getTree())) {
                if (bookkeepingOnly(entry)) continue;
                int lines = formatter.toFileHeader(entry).toEditList().stream()
                        .mapToInt(edit -> edit.getLengthA() + edit.getLengthB()).sum();
                changes.add(new CommittedChange(entry.getOldPath(), entry.getNewPath(), lines));
            }
            return List.copyOf(changes);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to inspect candidate diff for " + candidate.id(), exception);
        }
    }

    private static boolean bookkeepingOnly(DiffEntry entry) {
        return List.of(entry.getOldPath(), entry.getNewPath()).stream()
                .filter(path -> !DiffEntry.DEV_NULL.equals(path))
                .allMatch(path -> path.equals(".saaa") || path.startsWith(".saaa/"));
    }

    record CommittedChange(String oldPath, String newPath, int linesChanged) { }
}
