package com.dreamthought.saaa.adapters.mcp;

import com.dreamthought.saaa.adapters.evolve.EvolveRunRequest;
import com.dreamthought.saaa.adapters.evolve.EvolveRunResult;
import com.dreamthought.saaa.adapters.evolve.EvolveRunner;
import com.dreamthought.saaa.deterministic.EvolutionReporter;
import java.util.Map;
import java.util.Objects;

public final class EvolveMcpTool {
    private final ToolRunner runner;
    private final EvolveMcpResponseSerializer serializer;

    public EvolveMcpTool(EvolveRunner runner) {
        this((request, reporter) -> runner.run(request, reporter),
                new EvolveMcpResponseSerializer(new EvolveMcpResponseScrubber()));
    }

    EvolveMcpTool(ToolRunner runner, EvolveMcpResponseSerializer serializer) {
        this.runner = Objects.requireNonNull(runner, "runner");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
    }

    public ToolResponse call(Map<String, Object> arguments) {
        try {
            EvolveMcpRequest request = EvolveMcpRequest.fromArguments(arguments);
            return ToolResponse.success(serializer.serialize(runner.run(request.toRunRequest(), EvolutionReporter.NO_OP)));
        } catch (RuntimeException exception) {
            return ToolResponse.error(serializer.serializeError(exception));
        }
    }

    @FunctionalInterface
    interface ToolRunner {
        EvolveRunResult run(EvolveRunRequest request, EvolutionReporter reporter);
    }

    public record ToolResponse(boolean error, String json) {
        static ToolResponse success(String json) {
            return new ToolResponse(false, json);
        }

        static ToolResponse error(String json) {
            return new ToolResponse(true, json);
        }
    }
}
