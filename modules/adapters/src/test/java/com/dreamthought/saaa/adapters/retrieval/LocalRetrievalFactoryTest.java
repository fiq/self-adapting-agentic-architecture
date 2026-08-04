package com.dreamthought.saaa.adapters.retrieval;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dreamthought.saaa.domain.ProjectionStatus;
import com.dreamthought.saaa.domain.RetrievalMode;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class LocalRetrievalFactoryTest {
    @Test
    void rejectsAnAbsentOrStaleGraphWithoutChangingTheTreatment() {
        assertThatThrownBy(() -> LocalRetrievalFactory.requireCurrentProjection(
                RetrievalMode.GRAPH, "rev-2", "rev-2", status(Optional.empty())))
                .hasMessageContaining("<not indexed>")
                .hasMessageContaining("run saaa-index update")
                .hasMessageContaining("retrieval treatment was not changed");

        assertThatThrownBy(() -> LocalRetrievalFactory.requireCurrentProjection(
                RetrievalMode.HYBRID, "rev-2", "rev-2", status(Optional.of("rev-1"))))
                .hasMessageContaining("Neo4j projection revision rev-1")
                .hasMessageContaining("repository revision rev-2")
                .hasMessageContaining("HYBRID retrieval");
    }

    @Test
    void rejectsAQueryWhoseRepositoryChangedBeforeRetrieval() {
        assertThatThrownBy(() -> LocalRetrievalFactory.requireCurrentProjection(
                RetrievalMode.VECTOR, "rev-1", "rev-2", status(Optional.of("rev-1"))))
                .hasMessageContaining("query revision rev-1")
                .hasMessageContaining("current repository revision rev-2")
                .hasMessageContaining("reconstruct the query");
    }

    @Test
    void acceptsOnlyAnExactRevisionMatch() {
        assertThatCode(() -> LocalRetrievalFactory.requireCurrentProjection(
                RetrievalMode.GRAPH, "rev-2", "rev-2", status(Optional.of("rev-2"))))
                .doesNotThrowAnyException();
    }

    private static ProjectionStatus status(Optional<String> revision) {
        return new ProjectionStatus("fixture", revision, Optional.of("graph-schema-v1"), 1, 0);
    }
}
