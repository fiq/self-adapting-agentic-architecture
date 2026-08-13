package com.dreamthought.saaa.adapters.acp;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.math.BigDecimal;
import com.dreamthought.saaa.domain.ResourceBudget;

/** Configuration for one ACP agent subprocess. */
public record AcpAgentConfig(String command, List<String> arguments) {
    public AcpAgentConfig {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command must not be blank");
        }
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        arguments.forEach(argument -> {
            if (argument == null || argument.isBlank()) {
                throw new IllegalArgumentException("arguments must not contain blanks");
            }
        });
    }

    public static AcpAgentConfig fromApplicationConfig() {
        String command = firstNonBlank(System.getProperty("saaa.acp.command"),
                System.getenv("SAAA_ACP_COMMAND"));
        if (command == null) {
            throw new IllegalStateException("ACP profile requires saaa.acp.command or SAAA_ACP_COMMAND");
        }
        String rawArguments = firstNonBlank(System.getProperty("saaa.acp.args"),
                System.getenv("SAAA_ACP_ARGS"));
        List<String> arguments = rawArguments == null
                ? List.of()
                : Arrays.stream(rawArguments.trim().split("\\s+"))
                        .filter(value -> !value.isBlank()).toList();
        return new AcpAgentConfig(command, arguments);
    }

    public static ResourceBudget defaultBudget() {
        return new ResourceBudget(
                longSetting("SAAA_ACP_INPUT_TOKENS", 120_000),
                longSetting("SAAA_ACP_OUTPUT_TOKENS", 16_000),
                decimalSetting("SAAA_ACP_CREDITS", BigDecimal.ONE),
                longSetting("SAAA_ACP_WALL_CLOCK_MILLIS", 120_000),
                (int) longSetting("SAAA_ACP_RETRIES", 0));
    }

    private static long longSetting(String name, long fallback) {
        String value = firstNonBlank(System.getProperty(name.toLowerCase().replace('_', '.')),
                System.getenv(name));
        if (value == null) return fallback;
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0 || (name.endsWith("RETRIES") && parsed > Integer.MAX_VALUE)) {
                throw new IllegalArgumentException(name + " must be a non-negative bounded integer");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be an integer", exception);
        }
    }

    private static BigDecimal decimalSetting(String name, BigDecimal fallback) {
        String value = firstNonBlank(System.getProperty(name.toLowerCase().replace('_', '.')),
                System.getenv(name));
        if (value == null) return fallback;
        try {
            BigDecimal parsed = new BigDecimal(value);
            if (parsed.signum() < 0) throw new IllegalArgumentException(name + " must not be negative");
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a decimal", exception);
        }
    }

    private static String firstNonBlank(String... values) {
        return Arrays.stream(values).filter(value -> value != null && !value.isBlank()).findFirst().orElse(null);
    }
}
