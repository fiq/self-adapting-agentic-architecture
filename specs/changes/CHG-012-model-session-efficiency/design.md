# CHG-012 design

## Decision

Put the detailed policy in `.agents/coordination/MODEL_ROUTING_POLICY.md`, the
location already required by repository contract checks and used for agent
coordination. Keep a concise operational version in `AGENTS.md`, durable
pattern knowledge in `PAT-002`, and user-facing explanations in the wiki.

`check-repo-contract` requires the policy's four sections and its links to
Q-010, Q-011, ARCH-001 and RISK-001. This is a lightweight conformance guard:
it verifies that the non-negotiable controls remain discoverable, while human
review evaluates whether the prose is sufficient for new provider evidence.

## Trade-offs

Long-lived sessions reduce repeated stable context only when provider caching is
available. They can also carry irrelevant conclusions into a changed task. The
policy therefore permits reuse only within a fixed objective/role/permission
scope and requires a fresh reviewer session. It records unavailable telemetry
rather than manufacturing a cost model, preserving Q-010's evidence threshold
for automatic routing.
