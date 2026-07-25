---
name: worktree-status
description: Show active agent worktrees, branches, owners and HEAD commits without modifying repository state.
---

# Worktree Status

Use this when the user asks what agents or branches are currently active.

Run:

```sh
.agentic-template/bin/project worktree-status
```

Report only observable git and handoff state. If `HANDOFF.toon` and git
disagree, flag the discrepancy; do not resolve it automatically.

Do not modify files, remove worktrees or infer hidden task status.
