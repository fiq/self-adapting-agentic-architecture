package com.dreamthought.saaa.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

final class FitnessSignalIdTest {
    @Test
    void rendersScopeForceAndNameAsACanonicalString() {
        assertThat(FitnessSignalId.invariant("deterministic_checks").canonical())
                .isEqualTo("subject.invariant.deterministic_checks");
        assertThat(FitnessSignalId.objective("parsimony").canonical())
                .isEqualTo("subject.objective.parsimony");
        assertThat(FitnessSignalId.processInvariant("layer_boundaries").canonical())
                .isEqualTo("process.invariant.layer_boundaries");
    }

    @Test
    void parsesItsOwnCanonicalForm() {
        FitnessSignalId id = FitnessSignalId.parse("subject.objective.task_success");

        assertThat(id.scope()).isEqualTo(FitnessScope.SUBJECT);
        assertThat(id.force()).isEqualTo(FitnessForce.OBJECTIVE);
        assertThat(id.name()).isEqualTo("task_success");
        assertThat(id).isEqualTo(FitnessSignalId.objective("task_success"));
    }

    /**
     * The role must come from the type, never from the name. A signal named to look like a gate
     * is still whatever its force says it is, which is the property a prefix convention cannot give.
     */
    @Test
    void aNameThatLooksLikeAnotherRoleDoesNotChangeTheRole() {
        FitnessSignalId id = FitnessSignalId.objective("invariant");

        assertThat(id.force()).isEqualTo(FitnessForce.OBJECTIVE);
        assertThat(id.canonical()).isEqualTo("subject.objective.invariant");
    }

    @Test
    void acceptsLegacyKeysSoPersistedResultsCanBeReEmittedCanonically() {
        assertThat(FitnessSignalId.parse("hard_gate_deterministic_checks").canonical())
                .isEqualTo("subject.invariant.deterministic_checks");
        assertThat(FitnessSignalId.parse("task_success").canonical())
                .isEqualTo("subject.objective.task_success");
    }

    @Test
    void rejectsAMalformedCanonicalString() {
        assertThatThrownBy(() -> FitnessSignalId.parse("subject.invariant"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FitnessSignalId.parse("nowhere.invariant.x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANameThatIsNotASafeSegment() {
        assertThatThrownBy(() -> FitnessSignalId.objective("has.dot"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FitnessSignalId.objective("has space"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FitnessSignalId.objective(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
