package com.dreamthought.saaa.domain;

/** The audited route selected before an agent is invoked; it records intent, not provider proof. */
public record AgentRoute(String provider, String model, String tier, String reason) {
    public AgentRoute {
        provider = Require.nonBlank(provider, "provider");
        model = Require.nonBlank(model, "model");
        tier = Require.nonBlank(tier, "tier");
        reason = Require.nonBlank(reason, "reason");
    }
}
