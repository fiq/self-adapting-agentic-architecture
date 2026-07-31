package com.dreamthought.saaa.adapters.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.MutationScope;
import com.dreamthought.saaa.domain.WorkflowGraph;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class OpenAiCompatibleMutationProposerIntegrationTest {
    private LocalOpenAiServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void proposesABoundedMutationAgainstALocalOpenAiCompatibleEndpoint() throws Exception {
        server = LocalOpenAiServer.start();
        var chatModel = new OpenAiCompatibleChatModelFactory().fromEnvironment(Map.of(
                OpenAiCompatibleChatModelFactory.BASE_URL_ENV, server.baseUrl(),
                OpenAiCompatibleChatModelFactory.API_KEY_ENV, "local-test-key",
                OpenAiCompatibleChatModelFactory.MODEL_NAME_ENV, "local-test-model"
        ));
        var proposer = LangChain4jMutationProposalAdapter.from(chatModel);

        var mutation = proposer.proposeFor(new WorkflowGraph("workflow-a", "baseline", "agent -> tool -> answer"));

        assertThat(mutation.id()).isEqualTo("mut-local-001");
        assertThat(mutation.scope()).isEqualTo(MutationScope.WORKFLOW_DEFINITION);
        assertThat(mutation.patch()).isEqualTo("agent -> deterministic-tool -> answer");
        assertThat(server.lastPath).isEqualTo("/v1/chat/completions");
        assertThat(server.lastAuthorization).isEqualTo("Bearer local-test-key");
        assertThat(server.lastRequestBody)
                .contains("\"model\" : \"local-test-model\"")
                .contains("workflow-a")
                .contains("agent -> tool -> answer");
    }

    private static final class LocalOpenAiServer implements AutoCloseable {
        private final HttpServer server;
        private String lastPath;
        private String lastAuthorization;
        private String lastRequestBody;

        private LocalOpenAiServer(HttpServer server) {
            this.server = server;
        }

        private static LocalOpenAiServer start() throws IOException {
            var httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            var localServer = new LocalOpenAiServer(httpServer);
            httpServer.createContext("/v1/chat/completions", localServer::handleChatCompletions);
            httpServer.setExecutor(Executors.newSingleThreadExecutor());
            httpServer.start();
            return localServer;
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        }

        private void handleChatCompletions(HttpExchange exchange) throws IOException {
            lastPath = exchange.getRequestURI().getPath();
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
        }
    }
}
