package io.github.selfadaptingagenticarchitecture.core;

public record WorkflowGraph(String id, String version, String definition) {
    public WorkflowGraph {
        id = Require.nonBlank(id, "id");
        version = Require.nonBlank(version, "version");
        definition = Require.nonBlank(definition, "definition");
    }
}
