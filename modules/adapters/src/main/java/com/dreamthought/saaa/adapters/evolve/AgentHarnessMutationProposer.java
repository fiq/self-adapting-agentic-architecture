package com.dreamthought.saaa.adapters.evolve;

import com.dreamthought.saaa.deterministic.AgentHarness;
import com.dreamthought.saaa.deterministic.MutationProposer;
import com.dreamthought.saaa.domain.AgentRequest;
import com.dreamthought.saaa.domain.AgentRunStatus;
import com.dreamthought.saaa.domain.AgentRoute;
import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.PreparedMutationProposalRequest;
import com.dreamthought.saaa.domain.ProposerEvidence;
import com.dreamthought.saaa.domain.ResourceBudget;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Adapts one provider-neutral harness invocation to the mutation-proposer port. */
public final class AgentHarnessMutationProposer implements MutationProposer {
    private static final String OUTPUT_SCHEMA = "mutation-v1";
    private final AgentHarness harness;
    private final AgentRoute route;
    private final ResourceBudget budget;

    public AgentHarnessMutationProposer(AgentHarness harness, AgentRoute route, ResourceBudget budget) {
        this.harness = Objects.requireNonNull(harness, "harness");
        this.route = Objects.requireNonNull(route, "route");
        this.budget = Objects.requireNonNull(budget, "budget");
    }

    @Override
    public Mutation proposeFor(WorkflowGraph baseline) {
        return invoke(new com.dreamthought.saaa.domain.MutationProposalRequest(
                baseline,
                new com.dreamthought.saaa.domain.RetrievalQuery(
                        com.dreamthought.saaa.domain.RetrievalMode.NONE,
                        "Propose a bounded workflow mutation",
                        baseline,
                        baseline.version(),
                        java.util.List.of(baseline.id()),
                        Optional.empty())), Optional.empty());
    }

    @Override
    public Mutation proposeFor(PreparedMutationProposalRequest request) {
        Objects.requireNonNull(request, "request");
        return invoke(new com.dreamthought.saaa.domain.MutationProposalRequest(
                request.baseline(), request.retrievalQuery()), Optional.of(request.retrieval()));
    }

    @Override
    public Optional<ProposerEvidence> proposerEvidence() {
        return Optional.of(ProposerEvidence.of("agent_harness", Map.of(
                "provider", route.provider(),
                "model", route.model(),
                "tier", route.tier())));
    }

    private Mutation invoke(
            com.dreamthought.saaa.domain.MutationProposalRequest proposal,
            Optional<com.dreamthought.saaa.domain.RetrievalBundle> retrieval) {
        Path workspace = createIsolatedWorkspace(proposal.baseline());
        try {
            AgentRequest request = new AgentRequest(
                    proposal,
                    workspace,
                    Set.of("read-workflow", "read-retrieval-context"),
                    OUTPUT_SCHEMA,
                    route,
                    budget,
                    retrieval);
            com.dreamthought.saaa.domain.AgentRunResult result;
            try {
                result = Objects.requireNonNull(harness.run(request), "agent harness result");
            } catch (RuntimeException exception) {
                throw new IllegalStateException("agent invocation failed: " + safeFailure(exception), exception);
            }
            if (result.status() != AgentRunStatus.COMPLETED || result.mutation().isEmpty()) {
                throw new IllegalStateException("agent invocation did not complete: "
                        + result.status() + ": " + result.failureReason().orElse("no mutation returned"));
            }
            return result.mutation().orElseThrow();
        } finally {
            deleteIsolatedWorkspace(workspace);
        }
    }

    private static Path createIsolatedWorkspace(WorkflowGraph baseline) {
        try {
            Path workspace = Files.createTempDirectory("saaa-agent-proposal-");
            Files.writeString(workspace.resolve("baseline.txt"), baseline.definition());
            return workspace;
        } catch (IOException exception) {
            throw new IllegalStateException("failed to create isolated agent workspace", exception);
        }
    }

    private static void deleteIsolatedWorkspace(Path workspace) {
        try (var paths = Files.walk(workspace)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new IllegalStateException("failed to clean isolated agent workspace", exception);
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException("failed to clean isolated agent workspace", exception);
        }
    }

    private static String safeFailure(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
        String safe = message.replaceAll("(?i)bearer\\s+\\S+", "Bearer <redacted>")
                .replaceAll("(?i)sk-[a-z0-9_-]+", "<redacted>")
                .replaceAll("[\\r\\n]+", " ").trim();
        return safe.substring(0, Math.min(safe.length(), 512));
    }
}
