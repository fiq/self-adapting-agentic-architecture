package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.EvidenceDocument;
import java.util.List;

public interface VectorEvidenceStore {
    List<EvidenceDocument> searchVector(String embeddingModelId, int dimensions, List<Float> query, int limit);
}
