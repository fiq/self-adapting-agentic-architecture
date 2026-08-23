package com.dreamthought.saaa.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.FitnessScore;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ConsoleReporterTest {
    @Test
    void roundsRawMagnitudeOnlyWhenRenderingTheConsoleReport() {
        var output = new StringWriter();
        var reporter = new ConsoleReporter(new PrintWriter(output));
        var result = new FitnessResult(
                new Candidate("candidate-1", "mutation-1", "candidate/1", Path.of("/tmp/candidate"), "abc123"),
                new EvaluationEvidence(List.of(), List.of(), Instant.EPOCH), Map.of(),
                new FitnessScore(new BigDecimal("0.5949"), FitnessDecision.DISCARD));

        reporter.scored(result);

        assertThat(output.toString()).isEqualTo("  score      0.59\n  DISCARD\n");
        assertThat(result.fitnessScore().rawMagnitude()).isEqualByComparingTo("0.5949");
    }
}
