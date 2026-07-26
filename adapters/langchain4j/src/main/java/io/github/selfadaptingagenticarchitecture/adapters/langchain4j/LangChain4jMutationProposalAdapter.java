package io.github.selfadaptingagenticarchitecture.adapters.langchain4j;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.github.selfadaptingagenticarchitecture.application.MutationProposer;
import io.github.selfadaptingagenticarchitecture.core.Mutation;
import io.github.selfadaptingagenticarchitecture.core.MutationLimits;
import io.github.selfadaptingagenticarchitecture.core.MutationScope;
import io.github.selfadaptingagenticarchitecture.core.WorkflowGraph;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class LangChain4jMutationProposalAdapter implements MutationProposer {
    private final WorkflowMutationAiService service;

    public static LangChain4jMutationProposalAdapter from(ChatModel chatModel) {
        Objects.requireNonNull(chatModel, "chatModel");
        return new LangChain4jMutationProposalAdapter(AiServices.create(WorkflowMutationAiService.class, chatModel));
    }

    LangChain4jMutationProposalAdapter(WorkflowMutationAiService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Mutation proposeFor(WorkflowGraph baseline) {
        Objects.requireNonNull(baseline, "baseline");
        MutationProposal proposal = Objects.requireNonNull(
                service.proposeMutation(
                        baseline.id(),
                        baseline.version(),
                        baseline.definition(),
                        allowedScopes()
                ),
                "mutation proposal"
        );
        return proposal.toMutation();
    }

    private static List<String> allowedScopes() {
        return Arrays.stream(MutationScope.values())
                .map(Enum::name)
                .toList();
    }

    public interface WorkflowMutationAiService {
        @SystemMessage("""
                You propose bounded workflow mutations for an experimental agentic workflow evaluator.
                Return exactly one mutation proposal. Do not approve, score, promote or discard candidates.
                Validation, fitness scoring, promotion and rollback are deterministic system responsibilities.
                """)
        @UserMessage("""
                Baseline workflow:
                id: {{workflowId}}
                version: {{workflowVersion}}
                definition:
                {{workflowDefinition}}

                Allowed mutation scopes: {{allowedScopes}}

                Return JSON with fields:
                - id: stable mutation identifier
                - summary: concise human-readable change summary
                - scope: one of the allowed scopes
                - patch: bounded workflow-definition patch text
                """)
        MutationProposal proposeMutation(
                @V("workflowId") String workflowId,
                @V("workflowVersion") String workflowVersion,
                @V("workflowDefinition") String workflowDefinition,
                @V("allowedScopes") List<String> allowedScopes
        );
    }

    public record MutationProposal(String id, String summary, String scope, String patch) {
        private Mutation toMutation() {
            return new Mutation(
                    requireBounded(id, "id", MutationLimits.MAX_ID_LENGTH),
                    requireBounded(summary, "summary", MutationLimits.MAX_SUMMARY_LENGTH),
                    parseScope(scope),
                    requireBounded(patch, "patch", MutationLimits.MAX_PATCH_LENGTH)
            );
        }

        private static MutationScope parseScope(String scope) {
            String value = requireBounded(scope, "scope", MutationLimits.MAX_SCOPE_LENGTH);
            try {
                return MutationScope.valueOf(value);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("unsupported mutation scope from model: " + value, exception);
            }
        }
    }

    private static String requireBounded(String value, String name, int maxLength) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(name + " must be at most " + maxLength + " characters");
        }
        return value;
    }
}
