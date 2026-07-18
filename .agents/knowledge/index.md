# Knowledge Index

Search this index first, then load only relevant entries. The full graph — how
knowledge, specs, ADRs and wiki pages connect — is defined in
[`TAXONOMY.md`](TAXONOMY.md).

| Category | Folder | ID Prefix | Purpose |
|---|---|---:|---|
| Domains | `domains/` | `DOM-` | Business concepts, invariants and ownership |
| Systems | `systems/` | `SYS-` | Apps, services, agents, pipelines and dependencies |
| Contracts | `contracts/` | `CON-` | APIs, events, schemas, files and behaviours |
| Architecture | `architecture/` | `ARCH-` | Boundaries, flows and structural rules |
| Decisions | `decisions/` | `ADR-` | Durable choices and consequences |
| Patterns | `patterns/` | `PAT-` | Reusable engineering or operating practices |
| Risks | `risks/` | `RISK-` | Failure modes and concerns |
| Questions | `questions/` | `Q-` | Known unknowns requiring action |
| Learnings | `learnings/` | `LRN-` | Evidence-backed discoveries |
| Inbox | `inbox/` | `INBOX-` | Unreviewed proposals |

## Status Values

- `canonical`: reviewed and currently trusted
- `proposed`: plausible but not yet validated
- `experimental`: intentionally temporary or under evaluation
- `deprecated`: retained but no longer recommended
- `superseded`: replaced by another entry
- `stale`: review date exceeded or evidence no longer reliable

Agents must treat non-canonical entries as input, not authority.
