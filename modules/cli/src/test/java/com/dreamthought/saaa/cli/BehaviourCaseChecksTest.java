package com.dreamthought.saaa.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dreamthought.saaa.adapters.evolve.BehaviourCaseChecks;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class BehaviourCaseChecksTest {
    @Test
    void mapsEveryCaseNameToItsOwnScriptRelativeToTheGitRoot() {
        var checks = BehaviourCaseChecks.forCases(List.of("workflow-check", "second-check"), Path.of("toy"));

        assertThat(checks).hasSize(2);
        assertThat(checks.get(0).name()).isEqualTo("workflow-check");
        assertThat(checks.get(0).command()).containsExactly("./toy/workflow-check.sh");
        assertThat(checks.get(1).name()).isEqualTo("second-check");
        assertThat(checks.get(1).command()).containsExactly("./toy/second-check.sh");
    }

    /**
     * The case name becomes a path segment, so a traversing name would run a script outside the
     * target folder. Reject rather than sanitize: silently rewriting the name would decouple the
     * gate name recorded in the journal from the script that actually ran.
     */
    @Test
    void rejectsCaseNamesThatWouldEscapeTheTargetFolder() {
        assertThatThrownBy(() -> BehaviourCaseChecks.forCases(List.of("../../etc/evil"), Path.of("toy")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("../../etc/evil");
    }

    /**
     * When the target folder is the Git root the relative check directory is empty. A bare
     * "workflow-check.sh" has no path separator, and execvp resolves such a program name against
     * PATH rather than the child's working directory, so the check would run whatever is on PATH
     * instead of the script inside the candidate worktree.
     */
    @Test
    void keepsCommandsWorktreeRelativeWhenTheTargetFolderIsTheGitRoot() {
        var checks = BehaviourCaseChecks.forCases(List.of("workflow-check"), Path.of(""));

        assertThat(checks.get(0).command()).containsExactly("./workflow-check.sh");
    }

    @Test
    void rejectsAnAbsoluteCheckDirectoryBecauseItWouldEscapeTheCandidateWorktree() {
        assertThatThrownBy(() -> BehaviourCaseChecks.forCases(List.of("workflow-check"), Path.of("/etc")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/etc");
    }

    @Test
    void rejectsCaseNamesDifferingOnlyByCaseBecauseOneScriptWouldSatisfyTwoCases() {
        assertThatThrownBy(() -> BehaviourCaseChecks.forCases(List.of("Foo", "foo"), Path.of("toy")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("foo");
    }

    @Test
    void rejectsDuplicateCaseNamesBecauseTheGateWouldCountOneCaseTwice() {
        assertThatThrownBy(() -> BehaviourCaseChecks.forCases(List.of("dup", "dup"), Path.of("toy")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dup");
    }
}
