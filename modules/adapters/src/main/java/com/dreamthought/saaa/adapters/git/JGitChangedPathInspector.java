package com.dreamthought.saaa.adapters.git;

import com.dreamthought.saaa.deterministic.ChangedPathInspector;
import com.dreamthought.saaa.domain.Candidate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.eclipse.jgit.diff.DiffEntry;

/** Reads committed candidate paths through JGit; bookkeeping under .saaa is not a realization. */
public final class JGitChangedPathInspector implements ChangedPathInspector {
    @Override
    public List<String> inspect(Candidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        return JGitCandidateDiff.inspect(candidate).stream()
                .flatMap(entry -> Stream.of(entry.oldPath(), entry.newPath()))
                .filter(path -> !DiffEntry.DEV_NULL.equals(path))
                .distinct()
                .sorted()
                .toList();
    }
}
