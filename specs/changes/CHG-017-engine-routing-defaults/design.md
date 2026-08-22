# CHG-017 design

## Why it lives in the session policy

`MODEL_ROUTING_POLICY.md` already governs how a session is used. Which engine
receives the work is the same decision one step earlier, and splitting them
across two documents would mean two places to keep consistent.

The guard follows the shape `check_model_routing_policy` already uses: phrases
matched inside their owning section, whitespace-compacted, so a control cannot
migrate out of its section and still pass, and each failure names the scenario
test id.

## What the section deliberately does not contain

**Prices.** They move, and a document that records them is wrong within weeks.
The section states the *shape* of the economics — cached prefix tokens are
roughly an order of magnitude cheaper than fresh input — which is stable enough
to reason from and does not date.

**A decision procedure.** The rows are defaults a person reaches for, not a
lookup table an agent executes. `Q-010` and `Q-011` defer automatic routing, and
the first line of the section says nothing selects an engine at runtime so the
distinction survives a careless reading.

## The one row that is not about cost

Spec authorship, merge decisions and anything touching the deterministic boundary
stay with the lead. That is not an efficiency judgement; those decisions must be
defensible and someone must be accountable for them. `ARCH-001` already forbids a
model approving its own result, and routing that work outward would erode the
same boundary from a different direction.
