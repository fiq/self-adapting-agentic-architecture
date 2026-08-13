package com.dreamthought.saaa.adapters.acp;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class AcpAgentConfigTest {
    @AfterEach
    void clearProperties() {
        System.clearProperty("saaa.acp.command");
        System.clearProperty("saaa.acp.args");
        System.clearProperty("saaa.acp.input.tokens");
        System.clearProperty("saaa.acp.output.tokens");
        System.clearProperty("saaa.acp.credits");
        System.clearProperty("saaa.acp.wall.clock.millis");
        System.clearProperty("saaa.acp.retries");
    }

    @Test
    void readsCommandArgumentsAndResourceBudgetFromExplicitProperties() {
        System.setProperty("saaa.acp.command", "local-agent");
        System.setProperty("saaa.acp.args", "--mode proposal --profile local");
        System.setProperty("saaa.acp.input.tokens", "12");
        System.setProperty("saaa.acp.output.tokens", "7");
        System.setProperty("saaa.acp.credits", "0.25");
        System.setProperty("saaa.acp.wall.clock.millis", "900");
        System.setProperty("saaa.acp.retries", "1");

        var config = AcpAgentConfig.fromApplicationConfig();

        assertThat(config.command()).isEqualTo("local-agent");
        assertThat(config.arguments()).containsExactly("--mode", "proposal", "--profile", "local");
        assertThat(AcpAgentConfig.defaultBudget().inputTokensRemaining()).isEqualTo(12);
        assertThat(AcpAgentConfig.defaultBudget().outputTokensRemaining()).isEqualTo(7);
        assertThat(AcpAgentConfig.defaultBudget().creditsRemaining()).isEqualByComparingTo(new BigDecimal("0.25"));
        assertThat(AcpAgentConfig.defaultBudget().wallClockMillisRemaining()).isEqualTo(900);
        assertThat(AcpAgentConfig.defaultBudget().retriesRemaining()).isEqualTo(1);
    }
}
