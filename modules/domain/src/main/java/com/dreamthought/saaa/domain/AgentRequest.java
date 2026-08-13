package com.dreamthought.saaa.domain;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;

/** Provider-neutral input to one bounded agent invocation. */
public record AgentRequest(
        MutationProposalRequest proposal,
        Path workspace,
        Set<String> allowedCapabilities,
        String expectedOutputSchema,
        AgentRoute route,
        ResourceBudget budget,
        Optional<RetrievalBundle> retrieval
) {
    public AgentRequest(
            MutationProposalRequest proposal,
            Path workspace,
            Set<String> allowedCapabilities,
            String expectedOutputSchema,
            AgentRoute route,
            ResourceBudget budget
    ) {
        this(proposal, workspace, allowedCapabilities, expectedOutputSchema, route, budget, Optional.empty());
    }

    public AgentRequest {
        proposal = Objects.requireNonNull(proposal, "proposal");
        workspace = Objects.requireNonNull(workspace, "workspace").toAbsolutePath().normalize();
        if (workspace.toString().isBlank()) {
            throw new IllegalArgumentException("workspace must not be blank");
        }
        allowedCapabilities = Set.copyOf(Objects.requireNonNull(allowedCapabilities, "allowedCapabilities"));
        allowedCapabilities.forEach(capability -> Require.nonBlank(capability, "capability"));
        expectedOutputSchema = Require.nonBlank(expectedOutputSchema, "expectedOutputSchema");
        route = Objects.requireNonNull(route, "route");
        budget = Objects.requireNonNull(budget, "budget");
        retrieval = Objects.requireNonNull(retrieval, "retrieval");
        if (retrieval.isPresent()) {
            RetrievalBundle bundle = retrieval.orElseThrow();
            if (!bundle.mode().equals(proposal.retrievalQuery().mode())
                    || !bundle.repositoryRevision().equals(proposal.retrievalQuery().repositoryRevision())) {
                throw new IllegalArgumentException("agent retrieval must match the proposal query");
            }
        }
    }
}
