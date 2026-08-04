package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.EvidenceCapsule;
import com.dreamthought.saaa.domain.EvidenceDocument;
import com.dreamthought.saaa.domain.RetrievalBundle;
import com.dreamthought.saaa.domain.RetrievalConfig;
import com.dreamthought.saaa.domain.RetrievalDiagnostics;
import com.dreamthought.saaa.domain.RetrievalMode;
import com.dreamthought.saaa.domain.RetrievalQuery;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Deterministic exact/vector/graph rank fusion and evidence budget enforcement. */
public final class HybridEvidenceRetriever implements EvidenceRetriever {
    private final EvidenceSearch search;
    private final RetrievalConfig config;
    private final EvidenceCapsuleCompiler capsuleCompiler;

    public HybridEvidenceRetriever(EvidenceSearch search, RetrievalConfig config) {
        this(search, config, EvidenceCapsuleCache.disabled());
    }

    public HybridEvidenceRetriever(EvidenceSearch search, RetrievalConfig config, EvidenceCapsuleCache capsuleCache) {
        this.search = Objects.requireNonNull(search, "search");
        this.config = Objects.requireNonNull(config, "config");
        this.capsuleCompiler = new EvidenceCapsuleCompiler(capsuleCache, config.capsuleProjectionVersion());
    }

    @Override
    public RetrievalBundle retrieve(RetrievalQuery query) {
        Objects.requireNonNull(query, "query");
        if (query.mode() == RetrievalMode.NONE) {
            return EvidenceRetriever.none(config.id(), config.memoryPolicyId()).retrieve(query);
        }

        List<EvidenceDocument> exact = usesGraph(query.mode())
                ? search.resolveExact(query.exactIdentifiers())
                : List.of();
        List<EvidenceDocument> vector = usesVector(query.mode())
                ? search.vectorSearch(query.semanticText(), config.maxEvidence() * 3)
                : List.of();

        var seedIds = new LinkedHashSet<String>();
        exact.forEach(item -> seedIds.add(item.stableId()));
        vector.forEach(item -> seedIds.add(item.stableId()));
        List<EvidenceDocument> graph = usesGraph(query.mode())
                ? search.expand(
                        List.copyOf(seedIds),
                        config.allowedRelationships(),
                        config.graphDepth(),
                        config.maxFanOut())
                : List.of();

        Map<String, Ranked> fused = new LinkedHashMap<>();
        addRanks(fused, exact, "exact identifier");
        addRanks(fused, vector, "semantic vector rank");
        addRanks(fused, graph, "bounded graph expansion");

        List<Ranked> ordered = fused.values().stream()
                .map(this::addHistoricalBonus)
                .sorted(Comparator.comparingDouble(Ranked::score).reversed()
                        .thenComparing(ranked -> ranked.document().stableId()))
                .toList();

        var selected = new ArrayList<EvidenceCapsule>();
        int tokens = 0;
        int cacheHits = 0;
        int cacheMisses = 0;
        for (Ranked ranked : ordered) {
            EvidenceCapsuleCompiler.Compilation compilation = capsuleCompiler.compile(
                    ranked.document(), List.copyOf(ranked.reasons()));
            EvidenceCapsule capsule = compilation.capsule();
            if (selected.size() >= config.maxEvidence()
                    || tokens + capsule.estimatedTokens() > config.maxContextTokens()) {
                continue;
            }
            selected.add(capsule);
            tokens += capsule.estimatedTokens();
            if (compilation.cacheHit()) {
                cacheHits++;
            } else {
                cacheMisses++;
            }
        }

        List<String> considered = ordered.stream().map(ranked -> ranked.document().stableId()).toList();
        RetrievalDiagnostics diagnostics = new RetrievalDiagnostics(
                exact.size(),
                vector.size(),
                graph.size(),
                ordered.size(),
                cacheHits,
                cacheMisses,
                config.historicalWeightCap(),
                considered);
        return new RetrievalBundle(
                query.mode(),
                config.id(),
                query.repositoryRevision(),
                config.graphSchemaVersion(),
                config.capsuleProjectionVersion(),
                config.rankingVersion(),
                config.embeddingModelId(),
                config.memoryPolicyId(),
                selected,
                diagnostics,
                render(selected));
    }

    private void addRanks(
            Map<String, Ranked> fused,
            List<EvidenceDocument> documents,
            String reason
    ) {
        int rank = 1;
        for (EvidenceDocument document : documents) {
            int currentRank = rank;
            double rrf = 1.0 / (config.reciprocalRankConstant() + rank);
            String rankedReason = reason + " #" + currentRank;
            String finalReason = rankedReason;
            fused.compute(document.stableId(), (id, current) -> current == null
                    ? new Ranked(document, rrf, new ArrayList<>(List.of(finalReason)))
                    : current.add(rrf, finalReason));
            rank++;
        }
    }

    private Ranked addHistoricalBonus(Ranked ranked) {
        int outcomes = ranked.document().historicalOutcomes().size();
        if (outcomes == 0) return ranked;
        double bonus = Math.min(config.historicalWeightCap(), outcomes * 0.01);
        return ranked.add(bonus, "historical outcomes " + outcomes + " with bonus " + bonus
                + " applied once after fusion (cap " + config.historicalWeightCap() + ")");
    }

    private static String render(List<EvidenceCapsule> capsules) {
        StringBuilder out = new StringBuilder();
        for (EvidenceCapsule capsule : capsules) {
            out.append("[").append(capsule.subject().stableId()).append("]\n")
                    .append("kind: ").append(capsule.subject().kind()).append("\n")
                    .append("authority: ").append(capsule.authority()).append(" / ").append(capsule.status()).append("\n")
                    .append("reason: ").append(String.join("; ", capsule.selectionReasons())).append("\n")
                    .append("relationships: ").append(capsule.links()).append("\n")
                    .append(capsule.summary()).append("\n")
                    .append("sources: ")
                    .append(capsule.sources().stream().map(source -> source.path() + "#" + source.anchor()).toList())
                    .append("\n\n");
        }
        return out.toString();
    }

    private static boolean usesGraph(RetrievalMode mode) {
        return mode == RetrievalMode.GRAPH || mode == RetrievalMode.HYBRID;
    }

    private static boolean usesVector(RetrievalMode mode) {
        return mode == RetrievalMode.VECTOR || mode == RetrievalMode.HYBRID;
    }

    private record Ranked(EvidenceDocument document, double score, List<String> reasons) {
        private Ranked add(double increment, String reason) {
            var updated = new ArrayList<>(reasons);
            updated.add(reason);
            return new Ranked(document, score + increment, updated);
        }
    }
}
