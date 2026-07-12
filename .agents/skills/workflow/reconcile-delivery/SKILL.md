---
name: reconcile-delivery
description: Reconcile planned architecture and acceptance criteria against what was actually delivered.
---

# Reconcile Delivery

## Outcome

Ensure documentation, specs, profiles and handoff state truthfully reflect
the delivered repository, not stale intentions.

## When to trigger

- before a delivery PR;
- after material scope changes;
- before declaring the project delivered;
- when `project ready` reports documentation drift.

## Procedure

1. Read `PROJECT_PROFILE.toon`, `HANDOFF.toon`, `README.md`, `AGENTS.md`.
2. Read active specs under `specs/active/`.
3. Read architecture overview under `docs/architecture/`.
4. Read ADR summaries under `docs/decisions/`.
5. Walk the repository structure and compare against documented claims.
6. Classify each acceptance item:

   | classification | meaning                                                |
   |----------------|--------------------------------------------------------|
   | `delivered`    | present, tested, documented                            |
   | `changed`      | delivered but differs from the original spec           |
   | `deferred`     | intentionally postponed with a recorded revisit       |
   | `removed`      | intentionally dropped with a recorded reason           |
   | `missing`      | expected by a spec or README but absent without reason |

7. For each `missing` item, either implement it, move it to `deferred`, or
   update the spec to `removed` with a reason.
8. Update:

   - `README.md` runtime architecture, repository structure and delivery state;
   - `AGENTS.md` canonical commands and architecture rules;
   - `PROJECT_PROFILE.toon` facts, inferences and decisions;
   - `HANDOFF.toon` current objective and next actions;
   - architecture overview under `docs/architecture/`;
   - ADR links under `docs/decisions/`;
   - active specification status under `specs/active/`.

9. Move completed specs from `specs/active/` to `specs/delivered/`, or mark
   them delivered in place.

## Ready gate

`project ready` must fail when:

- README or AGENTS still describes the project as a template;
- documentation references missing services or files;
- active specs claim major components that are not delivered or explicitly
  deferred;
- commands documented in README do not exist.

## Do not

- Leave stale architecture claims in the README.
- Keep delivered specs in `specs/active/` without a delivered marker.
- Pretend a deferred component is delivered.
