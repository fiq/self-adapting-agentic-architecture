package com.dreamthought.saaa.cli;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

/** Shared OpenAI-compatible proposal stub for CLI acceptance tests. */
final class OpenAiStubSupport {
    private static final String BASE_URL_PROPERTY = "saaa.model.base-url";
    private static final String API_KEY_PROPERTY = "saaa.model.api-key";
    private static final String MODEL_NAME_PROPERTY = "saaa.model.name";

    static WireMockServer startOpenAiStub() {
        var server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        return server;
    }

    static void stubOpenAiMutationResponses(WireMockServer server, String... responses) {
        for (int index = 0; index < responses.length; index++) {
            var stub = post(urlEqualTo("/v1/chat/completions"))
                    .inScenario("live-generation")
                    .whenScenarioStateIs(index == 0 ? Scenario.STARTED : "answered-" + index)
                    .withHeader("Authorization", equalTo("Bearer local-test-key"))
                    .withRequestBody(matchingJsonPath("$.model", equalTo("local-test-model")))
                    .withRequestBody(matchingJsonPath("$.messages[0].content",
                            containing("bounded workflow mutations")))
                    .withRequestBody(matchingJsonPath("$.messages[1].content",
                            containing("draft-check: skip")))
                    .willReturn(okJson(responses[index]));
            if (index < responses.length - 1) {
                stub.willSetStateTo("answered-" + (index + 1));
            }
            server.stubFor(stub);
        }
    }

    static String openAiMutationResponse(String mutationId, String patch) {
        String mutationJson = "{\"id\":\"" + mutationId
                + "\",\"summary\":\"enforce the draft check\",\"scope\":\"WORKFLOW_DEFINITION\","
                + "\"patch\":\"" + escapeJson(patch) + "\"}";
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
                        "content": %s
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
                """.formatted(quoteJson(mutationJson));
    }

    static String quoteJson(String raw) {
        return "\"" + escapeJson(raw) + "\"";
    }

    static String escapeJson(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    static void configureOpenAiCompatibleProfile(WireMockServer server) {
        System.setProperty(BASE_URL_PROPERTY, server.baseUrl() + "/v1");
        System.setProperty(API_KEY_PROPERTY, "local-test-key");
        System.setProperty(MODEL_NAME_PROPERTY, "local-test-model");
    }

    static void clearOpenAiCompatibleProfileConfig() {
        System.clearProperty(BASE_URL_PROPERTY);
        System.clearProperty(API_KEY_PROPERTY);
        System.clearProperty(MODEL_NAME_PROPERTY);
    }
}
