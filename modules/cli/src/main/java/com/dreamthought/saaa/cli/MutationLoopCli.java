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
            err.printf("error: %s%n", describe(exception));
            err.flush();
            return 1;
        });
        return commandLine;
    }

    /**
     * The outer message says what failed and the inner one says why, so both are kept, outermost
     * first: "failed to read ablation corpus: /missing.tsv" rather than only the path. A message
     * that repeats the one before it, as a wrapper copying its cause's message does, is dropped.
     * Stripped because messages relayed from a subprocess, such as Git's, end in a newline that
     * would otherwise print as a stray blank line. The walk is bounded, so a cause cycle of any
     * length cannot hang the error path.
     */
    static String describe(Throwable failure) {
        var parts = new java.util.ArrayList<String>();
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        for (Throwable cause = failure; cause != null && seen.add(cause) && seen.size() <= 16;
                cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message == null || message.isBlank()) {
                continue;
            }
            message = message.strip();
            if (parts.isEmpty() || !parts.get(parts.size() - 1).contains(message)) {
                parts.add(message);
            }
        }
        return parts.isEmpty() ? failure.getClass().getSimpleName() : String.join(": ", parts);
    }

    @Override
    public Integer call() {
        System.out.println("mutation fitness loop orchestration available; real adapters are being added incrementally");
        return 0;
    }
}
