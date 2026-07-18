# Agents

`AGENTS.md` is canonical. Claude, Codex, Copilot and other model instructions
should point back to it.

Use persistent roles for continuing responsibility and subagents for bounded
work. Keep context focused and compress outputs.

Before planning or implementation, agents should search `.agents/knowledge/`
through the `knowledge-search` skill. Knowledge, specs, ADRs and wiki form one
graph (`.agents/knowledge/TAXONOMY.md`); link new artifacts back into it by id.
After meaningful work, use `knowledge-capture` to decide whether discoveries
belong in `HANDOFF.toon`, the knowledge inbox, questions, learnings, decisions,
patterns, risks or no durable record.

Lifecycle skills (lazy-loaded via `.agents/skills/CATALOG.toon`):

- `calibrate-audience` — skill level, app shape, recorded right-sizing;
- `ideate` / `narrative-intake` — idea or narrative → validated `change.toon`;
- `outside-in-tdd` — boundary-in ATDD from change scenarios;
- `review-loop` — boy-scout clean-up, smells and coupling;
- `wiki-tidy` — keep the wiki current against the graph.

At hard choices, attribute each persona's stance as discourages / accepts /
encourages; the lead synthesises without forcing consensus.

Superpowers remains the preferred workflow layer for brainstorming, planning,
TDD, debugging, implementation, review and verification when it is available.
It is optional: Claude, Codex, Copilot and CI must still be able to start from
`AGENTS.md`, `PROJECT_PROFILE.toon`, `HANDOFF.toon` and repository commands
without Superpowers.
