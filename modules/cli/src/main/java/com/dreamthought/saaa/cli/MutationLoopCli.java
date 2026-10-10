package com.dreamthought.saaa.cli;

import java.util.concurrent.Callable;
import java.io.PrintWriter;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "saaa",
        mixinStandardHelpOptions = true,
        version = "self-adapting-agentic-architecture 0.1.0-SNAPSHOT",
        description = "Experimental workflow mutation and fitness evaluation CLI.",
        subcommands = {EvolveCommand.class, IndexCommand.class, RetrieveCommand.class,
                BenchmarkCommand.class, ReinflateCommand.class, McpCommand.class, SaCommand.class}
)
public final class MutationLoopCli implements Callable<Integer> {
    public static void main(String[] args) {
        int exitCode = commandLine().execute(args);
        System.exit(exitCode);
    }

    /**
     * Builds the top-level command with one execution-error policy for every subcommand. A bad
     * option value is a {@code ParameterException}, which picocli renders as it renders a parse
     * error: one line, usage, exit 2. Anything else that escapes a command is a runtime failure:
     * one {@code error:} line, exit 1, no stack trace. The split is by exception type at the point
     * of throw, not by root cause, so an {@code IllegalArgumentException} raised deep inside a run
     * is never presented to the user as a mistyped flag.
     */
    public static CommandLine commandLine() {
        var commandLine = new CommandLine(new MutationLoopCli());
        commandLine.setExecutionExceptionHandler((exception, command, parseResult) -> {
            PrintWriter err = command.getErr();
            err.printf("error: %s%n", messageOf(rootCause(exception)));
            err.flush();
            return 1;
        });
        return commandLine;
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static String messageOf(Throwable failure) {
        // Stripped because messages relayed from a subprocess, such as Git's, end in a newline
        // that would otherwise print as a stray blank line after the one error line.
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? failure.getClass().getSimpleName()
                : failure.getMessage().strip();
    }

    @Override
    public Integer call() {
        System.out.println("mutation fitness loop orchestration available; real adapters are being added incrementally");
        return 0;
    }
}
