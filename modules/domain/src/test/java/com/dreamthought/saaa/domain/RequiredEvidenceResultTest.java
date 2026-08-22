package com.dreamthought.saaa.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

final class RequiredEvidenceResultTest {
    @Test
    void recordsAnObservedOutcomeForOneDeclaredEvidenceId() {
        var result = RequiredEvidenceResult.passed("failing_case_reproduced", "reproduced in 1 case");

        assertThat(result.evidenceId()).isEqualTo("failing_case_reproduced");
        assertThat(result.passed()).isTrue();
        assertThat(result.diagnostic()).isEqualTo("reproduced in 1 case");
    }

    @Test
    void recordsAFailureWithItsDiagnostic() {
        var result = RequiredEvidenceResult.failed("regression_case_added", "no new case found");

        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostic()).isEqualTo("no new case found");
    }

    @Test
    void rejectsABlankEvidenceIdSoAnOutcomeCannotBeUnattributable() {
        assertThatThrownBy(() -> RequiredEvidenceResult.passed(" ", "any"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evidenceId");
    }

    @Test
    void rejectsABlankDiagnosticSoAFailureAlwaysStatesWhy() {
        assertThatThrownBy(() -> RequiredEvidenceResult.failed("regression_case_added", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("diagnostic");
    }
}
