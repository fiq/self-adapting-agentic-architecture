package com.dreamthought.saaa.domain;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Provider-neutral input to one bounded agent invocation. */
public record AgentRequest(
        MutationProposalRequest proposal,
        Path workspace,
        Set<String> allowedCapabilities,
        String expectedOutputSchema,
        AgentRoute route,
        ResourceBudget budget
) {
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
    }
}
