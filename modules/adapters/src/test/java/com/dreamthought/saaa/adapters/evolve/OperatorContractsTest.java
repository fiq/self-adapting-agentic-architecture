package com.dreamthought.saaa.adapters.evolve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CHG-019 T4. Declaring an operator commits the run to that operator's required evidence, which is
 * the point: RISK-002's example is a repair contract realized without the evidence it declared.
 */
final class OperatorContractsTest {
    @Test
    void aRepairContractRequiresTheEvidenceThatOperatorDeclares() {
        var contract = OperatorContracts.declare("repair", List.of(), "workflow.txt");

        assertThat(contract.requiredEvidence())
                .as("declaring repair commits the run to exactly what RISK-002 describes")
                .contains("failing_case_reproduced", "regression_case_added", "unit_tests_pass");
    }

    @Test
    void extraDeclaredEvidenceIsAddedWithoutDroppingTheOperatorDefaults() {
        var contract = OperatorContracts.declare("repair", List.of("extra_probe_passed"), "workflow.txt");

        assertThat(contract.requiredEvidence())
                .contains("failing_case_reproduced", "extra_probe_passed")
                .doesNotHaveDuplicates();
    }

    @Test
    void anUnknownOperatorIsRejected() {
        assertThatThrownBy(() -> OperatorContracts.declare("rewrite-everything", List.of(), "workflow.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported mutation operator");
    }

    @Test
    void anOperatorNeedingASearchPostureIsRejectedRatherThanSilentlyIncomplete() {
        assertThatThrownBy(() -> OperatorContracts.declare("hill-climb", List.of(), "workflow.txt"))
                .as("a contract missing its posture would fail validation later with a worse message")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("search posture");
    }

    @Test
    void theDeclaredContractPassesTheValidator() {
        assertThat(OperatorContracts.declare("simplify", List.of(), "workflow.txt").operator().wireName())
                .as("declare only returns a contract the validator accepted")
                .isEqualTo("simplify");
    }
}
