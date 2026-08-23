package com.dreamthought.saaa.cli;

import com.dreamthought.saaa.adapters.evolve.EvolveRunRequest;
import com.dreamthought.saaa.adapters.evolve.EvolveRunner;
import com.dreamthought.saaa.adapters.git.GitRealizationInspector;
import com.dreamthought.saaa.deterministic.EvolutionReporter;
import com.dreamthought.saaa.deterministic.RetrievalAblationRunner;
import com.dreamthought.saaa.domain.CheckStatus;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.RetrievalAblationTask;
import com.dreamthought.saaa.domain.RetrievalAttemptMetrics;
import com.dreamthought.saaa.domain.RetrievalMode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "saaa-ablate", description = "Run bounded experimental comparisons.",
        subcommands = BenchmarkCommand.RetrievalAblation.class)
public final class BenchmarkCommand implements Callable<Integer> {
    @Override public Integer call() { return 0; }

    @Command(name = "retrieval",
            description = "Run the same corpus under NONE, VECTOR, GRAPH and HYBRID retrieval.")
    static final class RetrievalAblation implements Callable<Integer> {
        @Option(names = "--experiment-id", required = true)
        String experimentId;

        @Option(names = "--corpus", required = true,
                description = "TSV: id, target_folder, profile, workflow_file, max_lines, baseline_fitness, behaviour_cases, task")
        Path corpus;

        @Option(names = "--attempts", defaultValue = "1")
        int attempts;

        @Option(names = "--mode", split = ",", defaultValue = "NONE,VECTOR,GRAPH,HYBRID")
        List<RetrievalMode> modes;

        @Spec CommandSpec spec;

        @Override
        public Integer call() {
            Map<String, CorpusEntry> entries = readCorpus(corpus);
            List<RetrievalAblationTask> tasks = entries.values().stream()
                    .map(entry -> new RetrievalAblationTask(entry.id(), entry.task(), entry.baselineFitness()))
                    .toList();
            var runner = new RetrievalAblationRunner((task, mode, attempt) -> runAttempt(
                    entries.get(task.id()), task, mode, attempt));
            var report = runner.run(experimentId, tasks, modes, attempts);
            spec.commandLine().getOut().print(render(report));
            spec.commandLine().getOut().flush();
            return 0;
        }

        private RetrievalAttemptMetrics runAttempt(
                CorpusEntry entry, RetrievalAblationTask task, RetrievalMode mode, int attempt) {
            String runId = safe(experimentId) + "-" + safe(task.id()) + "-" + mode.name().toLowerCase()
                    + "-" + attempt;
            long started = System.nanoTime();
            com.dreamthought.saaa.adapters.evolve.EvolveRunResult result;
            try {
                result = new EvolveRunner().run(new EvolveRunRequest(
                        entry.targetFolder(), entry.profile(), entry.workflowFile(), entry.behaviourCases(),
                        entry.maxLines(), mode, entry.task(), Optional.of(runId)), EvolutionReporter.NO_OP);
            } catch (IllegalStateException exception) {
                if (exception.getMessage() == null
                        || !exception.getMessage().startsWith("mutation validation failed:")) {
                    throw exception;
                }
                long wall = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                return new RetrievalAttemptMetrics(
                        task.id(), mode, attempt, false, task.baselineFitness(), 0,
                        0, 1, 0, 0, 0, 0, 0, 0, wall, 0, 0, 0, 0, 0, 0,
                        "retrieval-config-v1", "lineage-novelty-v1");
            }
            var fitness = result.fitnessResult();
            int failed = (int) fitness.evidence().checks().stream()
                    .filter(check -> check.status() != CheckStatus.PASSED).count();
            var realization = new GitRealizationInspector().inspect(fitness.candidate());
            int inputTokens = token(result.proposerEvidence(), "model_input_tokens");
            int outputTokens = token(result.proposerEvidence(), "model_output_tokens");
            var retrieval = result.retrieval();
            return new RetrievalAttemptMetrics(
                    task.id(), mode, attempt, fitness.decision() == FitnessDecision.PROMOTE,
                    fitness.fitnessScore().rawMagnitude().doubleValue(),
                    fitness.fitnessScore().rawMagnitude().doubleValue() - task.baselineFitness(),
                    failed, 0, failed, realization.filesChanged(), 0, inputTokens, outputTokens, 0,
                    result.wallClockMillis(), result.retrievalMillis(),
                    retrieval.diagnostics().graphNodesConsidered(), retrieval.capsules().size(),
                    retrieval.estimatedTokens(), retrieval.diagnostics().cacheHits(),
                    retrieval.diagnostics().cacheMisses(), retrieval.configurationId(), retrieval.memoryPolicyId());
        }

