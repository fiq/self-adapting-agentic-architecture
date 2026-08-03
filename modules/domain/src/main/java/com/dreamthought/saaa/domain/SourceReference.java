package com.dreamthought.saaa.domain;

public record SourceReference(String path, String anchor) {
    public SourceReference {
        path = Require.nonBlank(path, "path");
        anchor = Require.nonBlank(anchor, "anchor");
    }
}
