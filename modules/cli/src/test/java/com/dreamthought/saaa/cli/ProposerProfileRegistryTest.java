package com.dreamthought.saaa.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dreamthought.saaa.adapters.langchain4j.OpenAiCompatibleChatModelFactory;
import com.dreamthought.saaa.domain.MutationScope;
import com.dreamthought.saaa.domain.WorkflowGraph;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

final class ProposerProfileRegistryTest {
    private final ProposerProfileRegistry registry = new ProposerProfileRegistry(Map.of(
            OpenAiCompatibleChatModelFactory.BASE_URL_ENV, "http://127.0.0.1:11434/v1",
            OpenAiCompatibleChatModelFactory.API_KEY_ENV, "test-key",
            OpenAiCompatibleChatModelFactory.MODEL_NAME_ENV, "test-model"
    ));

    @Test
    void resolvesKnownProfileAndListsKnownNamesOnFailure() {
        assertThat(registry.knownNames()).containsExactly("fixture", "openai-compatible");
        assertThat(registry.resolve("fixture", Path.of("some/folder"))).isNotNull();
        assertThat(registry.resolve("openai-compatible", Path.of("some/folder"))).isNotNull();

        assertThatThrownBy(() -> registry.resolve("gpt-cloud", Path.of("some/folder")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown proposer profile: gpt-cloud; known profiles: fixture, openai-compatible");
    }

    @Test
    void openAiCompatibleProfileUsesTheSaaaEnvironmentContract() {
        var registry = new ProposerProfileRegistry(Map.of(
                OpenAiCompatibleChatModelFactory.BASE_URL_ENV, "http://127.0.0.1:11434/v1",
                OpenAiCompatibleChatModelFactory.API_KEY_ENV, "test-key"
        ));

        assertThatThrownBy(() -> registry.resolve("openai-compatible", Path.of("some/folder")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("missing required environment variable: SAAA_MODEL_NAME");
    }

    @Test
    void openAiCompatibleProfileProposesAgainstALocalEndpoint() throws Exception {
        try (var server = LocalOpenAiServer.start()) {
            var registry = new ProposerProfileRegistry(Map.of(
                    OpenAiCompatibleChatModelFactory.BASE_URL_ENV, server.baseUrl(),
                    OpenAiCompatibleChatModelFactory.API_KEY_ENV, "local-test-key",
                    OpenAiCompatibleChatModelFactory.MODEL_NAME_ENV, "local-test-model"
            ));

            var proposer = registry.resolve("openai-compatible", Path.of("some/folder"));
            var mutation = proposer.proposeFor(
                    new WorkflowGraph("workflow-a", "baseline", "agent -> tool -> answer"));

            assertThat(mutation.id()).isEqualTo("mut-local-001");
            assertThat(mutation.scope()).isEqualTo(MutationScope.WORKFLOW_DEFINITION);
            assertThat(mutation.patch()).isEqualTo("agent -> deterministic-tool -> answer");
            assertThat(server.lastAuthorization).isEqualTo("Bearer local-test-key");
            assertThat(server.lastRequestBody).contains("workflow-a", "agent -> tool -> answer");
        }
    }

    private static final class LocalOpenAiServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private String lastAuthorization;
        private String lastRequestBody;

        private LocalOpenAiServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        private static LocalOpenAiServer start() throws IOException {
            var httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            var executor = Executors.newSingleThreadExecutor();
            var localServer = new LocalOpenAiServer(httpServer, executor);
            httpServer.createContext("/v1/chat/completions", localServer::handleChatCompletions);
            httpServer.setExecutor(executor);
            httpServer.start();
            return localServer;
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        }

        private void handleChatCompletions(HttpExchange exchange) throws IOException {
            lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] response = """
                    {
                      "id": "chatcmpl-local",
                      "object": "chat.completion",
                      "created": 1,
                      "model": "local-test-model",
                      "choices": [
                        {
                          "index": 0,
                          "message": {
                            "role": "assistant",
                            "content": "{\\"id\\":\\"mut-local-001\\",\\"summary\\":\\"route through deterministic tool\\",\\"scope\\":\\"WORKFLOW_DEFINITION\\",\\"patch\\":\\"agent -> deterministic-tool -> answer\\"}"
                          },
                          "finish_reason": "stop"
                        }
                      ],
                      "usage": {
                        "prompt_tokens": 1,
                        "completion_tokens": 1,
                        "total_tokens": 2
                      }
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
