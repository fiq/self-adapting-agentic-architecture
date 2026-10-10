package com.dreamthought.saaa.adapters.langchain4j;

import static com.dreamthought.saaa.domain.MutationScope.WORKFLOW_DEFINITION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import com.dreamthought.saaa.domain.MutationLimits;
import com.dreamthought.saaa.domain.MutationScope;
import com.dreamthought.saaa.domain.PriceIdentity;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

final class LangChain4jMutationProposalAdapterTest {
    @Test
    void mapsTypedAiServiceProposalToCoreMutation() {
        var baseline = new WorkflowGraph("baseline", "v1", "agent -> tool -> answer");
        var service = new RecordingMutationService(new LangChain4jMutationProposalAdapter.MutationProposal(
                "mut-001",
                "tighten tool selection",
                WORKFLOW_DEFINITION.name(),
                "replace tool policy"
        ));
        var adapter = new LangChain4jMutationProposalAdapter(service);

        var mutation = adapter.proposeFor(baseline);

        assertThat(service.workflowId).isEqualTo("baseline");
        assertThat(service.workflowVersion).isEqualTo("v1");
        assertThat(service.workflowDefinition).isEqualTo("agent -> tool -> answer");
        assertThat(service.allowedScopes).containsExactlyElementsOf(Arrays.stream(MutationScope.values())
                .map(Enum::name)
                .toList());
        assertThat(mutation.id()).isEqualTo("mut-001");
        assertThat(mutation.summary()).isEqualTo("tighten tool selection");
        assertThat(mutation.scope()).isEqualTo(WORKFLOW_DEFINITION);
        assertThat(mutation.patch()).isEqualTo("replace tool policy");
    }

    @Test
    void createsTypedAiServiceFromChatModelWithoutCommittingToProvider() {
        var chatModel = new StaticJsonChatModel("""
                {
                  "id": "mut-002",
                  "summary": "prefer deterministic tool choice",
                  "scope": "TOOL_CONFIGURATION",
                  "patch": "set tool selection policy to deterministic"
                }
                """);
        var adapter = LangChain4jMutationProposalAdapter.from(chatModel);

        var mutation = adapter.proposeFor(new WorkflowGraph("baseline", "v1", "agent -> tool -> answer"));

        assertThat(chatModel.lastRequest).isNotNull();
        assertThat(chatModel.lastRequest.toString()).contains("baseline", "agent -> tool -> answer");
        assertThat(mutation.id()).isEqualTo("mut-002");
        assertThat(mutation.scope()).isEqualTo(MutationScope.TOOL_CONFIGURATION);
        assertThat(mutation.patch()).isEqualTo("set tool selection policy to deterministic");
    }

    @Test
    void recordsPromptDigestAndRawModelResponseWhenConstructedFromChatModel() {
        var chatModel = new StaticJsonChatModel("""
                {
                  "id": "mut-003",
                  "summary": "prefer deterministic tool choice",
                  "scope": "TOOL_CONFIGURATION",
                  "patch": "set tool selection policy to deterministic"
                }
                """);
        var adapter = LangChain4jMutationProposalAdapter.from(chatModel, "openai-compatible");

        adapter.proposeFor(new WorkflowGraph("baseline", "v1", "agent -> tool -> answer"));

        var evidence = adapter.proposerEvidence().orElseThrow();
        assertThat(evidence.proposerId()).isEqualTo("openai-compatible");
        assertThat(evidence.attributes())
                .containsKeys("prompt_digest", "prompt", "raw_response");
        assertThat(evidence.attributes().get("prompt_digest")).startsWith("sha256:");
        assertThat(evidence.attributes().get("prompt")).contains("baseline", "agent -> tool -> answer");
        assertThat(evidence.attributes().get("raw_response")).contains("mut-003");
    }

    @Test
    void reportsProviderUsageWithTheModelDeclaredByTheResponse() {
        var chatModel = new StaticJsonChatModel(response(
                "mut-usage", new TokenUsage(101, 202), "reported-model"));
        var adapter = LangChain4jMutationProposalAdapter.from(
                chatModel, "openai-compatible", "configured-model");

        adapter.proposeFor(new WorkflowGraph("baseline", "v1", "agent -> tool -> answer"));

        assertThat(adapter.usage()).isEqualTo(new com.dreamthought.saaa.domain.AgentUsage(
                OptionalLong.of(101),
                OptionalLong.of(202),
                Optional.empty(),
                adapter.usage().wallClockMillis(),
                OptionalInt.empty(),
                Optional.of(new PriceIdentity("openai-compatible", "reported-model"))));
        assertThat(adapter.usage().wallClockMillis()).isPresent();
        assertThat(adapter.proposerEvidence().orElseThrow().attributes())
                .containsEntry("requested_model", "configured-model")
                .containsEntry("reported_model", "reported-model");
    }

