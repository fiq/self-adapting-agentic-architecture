package com.dreamthought.saaa.cli;

import com.dreamthought.saaa.adapters.evolve.EvolveRunRequest;
import com.dreamthought.saaa.adapters.evolve.EvolveRunner;
import com.dreamthought.saaa.benchmarks.JmhBenchmarkRunner;
import com.dreamthought.saaa.deterministic.BenchmarkRunner;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import com.dreamthought.saaa.domain.MutationContract;
import com.dreamthought.saaa.adapters.evolve.OperatorContracts;
import java.util.List;
import java.util.Set;
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

    @Option(names = "--operator",
            description = "Declare a mutation contract for this run, by operator wire name such as "
                    + "repair or simplify. The operator's own required evidence is always included, "
                    + "and each id names a check that must exist and pass. With no --operator given "
                    + "the run behaves exactly as before and declares nothing.")
    private String operator;

    @Option(names = "--required-evidence",
            description = "Extra required evidence id to declare beyond the operator's own. "
                    + "Repeatable. Lower snake_case, because it is recorded as a "
                    + "subject.invariant.<id> audit key, and it must name a check that runs.")
    private List<String> requiredEvidence = new ArrayList<>();

    @Option(names = "--safety-probe",
            description = "Check whose outcome contributes to the behavioural-safety objective. "
                    + "Repeatable. Probes grade rather than gate: a failing probe lowers the score "
                    + "and does not discard, and a probe that did not run counts as failed. A safety "
                    + "property that must hold belongs in --required-evidence, which discards.")
    private List<String> safetyProbes = new ArrayList<>();

    @Option(names = "--held-out-case",
            description = "Behaviour case that runs and scores but decides no gate. Repeatable. "
                    + "A failing held-out case lowers task_success without discarding the candidate, "
                    + "so two candidates that both pass every required case remain comparable. A "
                    + "held-out case that did not run counts as failed, and one that must hold "
                    + "belongs in --behaviour-case, which gates.")
    private List<String> heldOutCases = new ArrayList<>();

    @Option(names = "--reliability-runs",
            description = "How many times to run each behaviour case. The first run gates as before; "
                    + "the rest grade the reliability objective as a pass fraction, so a flaky "
                    + "candidate scores lower instead of being discarded. Default 1, which leaves "
                    + "the objective at its previous value.")
    private int reliabilityRuns = 1;

    @Option(names = "--run-id",
            description = "Name this run, which is what keeps one run's candidate worktrees, "
                    + "branches and ids apart from another's. Defaults to a timestamp, so repeat "
                    + "runs on the same folder no longer collide. Give one when you want the "
                    + "worktree paths to be predictable, and expect a rerun with the same id to "
                    + "fail on the worktree its first run left behind.")
    private String runId;

    @Spec
    private CommandSpec spec;

    /**
     * A misconfigured budget does not fail loudly on its own: {@code budgetScore} skips any benchmark
     * without a matching budget, so a misspelled or unpaired name leaves {@code cost_latency_budget}
     * at 1.0 and the run promotes as though it had been measured. Because that silently changes a
     * recorded promotion decision, the configuration is rejected before the loop starts rather than
     * absorbed.
     */
    private void requireCoherentBenchmarkConfiguration() {
        if (benchmarks.isEmpty() && !benchmarkBudgets.isEmpty()) {
            throw new IllegalArgumentException(
                    "--benchmark-budget given without --benchmark, so nothing would be measured: "
                            + String.join(", ", benchmarkBudgets.keySet()));
        }
        var unmatched = new java.util.LinkedHashSet<>(benchmarkBudgets.keySet());
        unmatched.removeAll(benchmarks.keySet());
        if (!unmatched.isEmpty()) {
            throw new IllegalArgumentException(
                    "--benchmark-budget names benchmarks that were not requested with --benchmark: "
                            + String.join(", ", unmatched));
        }
        var unbudgeted = new java.util.LinkedHashSet<>(benchmarks.keySet());
        unbudgeted.removeAll(benchmarkBudgets.keySet());
        if (!unbudgeted.isEmpty()) {
            throw new IllegalArgumentException(
                    "--benchmark given without a matching --benchmark-budget, so it would be measured "
                            + "and then ignored by scoring: " + String.join(", ", unbudgeted));
        }
        benchmarkBudgets.forEach((name, budget) -> {
            if (budget == null || budget.isNaN() || budget.isInfinite() || budget <= 0.0) {
                throw new IllegalArgumentException(
                        "--benchmark-budget for " + name + " must be a positive finite number, got " + budget);
            }
        });
    }

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        requireCoherentBenchmarkConfiguration();
        BenchmarkRunner benchmarkRunner = benchmarks.isEmpty()
                ? candidate -> List.of()
                : new JmhBenchmarkRunner(benchmarks.entrySet().stream()
                        .map(entry -> new JmhBenchmarkRunner.BenchmarkDefinition(entry.getKey(), entry.getValue()))
                        .toList());
        if (operator == null && !requiredEvidence.isEmpty()) {
            throw new IllegalArgumentException(
                    "--required-evidence given without --operator, so nothing would declare it and "
                            + "nothing would gate on it: " + String.join(", ", requiredEvidence));
        }
        var contract = operator == null
                ? Optional.<MutationContract>empty()
                : Optional.of(OperatorContracts.declare(operator, requiredEvidence, workflowFile));
        contract.ifPresent(declared -> out.printf("  contract   %s requires %s%n",
                declared.operator().wireName(), String.join(", ", declared.requiredEvidence())));

        var result = new EvolveRunner(benchmarkRunner).run(
                new EvolveRunRequest(
                        targetFolder, profile, workflowFile, behaviourCases, maxLines, retrievalMode, task,
                        Optional.ofNullable(runId), benchmarkBudgets, contract, List.copyOf(safetyProbes),
                        reliabilityRuns, List.copyOf(heldOutCases)),
                new ConsoleReporter(out));
        out.printf("  journal    %s%n", result.journalPath());
        out.flush();
        return 0;
    }
}
