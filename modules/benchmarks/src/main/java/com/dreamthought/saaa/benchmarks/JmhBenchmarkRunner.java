package com.dreamthought.saaa.benchmarks;

import com.dreamthought.saaa.deterministic.BenchmarkRunner;
import com.dreamthought.saaa.domain.BenchmarkEvidence;
import com.dreamthought.saaa.domain.Candidate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import org.openjdk.jmh.runner.options.VerboseMode;

public final class JmhBenchmarkRunner implements BenchmarkRunner {
    private final List<BenchmarkDefinition> benchmarks;

    public JmhBenchmarkRunner(List<BenchmarkDefinition> benchmarks) {
        this.benchmarks = List.copyOf(Objects.requireNonNull(benchmarks, "benchmarks"));
        if (this.benchmarks.isEmpty()) {
            throw new IllegalArgumentException("benchmarks must not be empty");
        }
    }

    @Override
    public List<BenchmarkEvidence> runBenchmarks(Candidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        List<BenchmarkEvidence> evidence = new ArrayList<>();
        for (BenchmarkDefinition benchmark : benchmarks) {
            evidence.addAll(runBenchmark(benchmark));
        }
        return List.copyOf(evidence);
    }

    private static List<BenchmarkEvidence> runBenchmark(BenchmarkDefinition benchmark) {
        try {
            Collection<RunResult> results = new Runner(optionsFor(benchmark)).run();
            if (results.isEmpty()) {
                throw new IllegalStateException("JMH produced no results for " + benchmark.includeRegex());
            }
            return results.stream()
                    .map(result -> toEvidence(benchmark, results, result))
                    .toList();
        } catch (RunnerException exception) {
            throw new IllegalStateException("JMH benchmark failed: " + benchmark.includeRegex(), exception);
        }
    }

    private static Options optionsFor(BenchmarkDefinition benchmark) {
        return new OptionsBuilder()
                .include(benchmark.includeRegex())
                .mode(Mode.Throughput)
                .warmupIterations(0)
                .measurementIterations(1)
                .measurementTime(TimeValue.milliseconds(1))
                .forks(0)
                .shouldFailOnError(true)
                .shouldDoGC(false)
                .verbosity(VerboseMode.SILENT)
                .build();
    }

    private static BenchmarkEvidence toEvidence(
            BenchmarkDefinition benchmark,
            Collection<RunResult> results,
            RunResult result
    ) {
        String name = benchmark.name();
        if (results.size() > 1) {
            name = name + ":" + result.getParams().getBenchmark();
        }
        return BenchmarkEvidence.measurement(
                name,
                result.getPrimaryResult().getScore(),
                result.getPrimaryResult().getScoreUnit()
        );
    }

    public record BenchmarkDefinition(String name, String includeRegex) {
        public BenchmarkDefinition {
            name = requireNonBlank(name, "name");
            includeRegex = requireNonBlank(includeRegex, "includeRegex");
        }
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
