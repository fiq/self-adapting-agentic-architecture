# CHG-027: Assessment lenses, including user experience and infrastructure

## Why

Assessment in this repository has been strong on correctness and architecture and
silent on everything else. The mutation rule, the fitness functions and the
consolidation rules all point at whether the code is right and whether the
boundaries hold. Nothing said to look at what a person sees, and nothing said to
look at the build and CI as a thing with its own quality.

Silence is the problem rather than absence of skill. A review that never
considered a dimension reads identically to one that considered it and found
nothing, so a whole surface can go unexamined while the write-up looks thorough.

Architecture needed nothing new: `check-architecture-boundaries` already runs it
as a fitness function, the `review-loop` skill already names it, and
`architecture-review` exists as its own skill. Naming it in the lens list makes
the set complete rather than adding a control.

## Intent

Record the standing assessment lenses in `AGENTS.md` — correctness,
architecture, quality and debt, user experience, infrastructure quality — with
the rule that each is either applied or declared not applicable, and guard the
list with a repository-contract control so a lens cannot be dropped unnoticed.

## What user experience means here

The consumer is a developer-researcher driving a local CLI, so the surface is
concrete: `saaa` commands, flags and help text, `ConsoleReporter` output, error
messages, the journal a run appends to, and the README. The test the lens applies
is deliberately about failure rather than about polish — whether a person who
hits an error can tell what happened and what to do next, whether the default is
the safe path, and whether a flag's name means what it does.

This is immediately live rather than theoretical: `CHG-026` T4 adds a flag asking
for a generation of N candidates, and that flag is the first thing the lens
should be applied to.

## What the infrastructure lens can honestly reach today

This repository has no deployment target and an empty Compose topology, and its
own contract says container images, Compose services and infrastructure as code
are not applicable until distribution or remote execution is chosen. So the lens
reaches CI workflows and the Nix and Gradle build, and nothing else. The
proposal says that in the contract rather than leaving a lens that would invite a
review of infrastructure that does not exist.

## Non-goals

- any check that claims to observe that a lens was actually applied. The control
  is presence of the rule, exactly as the mutation-evidence controls are, and
  `CHG-020` already records that limit;
- a UX review of the existing CLI surface, which is its own change;
- adding infrastructure so that the infrastructure lens has more to look at.

## Related knowledge

`ADR-0002`, `ARCH-001`, `PAT-003`, `PAT-004`.
