package com.dreamthought.saaa.deterministic;

import java.util.List;

/** Provider-neutral semantic embedding boundary. */
public interface SemanticEmbeddingModel {
    String modelId();

    int dimensions();

    List<Float> embed(String semanticText);
}
