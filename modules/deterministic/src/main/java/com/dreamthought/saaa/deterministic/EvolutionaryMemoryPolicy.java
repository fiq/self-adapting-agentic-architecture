package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.EvolutionaryMemoryRecord;
import java.util.List;

/** Selects a bounded graph working set without deleting durable experiment history. */
public interface EvolutionaryMemoryPolicy {
    String id();

    List<EvolutionaryMemoryRecord> select(List<EvolutionaryMemoryRecord> archive);

    default List<EvolutionaryMemoryRecord> selectForRevision(
            List<EvolutionaryMemoryRecord> archive, String repositoryRevision) {
        return select(archive.stream()
                .filter(record -> record.baselineRepositoryRevision().equals(repositoryRevision)
                        || record.candidateCommit().equals(repositoryRevision))
                .toList());
    }
}
