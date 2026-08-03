package com.dreamthought.saaa.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Transitional retrieval input. MutationContract is the richer authority when present; task text
 * bridges the currently wired Mutation path until the live loop converges on that contract.
 */
public record RetrievalQuery(
        RetrievalMode mode,
        String task,
        WorkflowGraph baseline,
        String repositoryRevision,
        List<String> exactIdentifiers,
        Optional<MutationContract> mutationContract
) {
    public RetrievalQuery {
        mode = Objects.requireNonNull(mode, "mode");
        task = Require.nonBlank(task, "task");
        baseline = Objects.requireNonNull(baseline, "baseline");
        repositoryRevision = Require.nonBlank(repositoryRevision, "repositoryRevision");
        exactIdentifiers = List.copyOf(Objects.requireNonNull(exactIdentifiers, "exactIdentifiers"));
        mutationContract = Objects.requireNonNull(mutationContract, "mutationContract");
    }

    public String semanticText() {
        if (mutationContract.isEmpty()) {
            return task + "\nTarget: " + baseline.id();
        }
        MutationContract contract = mutationContract.get();
        return task
                + "\nHypothesis: " + contract.hypothesis()
                + "\nTarget: " + contract.target().file() + " " + contract.target().symbol()
                + "\nLoci: " + String.join(", ", contract.loci())
                + "\nRequired evidence: " + String.join(", ", contract.requiredEvidence())
                + contract.searchPosture().map(value -> "\nSearch posture: " + value).orElse("")
                + contract.parentTraits().stream()
                        .map(trait -> "\nParent trait: " + trait.trait())
                        .collect(Collectors.joining());
    }
}