        private static int token(Optional<com.dreamthought.saaa.domain.ProposerEvidence> evidence, String name) {
            return evidence.map(value -> value.attributes().get(name)).map(Integer::parseInt).orElse(0);
        }
    }

    private static Map<String, CorpusEntry> readCorpus(Path path) {
        try {
            Path absolute = path.toAbsolutePath().normalize();
            Path base = absolute.getParent();
            var result = new LinkedHashMap<String, CorpusEntry>();
            int lineNumber = 0;
            for (String line : Files.readAllLines(absolute)) {
                lineNumber++;
                if (line.isBlank() || line.startsWith("#") || line.startsWith("id\t")) continue;
                String[] fields = line.split("\t", -1);
                if (fields.length != 8) {
                    throw new IllegalArgumentException("ablation corpus line " + lineNumber + " needs 8 tab-separated fields");
                }
                Path target = Path.of(fields[1]);
                if (!target.isAbsolute()) target = base.resolve(target).normalize();
                var entry = new CorpusEntry(fields[0], target, fields[2], fields[3], Integer.parseInt(fields[4]),
                        Double.parseDouble(fields[5]), List.of(fields[6].split(",")), fields[7]);
                if (result.put(entry.id(), entry) != null) {
                    throw new IllegalArgumentException("duplicate ablation task id: " + entry.id());
                }
            }
            if (result.isEmpty()) throw new IllegalArgumentException("ablation corpus is empty");
            return result;
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to read ablation corpus", exception);
        }
    }

    private static String render(com.dreamthought.saaa.domain.RetrievalAblationReport report) {
        var out = new StringBuilder("retrieval_ablation:\n  experiment_id: ")
                .append(report.experimentId()).append("\n  improvement_claim: not_evaluated_by_reporter\n  treatments:\n");
        report.treatments().forEach(summary -> out.append("    - mode: ").append(summary.mode())
                .append("\n      attempts: ").append(summary.attempts())
                .append("\n      accepted: ").append(summary.accepted())
                .append("\n      acceptance_per_attempt: ").append(summary.acceptancePerAttempt())
                .append("\n      mean_attempts_to_first_accepted: ").append(summary.meanAttemptsToFirstAccepted())
                .append("\n      best_fitness: ").append(summary.bestFitness())
                .append("\n      accepted_fitness_improvement: ").append(summary.acceptedFitnessImprovement())
                .append("\n      mutation_cost: ").append(summary.mutationCost())
                .append("\n      accepted_improvement_per_cost: ").append(summary.acceptedImprovementPerCost())
                .append("\n      context_tokens_per_accepted: ").append(summary.contextTokensPerAcceptedCandidate())
                .append("\n"));
        out.append("  attempts:\n");
        report.attempts().forEach(value -> out.append("    - task: ").append(value.taskId())
                .append("\n      mode: ").append(value.mode()).append("\n      attempt: ").append(value.attempt())
                .append("\n      accepted: ").append(value.accepted())
                .append("\n      fitness: ").append(value.fitness())
                .append("\n      fitness_delta: ").append(value.fitnessDelta())
                .append("\n      invariant_failures: ").append(value.hardGateFailures())
                .append("\n      invalid_mutations: ").append(value.invalidMutations())
                .append("\n      regressions: ").append(value.regressions())
                .append("\n      files_changed: ").append(value.filesChanged())
                .append("\n      symbols_changed: ").append(value.symbolsChanged())
                .append("\n      model_input_tokens: ").append(value.modelInputTokens())
                .append("\n      model_output_tokens: ").append(value.modelOutputTokens())
                .append("\n      provider_cost: ").append(value.providerCost())
                .append("\n      wall_clock_millis: ").append(value.wallClockMillis())
                .append("\n      retrieval_millis: ").append(value.retrievalMillis())
                .append("\n      graph_nodes_considered: ").append(value.graphNodesConsidered())
                .append("\n      evidence_count: ").append(value.evidenceCount())
                .append("\n      evidence_context_tokens: ").append(value.evidenceContextTokens())
                .append("\n      cache_hits: ").append(value.cacheHits())
                .append("\n      cache_misses: ").append(value.cacheMisses())
                .append("\n      retrieval_configuration_id: ").append(value.retrievalConfigurationId())
                .append("\n      memory_policy_id: ").append(value.memoryPolicyId())
                .append("\n"));
        return out.toString();
    }

    private static String safe(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9_-]", "-");
    }

    private record CorpusEntry(
            String id, Path targetFolder, String profile, String workflowFile, int maxLines,
            double baselineFitness, List<String> behaviourCases, String task) { }
}
