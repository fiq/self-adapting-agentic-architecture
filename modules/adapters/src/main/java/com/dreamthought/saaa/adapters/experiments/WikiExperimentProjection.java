package com.dreamthought.saaa.adapters.experiments;

import com.dreamthought.saaa.domain.EvolutionaryMemoryRecord;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Human projection of the durable envelopes; explicitly non-authoritative and safely regenerable. */
public final class WikiExperimentProjection {
    private final Path target;

    public WikiExperimentProjection(Path repositoryRoot) {
        target = Objects.requireNonNull(repositoryRoot, "repositoryRoot").toAbsolutePath().normalize()
                .resolve("docs/wiki/experiments.md");
    }

    public void render(List<EvolutionaryMemoryRecord> records) {
        var ordered = records.stream().sorted(Comparator.comparing(EvolutionaryMemoryRecord::evaluatedAt).reversed()
                .thenComparing(EvolutionaryMemoryRecord::candidateId)).toList();
        StringBuilder out = new StringBuilder("""
                # Experiment memory

                > Generated from `experiments/ledger/*.toon`. This page is a human projection, not
                > deterministic-selection authority. Specifications, ADRs and evaluation envelopes retain
                > their own authority; inclusion here does not increase retrieval rank.

                ## Strategies observed

                """);
        var strategies = ordered.stream().collect(Collectors.groupingBy(
                record -> record.retrievalConfigurationId() + " / " + record.memoryPolicyId(),
                java.util.TreeMap::new, Collectors.counting()));
        if (strategies.isEmpty()) out.append("No experiment envelopes have been recorded.\n");
        else strategies.forEach((strategy, count) -> out.append("- `").append(strategy)
                .append("`: ").append(count).append(" evaluated candidate(s)\n"));
        out.append("\n## Evaluated candidates\n\n")
                .append("| Evaluated | Candidate | Subject revision | Process revision | Mutation | Changed paths | Retrieval | Fitness | Decision |\n")
                .append("|---|---|---|---|---|---|---|---:|---|\n");
        for (EvolutionaryMemoryRecord record : ordered) {
            out.append('|').append(record.evaluatedAt()).append('|').append(code(record.candidateId()))
                    .append('|').append(code(shortRevision(record.evolutionContext().subjectRepositoryRevision())))
                    .append('|').append(code(shortRevision(record.evolutionContext().processRepositoryRevision())))
                    .append('|').append(plain(record.mutationSummary())).append('|')
                    .append(plain(String.join(", ", record.changedPaths()))).append('|')
                    .append(code(record.retrievalMode() + " / " + record.retrievalConfigurationId()))
                    .append('|').append(record.fitnessScore().rawMagnitude()).append('|')
                    .append(record.fitnessScore().decision()).append("|\n");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, out.toString());
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to generate experiment wiki projection", exception);
        }
    }

    private static String code(Object value) { return "`" + plain(value.toString()).replace("`", "'") + "`"; }
    private static String plain(String value) { return value.replace('|', '/').replace('\n', ' '); }
    private static String shortRevision(String value) { return value.length() > 16 ? value.substring(0, 16) : value; }
}
