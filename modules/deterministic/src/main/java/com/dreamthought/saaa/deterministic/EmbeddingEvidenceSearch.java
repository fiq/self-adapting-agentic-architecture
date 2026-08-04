package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.EvidenceDocument;
import com.dreamthought.saaa.domain.RelationshipType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Adds semantic query embedding to a structural evidence store without leaking provider types. */
public final class EmbeddingEvidenceSearch implements EvidenceSearch {
    private final EvidenceSearch structural;
    private final VectorEvidenceStore vectors;
    private final CachedSemanticEmbeddingModel embeddings;

    public EmbeddingEvidenceSearch(
            EvidenceSearch structural,
            VectorEvidenceStore vectors,
            CachedSemanticEmbeddingModel embeddings) {
        this.structural = Objects.requireNonNull(structural, "structural");
        this.vectors = Objects.requireNonNull(vectors, "vectors");
        this.embeddings = Objects.requireNonNull(embeddings, "embeddings");
    }

    @Override
    public List<EvidenceDocument> resolveExact(List<String> identifiers) {
        return structural.resolveExact(identifiers);
    }

    @Override
    public List<EvidenceDocument> vectorSearch(String semanticQuery, int limit) {
        var embedded = embeddings.embed(sha256(semanticQuery), semanticQuery);
        return vectors.searchVector(embeddings.modelId(), embeddings.dimensions(), embedded.vector(), limit);
    }

    @Override
    public List<EvidenceDocument> expand(
            List<String> seedIds, Set<RelationshipType> relationships, int depth, int maxFanOut) {
        return structural.expand(seedIds, relationships, depth, maxFanOut);
    }

    private static String sha256(String value) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
