package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.EvolutionaryMemoryRecord;
import java.util.List;

/** Durable, replayable evaluation memory; unlike retrieval caches this is not disposable. */
public interface EvolutionaryMemoryArchive extends EvolutionaryMemoryStore {
    List<EvolutionaryMemoryRecord> records();
}
