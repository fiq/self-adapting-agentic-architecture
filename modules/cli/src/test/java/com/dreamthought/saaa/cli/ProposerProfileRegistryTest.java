package com.dreamthought.saaa.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.dreamthought.saaa.adapters.evolve.ProposerProfileRegistry;
import com.dreamthought.saaa.deterministic.AgentHarness;
import com.dreamthought.saaa.adapters.langchain4j.ModelEndpointConfig;
import com.dreamthought.saaa.adapters.langchain4j.OpenAiCompatibleMutationProposerFactory;
import com.dreamthought.saaa.domain.MutationScope;
import com.dreamthought.saaa.domain.WorkflowGraph;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProposerProfileRegistryTest {
    private final ProposerProfileRegistry registry = new ProposerProfileRegistry(
            folder -> new OpenAiCompatibleMutationProposerFactory().fromConfig(new ModelEndpointConfig(
                    "http://127.0.0.1:11434/v1",
                    "test-key",
                    "test-model")));

    @Test
    void resolvesKnownProfileAndListsKnownNamesOnFailure() {
        assertThat(registry.knownNames()).containsExactly("fixture", "openai-compatible", "acp");
        assertThat(registry.resolve("fixture", Path.of("some/folder"))).isNotNull();
        assertThat(registry.resolve("openai-compatible", Path.of("some/folder"))).isNotNull();

        assertThatThrownBy(() -> registry.resolve("gpt-cloud", Path.of("some/folder")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown proposer profile: gpt-cloud; known profiles: fixture, openai-compatible, acp");
    }

    @Test
    void openAiCompatibleProfileIsResolvedLazily() {
        var registry = new ProposerProfileRegistry(folder -> {
            throw new IllegalStateException("profile factory invoked");
        });

        assertThat(registry.knownNames()).containsExactly("fixture", "openai-compatible", "acp");
        assertThatThrownBy(() -> registry.resolve("openai-compatible", Path.of("some/folder")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("profile factory invoked");
    }

    @Test
    void resolvesAcpProfileWithoutRequiringProcessConfigurationWhenFactoryIsInjected() {
        AgentHarness harness = request -> {
            throw new AssertionError("the injected harness must be lazy");
        };
        var injected = new ProposerProfileRegistry(
                ignored -> ignoredBaselineProposer(),
                ignored -> harness);

        assertThat(injected.resolve("acp", Path.of("some/folder"))).isNotNull();
    }

    private static com.dreamthought.saaa.deterministic.MutationProposer ignoredBaselineProposer() {
        return baseline -> new com.dreamthought.saaa.domain.Mutation(
                "unused", "unused", MutationScope.WORKFLOW_DEFINITION, baseline.definition());
    }

    @Test
    void openAiCompatibleProfileProposesAgainstALocalEndpoint() throws Exception {
        var server = startOpenAiStub();
        try {
            stubMutationResponse(server);
            var registry = registryBackedBy(server);

            var proposer = registry.resolve("openai-compatible", Path.of("some/folder"));
            var mutation = proposer.proposeFor(
                    new WorkflowGraph("workflow-a", "baseline", "agent -> tool -> answer"));

            assertThat(mutation.id()).isEqualTo("mut-local-001");
            assertThat(mutation.scope()).isEqualTo(MutationScope.WORKFLOW_DEFINITION);
            assertThat(mutation.patch()).isEqualTo("agent -> deterministic-tool -> answer");
            verifyOpenAiRequest(server);
        } finally {
            server.stop();
        }
    }

    private static WireMockServer startOpenAiStub() {
        var server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        return server;
    }

    private static void stubMutationResponse(WireMockServer server) {
        server.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer local-test-key"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("local-test-model")))
                .withRequestBody(matchingJsonPath("$.messages[0].content", containing("bounded workflow mutations")))
                .withRequestBody(matchingJsonPath("$.messages[1].content", containing("workflow-a")))
                .withRequestBody(matchingJsonPath("$.messages[1].content", containing("agent -> tool -> answer")))
                .willReturn(okJson(openAiMutationResponse())));
    }

    private static ProposerProfileRegistry registryBackedBy(WireMockServer server) {
        return new ProposerProfileRegistry(folder ->
                new OpenAiCompatibleMutationProposerFactory().fromConfig(new ModelEndpointConfig(
                        server.baseUrl() + "/v1",
                        "local-test-key",
                        "local-test-model")));
    }

    private static void verifyOpenAiRequest(WireMockServer server) {
        server.verify(postRequestedFor(urlEqualTo("/v1/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer local-test-key"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("local-test-model"))));
    }

    private static String openAiMutationResponse() {
        return """
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
                """;
    }
}
