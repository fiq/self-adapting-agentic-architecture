package com.dreamthought.saaa.cli;

import java.util.concurrent.Callable;
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
        int exitCode = new CommandLine(new MutationLoopCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        System.out.println("mutation fitness loop orchestration available; real adapters are being added incrementally");
        return 0;
    }
}
