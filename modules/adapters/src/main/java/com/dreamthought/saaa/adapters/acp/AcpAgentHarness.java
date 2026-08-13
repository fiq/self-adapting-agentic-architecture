package com.dreamthought.saaa.adapters.acp;

import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema;
import com.dreamthought.saaa.deterministic.AgentHarness;
import com.dreamthought.saaa.domain.AgentRequest;
import com.dreamthought.saaa.domain.AgentRunResult;
import com.dreamthought.saaa.domain.AgentRunStatus;
import com.dreamthought.saaa.domain.AgentUsage;
import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.MutationScope;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Invokes an ACP-compatible coding agent over a local stdio subprocess. */
public final class AcpAgentHarness implements AgentHarness {
    private static final int MAX_OUTPUT_CHARS = 64 * 1024;
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{.*\\}", Pattern.DOTALL);
    private static final ObjectMapper JSON = new ObjectMapper();
    private final AcpAgentConfig config;

    public AcpAgentHarness(AcpAgentConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public AgentRunResult run(AgentRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.budget().inputTokensRemaining() == 0
                || request.budget().outputTokensRemaining() == 0
                || request.budget().wallClockMillisRemaining() == 0) {
            return rejected(request, "invocation resource budget is exhausted");
        }
        long started = System.nanoTime();
        String rawOutput = "";
        var transport = new StdioAcpClientTransport(parameters());
        AcpSyncClient client = null;
        try {
            var streamedText = new StringBuilder();
            client = AcpClient.sync(transport)
                    .requestTimeout(Duration.ofMillis(request.budget().wallClockMillisRemaining()))
                    .sessionUpdateConsumer(notification -> appendText(notification, streamedText))
                    .build();
            client.initialize();
            var session = client.newSession(new AcpSchema.NewSessionRequest(
                    request.workspace().toString(), List.of()));
            String prompt = promptFor(request);
            client.prompt(new AcpSchema.PromptRequest(
                    session.sessionId(), List.of(new AcpSchema.TextContent(prompt))));
            rawOutput = streamedText.toString();
            Mutation mutation = parseMutation(rawOutput);
            return new AgentRunResult(
                    AgentRunStatus.COMPLETED,
                    request.route(),
                    Optional.of(mutation),
                    Optional.of(session.sessionId()),
                    Optional.of(digest(rawOutput)),
                    new AgentUsage(0, 0, java.math.BigDecimal.ZERO, elapsedMillis(started), 0),
                    Optional.empty());
        } catch (RuntimeException exception) {
            AgentRunStatus status = isTimeout(exception)
                    ? AgentRunStatus.TIMED_OUT : AgentRunStatus.FAILED;
            return new AgentRunResult(
                    status,
                    request.route(),
                    Optional.empty(),
                    Optional.empty(),
                    rawOutput.isBlank() ? Optional.empty() : Optional.of(digest(rawOutput)),
                    new AgentUsage(0, 0, java.math.BigDecimal.ZERO, elapsedMillis(started), 0),
                    Optional.of(safeFailure(exception)));
        } finally {
            if (client != null) {
                client.close();
            } else {
                transport.closeGracefully().block(Duration.ofSeconds(2));
            }
        }
    }

    static Mutation parseMutation(String response) {
        Matcher matcher = JSON_OBJECT.matcher(Objects.requireNonNull(response, "response"));
        if (!matcher.find()) {
            throw new IllegalArgumentException("ACP agent response did not contain a JSON object");
        }
        try {
            JsonNode root = JSON.readTree(matcher.group());
            return new Mutation(
                    required(root, "id"),
                    required(root, "summary"),
                    MutationScope.valueOf(required(root, "scope")),
                    required(root, "patch"));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("ACP agent response was not a valid mutation proposal", exception);
        }
    }

    private AgentParameters parameters() {
        return AgentParameters.builder(config.command()).args(config.arguments()).build();
    }

    private static String promptFor(AgentRequest request) {
        var baseline = request.proposal().baseline();
        return "Return exactly one JSON mutation proposal and do not approve it. Expected schema: "
                + request.expectedOutputSchema()
                + "\nAllowed capabilities: " + request.allowedCapabilities()
                + "\nBaseline id: " + baseline.id()
                + "\nBaseline version: " + baseline.version()
                + "\nBaseline definition:\n" + baseline.definition()
                + "\nTask:\n" + request.proposal().retrievalQuery().semanticText()
                + request.retrieval().map(bundle -> "\nRetrieved evidence capsule context:\n"
                        + bundle.flattenedContext()).orElse("")
                + "\nJSON fields: id, summary, scope, patch. Scope must be one of "
                + List.of(MutationScope.values());
    }

    private static String required(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("missing mutation field: " + field);
        }
        return value.asText();
    }

    private static void appendText(AcpSchema.SessionNotification notification, StringBuilder output) {
        if (notification.update() instanceof AcpSchema.AgentMessageChunk chunk
                && chunk.content() instanceof AcpSchema.TextContent text) {
            if (output.length() + text.text().length() > MAX_OUTPUT_CHARS) {
                throw new IllegalArgumentException("ACP agent output exceeded the 65536-character limit");
            }
            output.append(text.text());
        }
    }

    private static boolean isTimeout(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static AgentRunResult rejected(AgentRequest request, String reason) {
        return new AgentRunResult(AgentRunStatus.REJECTED, request.route(), Optional.empty(), Optional.empty(),
                Optional.empty(), AgentUsage.none(), Optional.of(reason));
    }

    private static String digest(String value) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static long elapsedMillis(long started) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private static String safeFailure(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        String safe = message.replaceAll("(?i)bearer\\s+\\S+", "Bearer <redacted>")
                .replaceAll("(?i)sk-[a-z0-9_-]+", "<redacted>")
                .replaceAll("[\\r\\n]+", " ");
        return safe.substring(0, Math.min(safe.length(), 512));
    }
}
