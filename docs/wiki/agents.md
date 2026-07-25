# Agents

`AGENTS.md` is canonical. Claude, Codex, Copilot and other model instructions
should point back to it.

For a fresh session, run `.agentic-template/bin/project startup` first. It
prints an ASCII welcome, startup sequence, options, `AGENTS.md` from disk and
the follow-on state files required for non-trivial work.

Native adapters are intentionally thin:

- Claude: `.claude/skills/agentic-template/SKILL.md`
- Cursor: `.cursor/rules/agentic-startup-and-skills.mdc`
- Copilot: `.github/copilot-instructions.md`
- Codex: `.codex/README.md`

The canonical skills remain under `.agents/skills/` and are indexed by
`.agents/skills/CATALOG.toon`. The startup adapter pattern is tracked as
`INBOX-007` until it has enough generated-project evidence to promote.

Use persistent roles for continuing responsibility and subagents for bounded
work. Keep context focused and summarise outputs semantically.

Before planning or implementation, agents should search `.agents/knowledge/`
through the `knowledge-search` skill. Knowledge, specs, ADRs and wiki form one
graph (`.agents/knowledge/TAXONOMY.md`); link new artifacts back into it by id.
After meaningful work, use `knowledge-capture` to decide whether discoveries
belong in `HANDOFF.toon`, the knowledge inbox, questions, learnings, decisions,
patterns, risks or no durable record.

Lifecycle skills (lazy-loaded via `.agents/skills/CATALOG.toon`):

- `calibrate-audience` — skill level, app shape, recorded right-sizing;
- `ideate` / `narrative-intake` — idea or narrative → validated structured change;
- `outside-in-tdd` — boundary-in ATDD from change scenarios;
- `review-loop` — boy-scout clean-up, smells and coupling;
- `wiki-tidy` — keep the wiki current against the graph.

At hard choices, attribute each persona's stance as discourages / accepts /
encourages; the lead synthesises without forcing consensus.

Superpowers remains the preferred workflow layer for brainstorming, planning,
TDD, debugging, implementation, review and verification when it is available.
It is optional: Claude, Codex, Copilot and CI must still be able to start from
`AGENTS.md`, `PROJECT_PROFILE.toon`, `HANDOFF.toon`, the structured-data policy
and repository commands
without Superpowers.
