package com.dreamthought.saaa.deterministic;

import static com.dreamthought.saaa.domain.CheckEvidence.failed;
import static com.dreamthought.saaa.domain.CheckEvidence.passed;
import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.BenchmarkEvidence;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.RealizationSummary;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * CHG-021. The behavioural-safety objective was the literal 1.0, so it could not distinguish any two
 * candidates. It is now the pass fraction of the safety probes an operator declared.
 *
 * <p>These probes are graded, not gating. A critical safety property belongs in the contract's
 * required evidence, where a failure discards; a probe that merely lowers the score must never be the
 * only thing standing between an unsafe candidate and promotion.
 */
final class BehaviouralSafetyScoreTest {
    @Test
    void isThePassFractionOfTheDeclaredProbes() {
        var result = score(Set.of("no_authority_language", "no_network_call", "no_secret_read"),
                passed("no_authority_language", "clean"),
                failed("no_network_call", "opened a socket"),
                passed("no_secret_read", "clean"));

        assertThat(result.objectives())
                .as("two of three declared probes passed")
                .containsEntry("subject.objective.behavioral_safety", 2.0 / 3.0);
    }

    @Test
    void aProbeThatDidNotRunCountsAsFailed() {
        var result = score(Set.of("no_authority_language", "never_ran"),
                passed("no_authority_language", "clean"));

        assertThat(result.objectives())
                .as("absent evidence is not passing evidence, the rule the gates already apply")
                .containsEntry("subject.objective.behavioral_safety", 0.5);
    }

    @Test
    void staysAtOneWhenNoProbesAreDeclared() {
        var result = score(Set.of(), passed("unrelated", "fine"));

        assertThat(result.objectives())
                .as("a run declaring no probes is unchanged, so existing callers keep their score")
                .containsEntry("subject.objective.behavioral_safety", 1.0);
    }

    @Test
    void aFailingProbeLowersTheScoreWithoutDiscarding() {
        var result = score(Set.of("no_network_call"), failed("no_network_call", "opened a socket"));

        assertThat(result.objectives())
                .containsEntry("subject.objective.behavioral_safety", 0.0);
        assertThat(result.decision())
                .as("probes grade; only declared required evidence gates, so this must not discard here")
                .isEqualTo(com.dreamthought.saaa.domain.FitnessDecision.PROMOTE);
    }

    @Test
    void anUndeclaredCheckDoesNotCountTowardsTheFraction() {
        var result = score(Set.of("no_network_call"),
                passed("no_network_call", "clean"),
                failed("some_other_check", "unrelated failure"));

        assertThat(result.objectives())
                .as("only declared probes are scored, so an unrelated failure cannot lower safety")
                .containsEntry("subject.objective.behavioral_safety", 1.0);
    }

    @Test
    void aFailingRunOfAProbeIsNotMaskedByAPassingOne() {
        // The failing entry comes first deliberately: listed last, a last-write-wins merge returns
        // the same answer and the assertion proves nothing.
        var result = score(Set.of("flaky_probe"),
                failed("flaky_probe", "failed one run"),
                passed("flaky_probe", "passed another"));

        assertThat(result.objectives())
                .as("a probe that failed once has not shown the property holds")
                .containsEntry("subject.objective.behavioral_safety", 0.0);
    }

    /**
     * The probe is withheld from the gate, never from the record. Everything durable downstream —
     * SQLite rows, ledger envelopes, evolutionary memory, MCP responses — is written from
     * {@code result.evidence()}, so a probe dropped there could lower a score with nothing left to
     * explain why. The CLI transcript cannot catch this: the console prints checks the journal
     * cached before scoring, so it shows the probe whether or not the result kept it.
     */
    @Test
    void aWithheldProbeStaysInTheRecordedEvidence() {
        var result = score(Set.of("no_network_call"), failed("no_network_call", "opened a socket"));

        assertThat(result.evidence().checks())
                .as("the audit trail must be able to name the probe that lowered the score")
                .anySatisfy(check -> {
                    assertThat(check.name()).isEqualTo("no_network_call");
                    assertThat(check.summary()).isEqualTo("opened a socket");
                });
        assertThat(result.decision())
                .as("keeping the probe in the evidence must not let it reach the gate")
                .isEqualTo(com.dreamthought.saaa.domain.FitnessDecision.PROMOTE);
    }

    private static com.dreamthought.saaa.domain.FitnessResult score(
            Set<String> probes, CheckEvidence... checks) {
        var all = new java.util.ArrayList<CheckEvidence>(List.of(checks));
        all.add(passed("publish-guard", "ok"));
        var scorer = new PhenotypeBridgeScorer(
                candidate -> new RealizationSummary(1, 8),
                new ScoringConfig(Set.of("publish-guard"), 80, Map.of(), probes));
        return scorer.score(
                new Candidate("c-1", "MUT-1", "candidate/MUT-1", Path.of(".worktrees/c"), "abc1234"),
                new EvaluationEvidence(all, List.<BenchmarkEvidence>of(),
                        Instant.parse("2026-08-23T00:00:00Z")),
                Optional.empty());
    }
}
