package com.dreamthought.saaa.deterministic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dreamthought.saaa.domain.EvolutionTargetKind;
import com.dreamthought.saaa.domain.HarnessSessionStatus;
import com.dreamthought.saaa.domain.HarnessSessionTarget;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class HarnessSessionStateMachineTest {
    @Test
    void recordsExplicitTargetAndRoute() {
        var session = new HarnessSessionStateMachine("fixture");

        var selected = session.selectTarget(new HarnessSessionTarget(
                EvolutionTargetKind.HARNESS_WORKFLOW, Path.of("target")));
        var routed = session.selectRoute("acp");

        assertThat(selected.target()).isPresent();
        assertThat(routed.route()).isEqualTo("acp");
        assertThat(routed.status()).isEqualTo(HarnessSessionStatus.ACTIVE);
    }

    @Test
    void rejectsCommandsAfterClose() {
        var session = new HarnessSessionStateMachine("fixture");
        session.close();

        assertThatThrownBy(() -> session.selectRoute("acp"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("session is closed");
    }
}
