---
name: backlog-status
description: Show current in-progress and next work from HANDOFF.toon with claimed worktree context.
---

# Backlog Status

Use this when the user asks what is in progress, what is next, or what work is
currently claimed.

Run:

```sh
.agentic-template/bin/project backlog
```

Present `in_progress`, `next` and claimed `.worktrees/` entries exactly from
repository evidence. Do not invent priority beyond `HANDOFF.toon`.
