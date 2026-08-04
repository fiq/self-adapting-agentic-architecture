package com.dreamthought.saaa.domain;

import java.util.Locale;

public enum RetrievalMode {
    NONE,
    VECTOR,
    GRAPH,
    HYBRID;

    public static RetrievalMode parse(String value) {
        return valueOf(Require.nonBlank(value, "retrieval mode").toUpperCase(Locale.ROOT));
    }
}
