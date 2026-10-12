package com.dreamthought.saaa.adapters.evolve;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.deterministic.EvolutionReporter;
import com.dreamthought.saaa.domain.AgentUsage;
import com.dreamthought.saaa.domain.Candidate;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CompositeReporterTest {
    @Test
    void forwardsObservedUsageToEveryReporter() {
        var received = new ArrayList<String>();
        EvolutionReporter first = recording("first", received);
        EvolutionReporter second = recording("second", received);
        var candidate = new Candidate("candidate-1", "MUT-1", "candidate/toy-1",
                Path.of("/tmp/worktree"), "abc1234");

        new CompositeReporter(List.of(first, second)).usageObserved(candidate, AgentUsage.none());

        assertThat(received).containsExactly("first:candidate-1", "second:candidate-1");
    }

    private static EvolutionReporter recording(String name, List<String> received) {
        return new EvolutionReporter() {
            @Override
            public void usageObserved(Candidate candidate, AgentUsage usage) {
                received.add(name + ":" + candidate.id());
            }
        };
    }
}
