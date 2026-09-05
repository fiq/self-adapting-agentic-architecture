package com.dreamthought.saaa.deterministic;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * What distinguishes one candidate's worktree, branch and id from another's.
 *
 * <p>Candidate names were derived from the workflow id and the mutation id alone, and the workspace
 * refuses to overwrite an existing worktree, so a deterministic proposer asked twice produced the
 * same name and the second run failed outright — {@code RISK-003}. A generation turns that from
 * occasional into certain, because every candidate in one shares everything the old name was built
 * from.
 *
 * <p>Two parts, and each answers a different collision. The run id separates one run from the next.
 * The candidate position separates candidates inside one generation. Together they are also
 * readable: a worktree directory says which run it came from and which candidate it was, which is
 * what someone looking at {@code .worktrees/} after a failed run actually needs.
 *
 * <p>Derived from the clock rather than randomly, so the name a run produced can be reproduced from
 * the run's own record. Milliseconds, because a scripted pair of runs inside one second is a real
 * thing and a name that collides is the defect this exists to remove.
 */
public record CandidateNamespace(String runId) {
    private static final DateTimeFormatter RUN_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

    public CandidateNamespace {
        runId = Objects.requireNonNull(runId, "runId");
        if (runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }
    }

    /** A run id for a run starting now, unique to the millisecond and readable as a timestamp. */
    public static CandidateNamespace forRun(Instant at) {
        return new CandidateNamespace("run-" + RUN_STAMP.format(Objects.requireNonNull(at, "at")));
    }

    /** A run id the caller chose, used as given. */
    public static CandidateNamespace forRunId(String runId) {
        return new CandidateNamespace(runId);
    }

    /**
     * The namespace for one candidate in this run, one-based.
     *
     * <p>Prefixed with the run id rather than replacing it, so every worktree from one generation
     * sorts together and a stray one is traceable to the run that made it.
     */
    public String forCandidate(int position) {
        if (position < 1) {
            throw new IllegalArgumentException(
                    "candidate position is one-based, got " + position);
        }
        return runId + "-c" + position;
    }
}
