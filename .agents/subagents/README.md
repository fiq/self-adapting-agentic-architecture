# Bounded Subagents

Subagents perform bounded tasks with limited context and concise semantic
outputs. They stop when the task is complete.

## Using a subagent as a review lens

A bounded reviewer role is a lens as much as an actor. Naming *red-team-reviewer
+ test-reviewer* in a brief, and pointing at these files, produces sharper review
than asking for "a review" — the role carries what to look for, what not to
rewrite, and when not to be invoked at all. Pair a role with a **region** of the
change so several passes stay additive rather than converging. See
`dispatching-review-passes`.
