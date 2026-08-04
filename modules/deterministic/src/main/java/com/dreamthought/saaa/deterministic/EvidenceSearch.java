package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.EvidenceDocument;
import com.dreamthought.saaa.domain.RelationshipType;
import java.util.List;
import java.util.Set;

/** Provider-neutral exact, vector and graph search boundary. */
public interface EvidenceSearch {
    List<EvidenceDocument> resolveExact(List<String> identifiers);

    List<EvidenceDocument> vectorSearch(String semanticQuery, int limit);

    List<EvidenceDocument> expand(
            List<String> seedIds,
            Set<RelationshipType> relationships,
            int depth,
            int maxFanOut);
}
