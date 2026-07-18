# Project Knowledge Graph Taxonomy

Knowledge entries, specs, ADRs and wiki pages form one connected graph. Agents
traverse it (via `knowledge-search`) before acting and link every new durable
artifact back into it. Edges must resolve to existing nodes.

## Node types

| Node | Where | ID / key |
|---|---|---|
| Domain | `.agents/knowledge/domains/` | `DOM-` |
| System | `.agents/knowledge/systems/` | `SYS-` |
| Contract | `.agents/knowledge/contracts/` | `CON-` |
| Architecture | `.agents/knowledge/architecture/` | `ARCH-` |
| Decision (ADR) | `.agents/knowledge/decisions/` and `docs/decisions/` | `ADR-` |
| Pattern | `.agents/knowledge/patterns/` | `PAT-` |
| Risk | `.agents/knowledge/risks/` | `RISK-` |
| Question | `.agents/knowledge/questions/` | `Q-` |
| Learning | `.agents/knowledge/learnings/` | `LRN-` |
| Inbox proposal | `.agents/knowledge/inbox/` | `INBOX-` |
| Capability | `specs/capabilities/` | `CAP-` |
| Change | `specs/changes/<id>/` | `CHG-` |
| Wiki page | `docs/wiki/` | file name |

## Edge types

Declared in knowledge frontmatter and spec `relates_to`:

```
relates_to      general association
depends_on      needs another node to function
consumes        uses a contract or system
produces        emits a contract or output
decisions       governed by an ADR
patterns        applies a pattern
risks           exposed to a risk
open_questions  blocked or informed by a question
supersedes / superseded_by / contradicts   lifecycle edges
```

## Rules

- Knowledge → knowledge edges resolve (enforced by `check-knowledge`).
- Spec `relates_to` → knowledge ids resolve (enforced by `check-changes`).
- Wiki → knowledge references should resolve (`check-wiki` warns on drift).
- Prefer linking to an existing node over creating a duplicate.
- Non-canonical nodes are input, not authority (see `index.md` status values).