    @Test
    void aResponseWithoutUsageDoesNotInheritThePreviousCallsTokens() {
        var chatModel = new StaticJsonChatModel(
                response("mut-first", new TokenUsage(17, 29), "reported-model"),
                response("mut-second", null, null));
        var adapter = LangChain4jMutationProposalAdapter.from(
                chatModel, "openai-compatible", "configured-model");
        var baseline = new WorkflowGraph("baseline", "v1", "agent -> tool -> answer");

        adapter.proposeFor(baseline);
        var firstUsage = adapter.usage();
        adapter.proposeFor(baseline);

        assertThat(firstUsage.inputTokens()).hasValue(17L);
        assertThat(firstUsage.outputTokens()).hasValue(29L);
        assertThat(adapter.usage().inputTokens()).isEmpty();
        assertThat(adapter.usage().outputTokens()).isEmpty();
        assertThat(adapter.usage().creditsConsumed()).isEmpty();
        assertThat(adapter.usage().retries()).isEmpty();
        assertThat(adapter.usage().priceIdentity()).isEmpty();
        assertThat(adapter.usage().wallClockMillis()).isPresent();
    }

    @Test
    void rejectsModelOutputOutsideTheBoundedMutationContract() {
        var service = new RecordingMutationService(new LangChain4jMutationProposalAdapter.MutationProposal(
                "mut-approval",
                "approve itself",
                "APPROVAL",
                "promote candidate"
        ));
        var adapter = new LangChain4jMutationProposalAdapter(service);

        assertThatThrownBy(() -> adapter.proposeFor(new WorkflowGraph("baseline", "v1", "agent -> answer")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported mutation scope from model: APPROVAL");
    }

    @Test
    void rejectsMalformedModelOutputDeterministically() {
        var service = new RecordingMutationService(new LangChain4jMutationProposalAdapter.MutationProposal(
                " ",
                "blank id",
                WORKFLOW_DEFINITION.name(),
                "replace tool policy"
        ));
        var adapter = new LangChain4jMutationProposalAdapter(service);

        assertThatThrownBy(() -> adapter.proposeFor(new WorkflowGraph("baseline", "v1", "agent -> answer")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("id must not be blank");
    }

    @Test
    void rejectsOversizedModelOutputDeterministically() {
        var service = new RecordingMutationService(new LangChain4jMutationProposalAdapter.MutationProposal(
                "mut-oversized",
                "oversized patch",
                WORKFLOW_DEFINITION.name(),
                "x".repeat(MutationLimits.MAX_PATCH_LENGTH + 1)
        ));
        var adapter = new LangChain4jMutationProposalAdapter(service);

        assertThatThrownBy(() -> adapter.proposeFor(new WorkflowGraph("baseline", "v1", "agent -> answer")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("patch must be at most " + MutationLimits.MAX_PATCH_LENGTH + " characters");
    }

    @Test
    void mutationProposalContractDoesNotExposeApprovalOrScoringAuthority() {
        assertThat(Arrays.stream(LangChain4jMutationProposalAdapter.MutationProposal.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase())
                .toList())
                .containsExactly("id", "summary", "scope", "patch")
                .doesNotContain("score", "decision", "approve", "promote", "discard", "rollback");
    }

    private static final class RecordingMutationService
            implements LangChain4jMutationProposalAdapter.WorkflowMutationAiService {
        private final LangChain4jMutationProposalAdapter.MutationProposal proposal;
        private String workflowId;
        private String workflowVersion;
        private String workflowDefinition;
        private List<String> allowedScopes;
        private String mutationIntent;
        private String retrievalContext;

        private RecordingMutationService(LangChain4jMutationProposalAdapter.MutationProposal proposal) {
            this.proposal = proposal;
        }

        @Override
        public LangChain4jMutationProposalAdapter.MutationProposal proposeMutation(
                String workflowId,
                String workflowVersion,
                String workflowDefinition,
                List<String> allowedScopes,
                String mutationIntent,
                String retrievalContext
        ) {
            this.workflowId = workflowId;
            this.workflowVersion = workflowVersion;
            this.workflowDefinition = workflowDefinition;
            this.allowedScopes = List.copyOf(allowedScopes);
            this.mutationIntent = mutationIntent;
            this.retrievalContext = retrievalContext;
            return proposal;
        }
    }

    private static final class StaticJsonChatModel implements ChatModel {
        private final ChatResponse[] responses;
        private int responseIndex;
        private ChatRequest lastRequest;

        private StaticJsonChatModel(String response) {
            this(ChatResponse.builder().aiMessage(AiMessage.from(response)).build());
        }

        private StaticJsonChatModel(ChatResponse... responses) {
            this.responses = responses;
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            lastRequest = chatRequest;
            return responses[responseIndex++];
        }
    }

    private static ChatResponse response(String id, TokenUsage tokenUsage, String modelName) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from("""
                        {
                          "id": "%s",
                          "summary": "bounded proposal",
                          "scope": "WORKFLOW_DEFINITION",
                          "patch": "replace tool policy"
                        }
                        """.formatted(id)))
                .tokenUsage(tokenUsage)
                .modelName(modelName)
                .build();
    }
}
