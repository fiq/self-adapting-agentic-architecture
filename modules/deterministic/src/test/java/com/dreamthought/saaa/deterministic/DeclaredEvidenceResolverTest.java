package com.dreamthought.saaa.deterministic;

import static com.dreamthought.saaa.domain.CheckEvidence.failed;
import static com.dreamthought.saaa.domain.CheckEvidence.passed;
import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.EvaluationEvidence;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CHG-019 T1. A declared required_evidence id names a check that must exist and pass, so the
 * declaration is enforced against evidence the run already collects.
 */
final class DeclaredEvidenceResolverTest {
    private final DeclaredEvidenceResolver resolver = new DeclaredEvidenceResolver();

    @Test
    void resolvesADeclaredIdToThePassingCheckOfThatName() {
        var results = resolver.resolve(List.of("regression_case_added"), evidence(
                passed("regression_case_added", "one case added")));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.evidenceId()).isEqualTo("regression_case_added");
            assertThat(result.passed()).isTrue();
            assertThat(result.diagnostic()).contains("one case added");
        });
    }

    @Test
    void resolvesADeclaredIdToAFailureWhenTheCheckOfThatNameFailed() {
        var results = resolver.resolve(List.of("regression_case_added"), evidence(
                failed("regression_case_added", "no new case found")));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.passed()).isFalse();
            assertThat(result.diagnostic()).contains("no new case found");
        });
    }

    @Test
    void producesAFailingResultWhenNoCheckOfThatNameRan() {
        var results = resolver.resolve(List.of("failing_case_reproduced"), evidence(
                passed("something_else", "unrelated")));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.evidenceId()).isEqualTo("failing_case_reproduced");
            assertThat(result.passed())
                    .as("absent evidence is not passing evidence")
                    .isFalse();
            assertThat(result.diagnostic())
                    .as("the diagnostic must say why, so the discard is explicable")
                    .contains("no check named");
        });
    }

    @Test
    void aCheckTheContractDidNotDeclareProducesNoResult() {
        var results = resolver.resolve(List.of("regression_case_added"), evidence(
                passed("regression_case_added", "added"),
                passed("unrelated_check", "also passed")));

        assertThat(results)
                .as("only declared ids are resolved, so an undeclared check cannot satisfy one")
                .hasSize(1);
    }

    @Test
    void aFailingCheckWinsWhenTwoChecksShareADeclaredName() {
        // The failing check comes first deliberately: listed last, a last-wins bug returns the same
        // answer and the test proves nothing.
        var results = resolver.resolve(List.of("flaky"), evidence(
                failed("flaky", "failed the other time"),
                passed("flaky", "passed this time")));

        assertThat(results).singleElement().satisfies(result -> assertThat(result.passed())
                .as("a passing run cannot mask a failing one for the same declared id")
                .isFalse());
    }

    private static EvaluationEvidence evidence(com.dreamthought.saaa.domain.CheckEvidence... checks) {
        return new EvaluationEvidence(List.of(checks), List.of(), Instant.parse("2026-08-22T00:00:00Z"));
    }
}
