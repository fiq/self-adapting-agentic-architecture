package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.AgentRequest;
import com.dreamthought.saaa.domain.AgentRunResult;

/** Provider-neutral port for one bounded agent invocation. */
@FunctionalInterface
public interface AgentHarness {
    AgentRunResult run(AgentRequest request);
}
