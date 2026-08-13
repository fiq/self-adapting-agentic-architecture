package com.dreamthought.saaa.adapters.acp;

import java.util.List;
import java.util.Objects;

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
}
