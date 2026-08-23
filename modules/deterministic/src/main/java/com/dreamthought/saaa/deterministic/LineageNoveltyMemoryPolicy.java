package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.CheckStatus;
import com.dreamthought.saaa.domain.EvolutionaryMemoryPolicyConfig;
import com.dreamthought.saaa.domain.EvolutionaryMemoryRecord;
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
    // FitnessScore's natural order carries the decision and raw magnitude together. A discarded
    // candidate therefore cannot outrank a promotion merely by carrying a larger magnitude.
    private static final Comparator<EvolutionaryMemoryRecord> BEST = Comparator
            .comparing(EvolutionaryMemoryRecord::fitnessScore, Comparator.reverseOrder())
            .thenComparing(EvolutionaryMemoryRecord::evaluatedAt, Comparator.reverseOrder())
            .thenComparing(EvolutionaryMemoryRecord::candidateId);
    private final EvolutionaryMemoryPolicyConfig config;

    public LineageNoveltyMemoryPolicy(EvolutionaryMemoryPolicyConfig config) {
        this.config = java.util.Objects.requireNonNull(config, "config");
    }

    @Override public String id() { return config.id(); }

    @Override
    public List<EvolutionaryMemoryRecord> selectComparable(List<EvolutionaryMemoryRecord> archive) {
        var ordered = archive.stream().sorted(BEST).toList();
        var selected = new LinkedHashMap<String, EvolutionaryMemoryRecord>();

        fill(selected, distinct(ordered,
                record -> record.mutationScope() + "|" + record.retrievalConfigurationId()),
                config.championSlots());
        addAncestors(selected, archive, config.lineageSlots());

        fill(selected, distinct(ordered.stream().filter(record -> record.checks().stream()
                        .anyMatch(check -> check.status() != CheckStatus.PASSED)).toList(),
                LineageNoveltyMemoryPolicy::failureFingerprint),
                config.failureFingerprintSlots());

        fill(selected, distinct(ordered, LineageNoveltyMemoryPolicy::noveltySignature),
                config.noveltySlots());

        fill(selected, ordered.stream()
                .sorted(Comparator.comparing(record -> digest(record.candidateId()))).toList(),
                config.explorationSlots());

        return selected.values().stream().limit(config.maxActiveEvaluations()).toList();
    }

    /**
     * A category slot buys a record the selection does not already hold. Counting a slot against a
     * record an earlier pass took would leave the category unrepresented while reporting it filled,
     * and decision-first ordering makes that collision systematic rather than occasional: a promoted
     * record now sorts ahead of every failure in every pass, so the same one is offered repeatedly.
     *
     * <p>The slot advances to the next unrepresented class, not to another member of a class already
     * represented: {@code distinct} has already reduced each class to one candidate by the time this
     * runs, so a class whose representative was selected earlier is skipped rather than retried.
     */
    private static void fill(
            Map<String, EvolutionaryMemoryRecord> selected,
            List<EvolutionaryMemoryRecord> candidates,
            int slots) {
        candidates.stream()
                .filter(record -> !selected.containsKey(record.candidateId()))
                .limit(slots)
                .forEach(record -> put(selected, record));
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
        // Nothing makes candidateCommit unique, so two records can claim the same commit. Taking the
        // last one seen would make which ancestor is retained depend on archive input order, and a
        // selection that changes with input order is not a deterministic policy. The archive is
        // walked in BEST order and the first claim wins, so the answer is stable and the better
        // record represents the commit.
        Map<String, EvolutionaryMemoryRecord> byCommit = new LinkedHashMap<>();
        archive.stream().sorted(BEST)
                .forEach(record -> byCommit.putIfAbsent(record.candidateCommit(), record));
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
                        + check.summary().toLowerCase(java.util.Locale.ROOT)
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
