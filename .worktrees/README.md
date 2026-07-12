# Worktrees

This directory holds agent worktrees for bounded parallel work.

## Rules

- One bounded issue and branch per agent worktree.
- One mutable worktree per agent.
- Coordination checkout is not used for delegated implementation.
- Never remove a dirty worktree.
- Verify commit and push state before cleanup.
- Human or lead agent owns integration and merge.
- Direct commits to `main` require explicit user authorisation.
- Force-push requires explicit authorisation and never targets `main`.

## Naming

Use a descriptive branch name for each worktree, for example:

```
git worktree add .worktrees/<branch-name> -b <branch-name>
```

## Cleanup

Before removing a worktree:

1. confirm the branch is pushed;
2. confirm the work is merged or explicitly archived;
3. confirm the working tree is clean;
4. remove with `git worktree remove .worktrees/<branch-name>`.
