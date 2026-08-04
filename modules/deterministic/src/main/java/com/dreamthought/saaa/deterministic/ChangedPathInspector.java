package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.Candidate;
import java.util.List;

/** Observable committed-path boundary used by evolutionary memory. */
@FunctionalInterface
public interface ChangedPathInspector {
    List<String> inspect(Candidate candidate);

    static ChangedPathInspector disabled() {
        return candidate -> List.of();
    }
}
