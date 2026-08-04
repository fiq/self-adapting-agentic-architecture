package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.EvidenceCapsuleProjection;
import java.util.Optional;

/** Memoises context compilation, independently from experiment metadata. */
public interface EvidenceCapsuleCache {
    Optional<EvidenceCapsuleProjection> find(String logicalSubject, String subjectRevision, String projectionVersion);

    void put(EvidenceCapsuleProjection projection);

    static EvidenceCapsuleCache disabled() {
        return new EvidenceCapsuleCache() {
            @Override
            public Optional<EvidenceCapsuleProjection> find(
                    String logicalSubject, String subjectRevision, String projectionVersion) {
                return Optional.empty();
            }

            @Override
            public void put(EvidenceCapsuleProjection projection) { }
        };
    }
}
