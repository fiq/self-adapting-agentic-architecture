package com.dreamthought.saaa.cli;

import com.dreamthought.saaa.adapters.evolve.EvolveRunRequest;
import com.dreamthought.saaa.adapters.evolve.EvolveRunner;
import com.dreamthought.saaa.benchmarks.JmhBenchmarkRunner;
import com.dreamthought.saaa.deterministic.BenchmarkRunner;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import com.dreamthought.saaa.domain.RetrievalMode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        name = "saaa-evolve",
        description = "Run one mutation evaluation against a target folder."
)
public final class EvolveCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "Folder containing the workflow being evolved.")
    private Path targetFolder;

    @Option(names = "--profile", defaultValue = "fixture", description = "Proposer profile name.")
    private String profile;

    @Option(names = "--workflow-file", defaultValue = "workflow.txt",
            description = "File inside the target folder that is being evolved.")
    private String workflowFile;

    @Option(names = "--behaviour-case", required = true,
            description = "Name of a check that is required behaviour and hard-gates promotion.")
    private List<String> behaviourCases;

    @Option(names = "--max-lines", defaultValue = "80",
            description = "Change budget that parsimony is scored against.")
    private int maxLines;

    @Option(names = "--retrieval", defaultValue = "NONE",
            description = "Retrieval treatment: ${COMPLETION-CANDIDATES}.")
    private RetrievalMode retrievalMode;

    @Option(names = "--task", defaultValue = "Improve the target while preserving all declared behaviour cases",
            description = "Mutation goal used to retrieve evidence before proposal.")
    private String task;

    @Option(names = "--benchmark",
            description = "Benchmark to measure: name=JMH include-regex, resolved against classes in "
                    + ":benchmarks. Repeatable. With none given, cost_latency_budget stays at its 1.0 "
                    + "starting point.")
    private Map<String, String> benchmarks = new LinkedHashMap<>();

    @Option(names = "--benchmark-budget",
            description = "Benchmark name to its budget in the benchmark's own unit, scored by "
                    + "cost_latency_budget as worst budget/measured over the run's benchmarks. Repeatable.")
    private Map<String, Double> benchmarkBudgets = new LinkedHashMap<>();

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        BenchmarkRunner benchmarkRunner = benchmarks.isEmpty()
                ? candidate -> List.of()
                : new JmhBenchmarkRunner(benchmarks.entrySet().stream()
                        .map(entry -> new JmhBenchmarkRunner.BenchmarkDefinition(entry.getKey(), entry.getValue()))
                        .toList());
        var result = new EvolveRunner(benchmarkRunner).run(
                new EvolveRunRequest(
                        targetFolder, profile, workflowFile, behaviourCases, maxLines, retrievalMode, task,
                        Optional.empty(), benchmarkBudgets),
                new ConsoleReporter(out));
        out.printf("  journal    %s%n", result.journalPath());
        out.flush();
        return 0;
    }
}
