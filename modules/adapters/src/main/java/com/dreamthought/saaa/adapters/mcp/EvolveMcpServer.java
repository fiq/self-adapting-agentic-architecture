package com.dreamthought.saaa.adapters.mcp;

import com.dreamthought.saaa.adapters.evolve.EvolveRunner;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class EvolveMcpServer {
    static final String TOOL_NAME = "saaa_evolve";

    private final EvolveMcpTool evolveTool;

    public EvolveMcpServer(EvolveMcpTool evolveTool) {
        this.evolveTool = Objects.requireNonNull(evolveTool, "evolveTool");
    }

    public static AutoCloseable startStdio() {
        return new StdioServerHandle(new EvolveMcpServer(new EvolveMcpTool(new EvolveRunner())).buildStdioServer());
    }

    public McpSyncServer buildStdioServer() {
        var transport = new StdioServerTransportProvider(McpJsonDefaults.getMapper());
        return McpServer.sync(transport)
                .serverInfo("self-adapting-agentic-architecture", "0.1.0-SNAPSHOT")
                .validateToolInputs(true)
                .toolCall(toolDefinition(), this::callSdkTool)
                .build();
    }

    public EvolveMcpTool.ToolResponse callEvolve(Map<String, Object> arguments) {
        return evolveTool.call(arguments);
    }

    McpSchema.Tool toolDefinition() {
        return McpSchema.Tool.builder(TOOL_NAME, EvolveMcpRequest.inputSchema())
                .title("Evolve")
                .description("Run one bounded SAAA mutation evaluation against a target folder.")
                .outputSchema(outputSchema())
                .build();
    }

    McpSchema.CallToolResult callSdkTool(McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
        var response = callEvolve(request.arguments());
        var builder = McpSchema.CallToolResult.builder()
                .addTextContent(response.json())
                .isError(response.error());
        if (!response.error()) {
            builder.structuredContent(McpJsonDefaults.getMapper(), response.json());
        }
        return builder.build();
    }

    private static Map<String, Object> outputSchema() {
        var candidate = new LinkedHashMap<String, Object>();
        candidate.put("type", "object");
        candidate.put("additionalProperties", false);
        candidate.put("required", List.of("id", "mutationId", "branchName", "worktreePath", "commitSha"));
        candidate.put("properties", Map.of(
                "id", Map.of("type", "string"),
                "mutationId", Map.of("type", "string"),
                "branchName", Map.of("type", "string"),
                "worktreePath", Map.of("type", "string"),
                "commitSha", Map.of("type", "string")));

        var properties = new LinkedHashMap<String, Object>();
        properties.put("candidate", candidate);
        properties.put("evidence", evidenceSchema());
        properties.put("objectives", Map.of("type", "object", "additionalProperties", Map.of("type", "number")));
        properties.put("aggregateScore", Map.of("type", "number"));
        properties.put("aggregateScoreDisplay", Map.of("type", "string"));
        properties.put("decision", Map.of("type", "string", "enum", List.of("PROMOTE", "DISCARD")));
        properties.put("journalPath", Map.of("type", "string"));

        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", List.of(
                "candidate", "evidence", "objectives", "aggregateScore", "aggregateScoreDisplay", "decision", "journalPath"));
        schema.put("properties", properties);
        return schema;
    }

    private static Map<String, Object> evidenceSchema() {
        var check = new LinkedHashMap<String, Object>();
        check.put("type", "object");
        check.put("additionalProperties", false);
        check.put("required", List.of("name", "status", "summary"));
        check.put("properties", Map.of(
                "name", Map.of("type", "string"),
                "status", Map.of("type", "string", "enum", List.of("PASSED", "FAILED")),
                "summary", Map.of("type", "string")));

        var benchmark = new LinkedHashMap<String, Object>();
        benchmark.put("type", "object");
        benchmark.put("additionalProperties", false);
        benchmark.put("required", List.of("name", "value", "unit"));
        benchmark.put("properties", Map.of(
                "name", Map.of("type", "string"),
                "value", Map.of("type", "number"),
                "unit", Map.of("type", "string")));

        var evidence = new LinkedHashMap<String, Object>();
        evidence.put("type", "object");
        evidence.put("additionalProperties", false);
        evidence.put("required", List.of("evaluatedAt", "checks", "benchmarks"));
        evidence.put("properties", Map.of(
                "evaluatedAt", Map.of("type", "string"),
                "checks", Map.of("type", "array", "items", check),
                "benchmarks", Map.of("type", "array", "items", benchmark),
                "truncated", Map.of("type", "boolean"),
                "truncationReason", Map.of("type", "string")));
        return evidence;
    }

    private record StdioServerHandle(McpSyncServer server) implements AutoCloseable {
        private StdioServerHandle {
            Objects.requireNonNull(server, "server");
        }

        @Override
        public void close() {
            server.closeGracefully();
        }
    }
}
