package com.dreamthought.saaa.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * One generation as something that can be written down: which run produced it, what it ranked, and
 * when it was recorded.
 *
 * <p>The ranking, the winner, the shared scoring fingerprint, the evidence count and the spread are
 * all derived from {@link RankedGeneration} rather than stored here, so a record cannot disagree
 * with the generation it describes. This adds only the two things the generation itself cannot know.
 *
 * <p>The run id is what makes the record findable later. Candidate ids already carry it, so a reader
 * who has a worktree or a branch can get back to the generation it came from.
 */
public record GenerationRecord(String runId, RankedGeneration generation, Instant recordedAt) {
    public GenerationRecord {
        runId = Require.nonBlank(runId, "runId");
        generation = Objects.requireNonNull(generation, "generation");
        recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
    }
}
