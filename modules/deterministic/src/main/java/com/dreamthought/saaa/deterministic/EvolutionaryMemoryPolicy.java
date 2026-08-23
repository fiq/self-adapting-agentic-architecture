package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.EvolutionaryMemoryRecord;
import java.util.List;
import java.util.Objects;

/** Selects a bounded graph working set without deleting durable experiment history. */
public interface EvolutionaryMemoryPolicy {
    String id();

    /**
     * Selects a working set from the records that are comparable with {@code currentFingerprint}.
     *
     * <p>The fingerprint parameter is mandatory rather than an overload, so mixing configurations is
     * a compile error rather than a convention someone can forget. An earlier version of this port
     * took the archive alone; stamping a fingerprint onto every record while leaving that signature
     * in place produced exactly the defect CHG-024 exists to close — a guard written down and never
     * read. See RISK-002 for the same shape elsewhere.
     *
     * <p>Records scored under another configuration, and legacy records that never carried one, stay
     * in the archive and are simply not ranked. A magnitude produced under different weights, a
     * different held-out set or a different threshold is not a worse number, it is a different
     * measurement.
     */
    default List<EvolutionaryMemoryRecord> select(
            List<EvolutionaryMemoryRecord> archive, String currentFingerprint) {
        Objects.requireNonNull(currentFingerprint, "currentFingerprint");
        return selectComparable(archive.stream()
                .filter(record -> record.scoringFingerprint().equals(currentFingerprint))
                .toList());
    }

    /**
     * Applies the policy's own slot logic to records already known to be comparable.
     *
     * <p>Implementations put their selection here. Nothing outside this interface should call it:
     * the comparability filter above is what makes the working set meaningful, and bypassing it
     * ranks measurements that were never the same.
     */
    List<EvolutionaryMemoryRecord> selectComparable(List<EvolutionaryMemoryRecord> archive);

    /**
     * The fingerprint a replay should treat as current: the newest record's, ties broken by candidate
     * id so the choice is deterministic rather than dependent on archive order.
     *
     * <p>A replay has no run in flight to take the fingerprint from, so it takes the most recent
     * measurement in the archive and ranks what is comparable with that. An empty archive has no
     * current configuration, and reports the legacy marker rather than inventing one.
     */
    static String currentFingerprintOf(List<EvolutionaryMemoryRecord> archive) {
        return archive.stream()
                .max(java.util.Comparator.comparing(EvolutionaryMemoryRecord::evaluatedAt)
                        .thenComparing(EvolutionaryMemoryRecord::candidateId))
                .map(EvolutionaryMemoryRecord::scoringFingerprint)
                .orElse(com.dreamthought.saaa.domain.ScoringContext.LEGACY_UNVERSIONED);
    }

    default List<EvolutionaryMemoryRecord> selectForRevision(
            List<EvolutionaryMemoryRecord> archive, String repositoryRevision,
            String currentFingerprint) {
        return select(archive.stream()
                .filter(record -> record.baselineRepositoryRevision().equals(repositoryRevision)
                        || record.candidateCommit().equals(repositoryRevision))
                .toList(), currentFingerprint);
    }
}
