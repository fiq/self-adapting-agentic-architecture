package com.dreamthought.saaa.adapters.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.dreamthought.saaa.domain.MutationScope;
import com.dreamthought.saaa.domain.WorkflowGraph;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class OpenAiCompatibleMutationProposerIntegrationTest {
    private WireMockServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void proposesABoundedMutationAgainstALocalOpenAiCompatibleEndpoint() throws Exception {
        server = startOpenAiStub();
        stubMutationResponse();
        var chatModel = openAiCompatibleChatModel();
        var proposer = LangChain4jMutationProposalAdapter.from(chatModel);

        var mutation = proposer.proposeFor(new WorkflowGraph("workflow-a", "baseline", "agent -> tool -> answer"));

        assertThat(mutation.id()).isEqualTo("mut-local-001");
        assertThat(mutation.scope()).isEqualTo(MutationScope.WORKFLOW_DEFINITION);
        assertThat(mutation.patch()).isEqualTo("agent -> deterministic-tool -> answer");
        verifyOpenAiRequest();
    }

    private static WireMockServer startOpenAiStub() {
        var wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        return wireMockServer;
    }

    private void stubMutationResponse() {
        server.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer local-test-key"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("local-test-model")))
                .withRequestBody(matchingJsonPath("$.messages[0].content", containing("bounded workflow mutations")))
                .withRequestBody(matchingJsonPath("$.messages[1].content", containing("workflow-a")))
                .withRequestBody(matchingJsonPath("$.messages[1].content", containing("agent -> tool -> answer")))
                .willReturn(okJson(openAiMutationResponse())));
    }

    private ChatModel openAiCompatibleChatModel() {
        return new OpenAiCompatibleChatModelFactory().fromConfig(new ModelEndpointConfig(
                server.baseUrl() + "/v1",
                "local-test-key",
                "local-test-model"));
    }

    private void verifyOpenAiRequest() {
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
