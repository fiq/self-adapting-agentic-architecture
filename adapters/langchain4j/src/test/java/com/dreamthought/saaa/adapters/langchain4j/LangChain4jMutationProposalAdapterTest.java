package com.dreamthought.saaa.adapters.langchain4j;

import static com.dreamthought.saaa.core.MutationScope.WORKFLOW_DEFINITION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import com.dreamthought.saaa.core.MutationLimits;
import com.dreamthought.saaa.core.MutationScope;
import com.dreamthought.saaa.core.WorkflowGraph;
import java.util.Arrays;
import java.util.List;
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

        private RecordingMutationService(LangChain4jMutationProposalAdapter.MutationProposal proposal) {
            this.proposal = proposal;
        }

        @Override
        public LangChain4jMutationProposalAdapter.MutationProposal proposeMutation(
                String workflowId,
                String workflowVersion,
                String workflowDefinition,
                List<String> allowedScopes
        ) {
            this.workflowId = workflowId;
            this.workflowVersion = workflowVersion;
            this.workflowDefinition = workflowDefinition;
            this.allowedScopes = List.copyOf(allowedScopes);
            return proposal;
        }
    }

    private static final class StaticJsonChatModel implements ChatModel {
        private final String response;
        private ChatRequest lastRequest;

        private StaticJsonChatModel(String response) {
            this.response = response;
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            lastRequest = chatRequest;
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(response))
                    .build();
        }
    }
}
