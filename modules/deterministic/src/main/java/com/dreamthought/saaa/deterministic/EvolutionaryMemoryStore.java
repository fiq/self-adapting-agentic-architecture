package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.EvolutionaryMemoryRecord;

public interface EvolutionaryMemoryStore {
    void append(EvolutionaryMemoryRecord record);

    static EvolutionaryMemoryStore disabled() {
        return record -> { };
    }
}
