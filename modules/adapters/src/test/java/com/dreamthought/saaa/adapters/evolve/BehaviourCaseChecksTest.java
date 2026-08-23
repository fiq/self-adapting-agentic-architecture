package com.dreamthought.saaa.adapters.evolve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dreamthought.saaa.adapters.checks.CommandCheckRunner.CommandCheck;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** CHG-022. Repeated runs re-run one script under distinct names so each result is attributable. */
final class BehaviourCaseChecksTest {
    @Test
    void repeatedRunsShareTheCanonicalCommand() {
        var canonical = BehaviourCaseChecks.forCases(List.of("unit_tests_pass"), Path.of("toy"));

        var repeated = BehaviourCaseChecks.withRepeatedRuns(
                canonical, List.of("unit_tests_pass"), 3);

        assertThat(repeated).extracting(CommandCheck::name)
                .as("each run is separately attributable in the evidence")
                .containsExactly("unit_tests_pass", "unit_tests_pass.run2", "unit_tests_pass.run3");
        assertThat(repeated).extracting(CommandCheck::command)
                .as("a repeat re-runs the same script; a different command would measure something else")
                .containsOnly(canonical.get(0).command());
    }

    @Test
    void probesAreNotRepeated() {
        var checks = BehaviourCaseChecks.forCases(
                List.of("unit_tests_pass", "no_network_call"), Path.of("toy"));

        var repeated = BehaviourCaseChecks.withRepeatedRuns(checks, List.of("unit_tests_pass"), 2);

        assertThat(repeated).extracting(CommandCheck::name)
                .as("probes already grade, so repeating them would change what their fraction means")
                .containsExactly("unit_tests_pass", "no_network_call", "unit_tests_pass.run2");
    }

    @Test
    void aCaseMayNotBeNamedForARepeatedRun() {
        assertThatThrownBy(() -> BehaviourCaseChecks.forCases(
                        List.of("unit_tests_pass.run2"), Path.of("toy")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repeated-run suffix");
    }

    @Test
    void oneRunAddsNothing() {
        var canonical = BehaviourCaseChecks.forCases(List.of("unit_tests_pass"), Path.of("toy"));

        assertThat(BehaviourCaseChecks.withRepeatedRuns(canonical, List.of("unit_tests_pass"), 1))
                .isEqualTo(canonical);
    }
}
