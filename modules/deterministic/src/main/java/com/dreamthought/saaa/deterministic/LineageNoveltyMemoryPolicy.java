package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.CheckStatus;
import com.dreamthought.saaa.domain.EvolutionaryMemoryPolicyConfig;
import com.dreamthought.saaa.domain.EvolutionaryMemoryRecord;
import com.dreamthought.saaa.domain.FitnessDecision;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Keeps champions, their known ancestors, distinct failures, evidence-novel individuals and a
 * deterministic exploration reservoir. Recency only breaks otherwise equal choices.
 */
public final class LineageNoveltyMemoryPolicy implements EvolutionaryMemoryPolicy {
    // Decision outranks score. A discarded candidate keeps its weighted magnitude, so ordering on
    // score alone would let a high-scoring failure take a champion slot from a lower-scoring
    // promotion. Score only ranks within one decision, never across the two.
    private static final Comparator<EvolutionaryMemoryRecord> BEST = Comparator
            .comparingInt((EvolutionaryMemoryRecord record) ->
                    record.decision() == FitnessDecision.PROMOTE ? 0 : 1)
            .thenComparing(Comparator.comparingDouble(EvolutionaryMemoryRecord::aggregateFitness).reversed())
            .thenComparing(EvolutionaryMemoryRecord::evaluatedAt, Comparator.reverseOrder())
            .thenComparing(EvolutionaryMemoryRecord::candidateId);
    private final EvolutionaryMemoryPolicyConfig config;

    public LineageNoveltyMemoryPolicy(EvolutionaryMemoryPolicyConfig config) {
        this.config = java.util.Objects.requireNonNull(config, "config");
    }

    @Override public String id() { return config.id(); }

    @Override
    public List<EvolutionaryMemoryRecord> select(List<EvolutionaryMemoryRecord> archive) {
        var ordered = archive.stream().sorted(BEST).toList();
        var selected = new LinkedHashMap<String, EvolutionaryMemoryRecord>();

        distinct(ordered, record -> record.mutationScope() + "|" + record.retrievalConfigurationId())
                .stream().limit(config.championSlots()).forEach(record -> put(selected, record));
        addAncestors(selected, archive, config.lineageSlots());

        distinct(ordered.stream().filter(record -> record.checks().stream()
                        .anyMatch(check -> check.status() != CheckStatus.PASSED)).toList(),
                LineageNoveltyMemoryPolicy::failureFingerprint)
                .stream().limit(config.failureFingerprintSlots()).forEach(record -> put(selected, record));

        distinct(ordered, LineageNoveltyMemoryPolicy::noveltySignature)
                .stream().limit(config.noveltySlots()).forEach(record -> put(selected, record));

        ordered.stream().sorted(Comparator.comparing(record -> digest(record.candidateId())))
                .limit(config.explorationSlots()).forEach(record -> put(selected, record));

        return selected.values().stream().limit(config.maxActiveEvaluations()).toList();
    }

    private static List<EvolutionaryMemoryRecord> distinct(
            List<EvolutionaryMemoryRecord> records,
            java.util.function.Function<EvolutionaryMemoryRecord, String> classifier) {
        Map<String, EvolutionaryMemoryRecord> representatives = new LinkedHashMap<>();
        records.forEach(record -> representatives.putIfAbsent(classifier.apply(record), record));
        return new ArrayList<>(representatives.values());
    }

    private static void addAncestors(
            LinkedHashMap<String, EvolutionaryMemoryRecord> selected,
            List<EvolutionaryMemoryRecord> archive,
            int lineageSlots) {
        Map<String, EvolutionaryMemoryRecord> byCommit = new LinkedHashMap<>();
        archive.forEach(record -> byCommit.put(record.candidateCommit(), record));
        var frontier = new ArrayList<>(selected.values());
        var visited = new LinkedHashSet<String>();
        int added = 0;
        while (!frontier.isEmpty() && added < lineageSlots) {
            EvolutionaryMemoryRecord child = frontier.removeFirst();
            if (!visited.add(child.candidateId())) continue;
            EvolutionaryMemoryRecord parent = byCommit.get(child.baselineRepositoryRevision());
            if (parent != null && !selected.containsKey(parent.candidateId())) {
                put(selected, parent);
                added++;
                frontier.add(parent);
            }
        }
    }

    private static String failureFingerprint(EvolutionaryMemoryRecord record) {
        String normalized = record.checks().stream()
                .filter(check -> check.status() != CheckStatus.PASSED)
                .map(check -> check.name() + ":" + check.status() + ":"
                        + check.summary().toLowerCase()
                                .replaceAll("0x[0-9a-f]+", " <hex> ")
                                .replaceAll("\\b[0-9]+\\b", " <n> ")
                                .replaceAll("[^a-z0-9<>]+", " ").trim())
                .sorted().reduce("", (left, right) -> left + "|" + right);
        return digest(normalized);
    }

    private static String noveltySignature(EvolutionaryMemoryRecord record) {
        return digest(record.mutationScope() + "|" + record.retrievedEvidenceIds().stream().sorted().toList()
                + "|" + record.retrievalMode());
    }

    private static void put(Map<String, EvolutionaryMemoryRecord> selected, EvolutionaryMemoryRecord record) {
        selected.putIfAbsent(record.candidateId(), record);
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
