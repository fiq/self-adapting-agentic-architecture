# Claude Compatibility

Run `.agentic-template/bin/project startup` first, or read `../AGENTS.md` from
disk yourself. Confirm that `../AGENTS.md` was read from disk, then continue.
Review the startup sequence and options before choosing the next command.
`../AGENTS.md` is the canonical repository operating contract.

Claude-specific commands or settings in this directory must stay thin and point
back to `AGENTS.md`, `HANDOFF.toon`, `PROJECT_PROFILE.toon` and relevant skills.

The native Claude skill adapter is `.claude/skills/agentic-template/SKILL.md`.
It points back to `.agents/skills/CATALOG.toon`; do not duplicate the canonical
skill tree here.
