package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.EvidenceCapsule;
import com.dreamthought.saaa.domain.EvidenceCapsuleProjection;
import com.dreamthought.saaa.domain.EvidenceDocument;
import java.util.List;
import java.util.Objects;

/** Deterministically compiles graph-shaped evidence into low-noise model context. */
public final class EvidenceCapsuleCompiler {
    private final EvidenceCapsuleCache cache;
    private final String projectionVersion;

    public EvidenceCapsuleCompiler(EvidenceCapsuleCache cache, String projectionVersion) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.projectionVersion = Objects.requireNonNull(projectionVersion, "projectionVersion");
    }

    public Compilation compile(EvidenceDocument document, List<String> selectionReasons) {
        var cached = cache.find(document.logicalId(), document.revision(), projectionVersion);
        EvidenceCapsuleProjection projection;
        boolean cacheHit;
        if (cached.isPresent()) {
            projection = cached.get();
            cacheHit = true;
        } else {
            projection = new EvidenceCapsuleProjection(
                    document.subject(), document.revision(), projectionVersion, document.semanticText(),
                    document.authority(), document.status(), document.links(), List.of(),
                    document.sources(), Math.max(1, (document.semanticText().length() + 3) / 4));
            cache.put(projection);
            cacheHit = false;
        }
        return new Compilation(new EvidenceCapsule(
                projection.subject(), projection.revision(), projection.projectionVersion(), projection.summary(),
                projection.authority(), projection.status(), projection.links(), document.historicalOutcomes(),
                projection.sources(), selectionReasons, projection.estimatedTokens()), cacheHit);
    }

    public record Compilation(EvidenceCapsule capsule, boolean cacheHit) { }
}
