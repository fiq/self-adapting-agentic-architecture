package com.dreamthought.saaa.deterministic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CHG-026 T4. What distinguishes one candidate's worktree from another's.
 *
 * <p>RISK-003: candidate ids and worktree paths derived from the workflow and mutation id alone
 * collide, so a second run of the same mutation fails with "candidate worktree already exists"
 * instead of evaluating a new candidate. A generation makes that immediate rather than occasional,
 * because N candidates in one run share everything the old name was built from.
 */
final class CandidateNamespaceTest {

    @Test
    @DisplayName("two runs a moment apart do not share a namespace, so the second can be evaluated")
    void aRunNamespaceComesFromTheClockSoASecondRunDoesNotCollide() {
        var first = CandidateNamespace.forRun(Instant.parse("2026-09-05T14:32:01.123Z"));
        var second = CandidateNamespace.forRun(Instant.parse("2026-09-05T14:32:01.124Z"));

        assertThat(first.forCandidate(1))
                .as("this is RISK-003: without something per-run in the name, the second run of a "
                        + "deterministic proposer lands on the first run's worktree path")
                .isNotEqualTo(second.forCandidate(1));
    }

    @Test
    @DisplayName("candidates within one generation are distinguished from each other")
    void everyCandidateInAGenerationGetsItsOwnNamespace() {
        var namespace = CandidateNamespace.forRun(Instant.parse("2026-09-05T14:32:01.123Z"));

        assertThat(namespace.forCandidate(1)).isNotEqualTo(namespace.forCandidate(2));
        assertThat(namespace.forCandidate(1))
                .as("one run's candidates stay grouped, so a human can see which run a worktree "
                        + "belongs to without opening it")
                .startsWith(namespace.runId());
    }

    @Test
    @DisplayName("an explicitly supplied run id is used as given")
    void anExplicitRunIdIsUsedAsGiven() {
        var namespace = CandidateNamespace.forRunId("bench-hybrid-001");

        assertThat(namespace.runId()).isEqualTo("bench-hybrid-001");
        assertThat(namespace.forCandidate(1)).startsWith("bench-hybrid-001");
    }

    @Test
    @DisplayName("a candidate position is one-based, matching how the generation counts them")
    void aCandidatePositionBelowOneIsRejected() {
        var namespace = CandidateNamespace.forRun(Instant.parse("2026-09-05T14:32:01.123Z"));

        assertThatThrownBy(() -> namespace.forCandidate(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one-based");
    }

    @Test
    @DisplayName("the namespace survives the path-segment sanitising a worktree name goes through")
    void aNamespaceContainsOnlyCharactersSafeInAPathSegment() {
        var namespace = CandidateNamespace.forRun(Instant.parse("2026-09-05T14:32:01.123Z"));

        assertThat(namespace.forCandidate(11))
                .as("GitCandidateWorkspace sanitises this into a directory name, and a namespace "
                        + "that changed under sanitising would stop being the thing that was recorded")
                .matches("[A-Za-z0-9][A-Za-z0-9-]*");
    }
}
