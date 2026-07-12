# Agentic Team Project Template

A reusable, opinionated starting point for software projects where AI agents
reduce decision fatigue through evidence-driven `/init`, concise semantic
state, composable skills, Nix-owned developer tooling, thin vertical slices,
test-first work, optional Superpowers workflow support, and practical Clean
Architecture boundaries.

This is the **source template**. Generated projects must no longer present
themselves as this template after `/init` completes. See
[`.agentic-template/templates/README_TEMPLATE.md`](.agentic-template/templates/README_TEMPLATE.md)
for the required structure of a generated project README.

It is not an internal developer platform. It does not install a default
database, broker, cloud emulator, Kubernetes stack, Java, Python, Node, Elixir
or Godot. Those choices are inferred from project evidence when the template is
instantiated.

## Contents

- [Agentic Team Project Template](#agentic-team-project-template)
  - [Contents](#contents)
  - [Start](#start)
    - [1. Create your project from this template repo](#1-create-your-project-from-this-template-repo)
    - [2. Provide briefs, artifacts and intent](#2-provide-briefs-artifacts-and-intent)
    - [3. Start the agent-guided setup](#3-start-the-agent-guided-setup)
  - [Run locally](#run-locally)
  - [Skills and agent compatibility](#skills-and-agent-compatibility)
    - [The `init` skill](#the-init-skill)
    - [Skill categories](#skill-categories)
    - [Full skill list](#full-skill-list)
      - [Workflow skills](#workflow-skills)
      - [Discovery skills](#discovery-skills)
      - [Specialise skills](#specialise-skills)
      - [Coordination skills](#coordination-skills)
      - [Knowledge skills](#knowledge-skills)
      - [Tooling skills](#tooling-skills)
    - [Compatible agents](#compatible-agents)
    - [Agent fallback](#agent-fallback)
  - [Run with containers](#run-with-containers)
  - [Tests](#tests)
  - [Configuration and environment variables](#configuration-and-environment-variables)
  - [Infrastructure and deployment state](#infrastructure-and-deployment-state)
  - [Deliberate non-goals](#deliberate-non-goals)
  - [Important decisions and documentation links](#important-decisions-and-documentation-links)
  - [AI-assisted delivery statement](#ai-assisted-delivery-statement)

```
                      AGENTS.md
                    operating rules
                           |
                           v
                          /init
                           |
                           v
                   evidence discovery
                           |
                           v
                 PROJECT_PROFILE.toon
                           |
          +----------------+----------------+
          |                |                |
          v                v                v
        facts          inferences        unknowns
          |                |                |
          +----------------+----------------+
                           |
                           v
                 composable subskills
                           |
          +----------------+----------------+
          |                |                |
          v                v                v
       runtime          testing       persistence
          |                |                |
          +----------------+----------------+
                           |
                           v
                      specialise
                           |
                           v
                   rewrite project identity
                           |
                           v
                      validation
                           |
                           v
                     HANDOFF.toon
                           |
                 +---------+---------+
                 |                   |
                 v                   v
                wiki                ADRs
```

## Start

Use this repository as the starting point for a new project, then let the
agentic bootstrap inspect evidence before choosing runtime, persistence,
messaging, deployment or test harness details.

### 1. Create your project from this template repo

Start by creating a new project repository from this template repository.

- On GitHub, use **Use this template** and clone the generated repository.
  Choose **Private** during repository creation if the project should not be
  public.
- Locally, copy or clone this repository into a new project directory and point
  `origin` at the new project repository.

Do the remaining steps inside the new project repository, not in the template
source repository.

### 2. Provide briefs, artifacts and intent

Bring whatever project material exists. It does not need to be polished.

- Update `CUSTOMIZE_THIS_PROJECT.toon` with anything already known: name,
  purpose, users, core capabilities, non-goals and hard constraints.
- Add briefs, product notes, research notes, architecture sketches, API drafts,
  source files, package manifests, deployment constraints, screenshots or links
  to relevant external systems.
- If the intent is still fuzzy, ask the agent to interview you. It should use
  the material you provided, ask the smallest useful question set, capture
  assumptions and unknowns, then turn the answers into project state.

### 3. Start the agent-guided setup

In Claude, Codex or another coding agent, ask it to read `AGENTS.md` and run the
template bootstrap if the repo instructions were not loaded automatically. A
useful first prompt is:

```text
Help me initialise this project from the template.
If AGENTS.md or CLAUDE.md was not loaded automatically, read it first.
Run .agentic-template/bin/project init, inspect the result, and guide me through
the smallest useful next setup steps. If my project intent is unclear, interview
me and help convert my answers, briefs and artifacts into project state.
```

## Run locally

Enter the Nix development shell if you use flakes:

```sh
nix develop
```

Run the template commands locally:

```sh
.agentic-template/bin/project init
.agentic-template/bin/project check
.agentic-template/bin/project ready
.agentic-template/bin/project doctor
```

`/init` has these phases:

```text
discover
  -> decide
  -> specialise
  -> rewrite top-level project identity
  -> validate
  -> update handoff
```

Mandatory postconditions after `/init`:

- `PROJECT_PROFILE.toon.project.state` is not `template`.
- `README.md` is project-facing.
- `AGENTS.md` is project-facing.
- `CUSTOMIZE_THIS_PROJECT.toon` is marked consumed or historical.
- Runtime, testing, container, infrastructure and CI decisions are explicit.
- Applicable repository commands are specialised.
- Non-applicable commands are explicitly recorded as such.
- `HANDOFF.toon` points to real project work.

## Skills and agent compatibility

The template uses a lazy-loaded skill system. Skills live under
[`.agents/skills/`](.agents/skills/) and are routed by
[`.agents/skills/CATALOG.toon`](.agents/skills/CATALOG.toon). An agent loads only
the skills relevant to the current phase rather than loading everything at once.

### The `init` skill

The [`init`](.agents/skills/init/SKILL.md) skill orchestrates the full
specialisation lifecycle. It is the entry point for transforming the template
into a specialised project. See
[`AGENTS_TEMPLATE.md`](.agentic-template/templates/AGENTS_TEMPLATE.md) for the
required structure of a generated project `AGENTS.md`.

### Skill categories

| Category | Skills | Purpose |
|---|---|---|
| Workflow | `init`, `discover`, `plan-task`, `specify`, `test-first`, `implement-slice`, `test-change`, `update-handoff`, `handoff-maintenance`, `reassess-project-profile`, `reconcile-delivery`, `architecture-review`, `red-team`, `promote-knowledge` | Orchestrate discovery, planning, implementation, review and delivery |
| Discovery | `detect-project-shape`, `detect-runtime`, `detect-application-shape`, `detect-messaging`, `detect-persistence`, `detect-infrastructure`, `capture-unknowns` | Inspect repository evidence and classify unknowns |
| Specialise | `runtime-node`, `runtime-java`, `runtime-python`, `runtime-elixir`, `runtime-godot`, `messaging-kafka`, `persistence-sql`, `persistence-document`, `persistence-redis`, `sql-migrations`, `test-harness`, `container-build`, `infra-local-compose`, `infra-decision`, `infra-aws`, `infra-fly`, `ci` | Specialise runtime, persistence, messaging, testing, containers, infrastructure and CI |
| Coordination | `team-selection`, `agent-team-fallback` | Select agent roles and degrade gracefully |
| Knowledge | `knowledge-search`, `knowledge-capture` | Search and capture durable project knowledge |
| Tooling | `detect-mcp`, `detect-superpowers`, `model-routing` | Inspect MCP, Superpowers and model routing |

### Full skill list

Every skill has a `SKILL.md` with frontmatter (`name`, `description`) and is
lazy-loaded via [`CATALOG.toon`](.agents/skills/CATALOG.toon).

#### Workflow skills

| Skill | Path | Trigger |
|---|---|---|
| `init` | [`init/SKILL.md`](.agents/skills/init/SKILL.md) | project profile absent or shape unknown |
| `discover` | [`workflow/discover/SKILL.md`](.agents/skills/workflow/discover/SKILL.md) | evidence gathering needed |
| `plan-task` | [`workflow/plan-task/SKILL.md`](.agents/skills/workflow/plan-task/SKILL.md) | bounded task planning |
| `specify` | [`workflow/specify/SKILL.md`](.agents/skills/workflow/specify/SKILL.md) | meaningful feature or behaviour change |
| `test-first` | [`workflow/test-first/SKILL.md`](.agents/skills/workflow/test-first/SKILL.md) | meaningful behaviour change |
| `implement-slice` | [`workflow/implement-slice/SKILL.md`](.agents/skills/workflow/implement-slice/SKILL.md) | thin slice implementation |
| `test-change` | [`workflow/test-change/SKILL.md`](.agents/skills/workflow/test-change/SKILL.md) | test modification |
| `update-handoff` | [`workflow/update-handoff/SKILL.md`](.agents/skills/workflow/update-handoff/SKILL.md) | work state changed |
| `handoff-maintenance` | [`workflow/handoff-maintenance/SKILL.md`](.agents/skills/workflow/handoff-maintenance/SKILL.md) | active work state changed |
| `reassess-project-profile` | [`workflow/reassess-project-profile/SKILL.md`](.agents/skills/workflow/reassess-project-profile/SKILL.md) | material profile assumption changed |
| `reconcile-delivery` | [`workflow/reconcile-delivery/SKILL.md`](.agents/skills/workflow/reconcile-delivery/SKILL.md) | before delivery PR or scope change |
| `architecture-review` | [`workflow/architecture-review/SKILL.md`](.agents/skills/workflow/architecture-review/SKILL.md) | architecture review needed |
| `red-team` | [`workflow/red-team/SKILL.md`](.agents/skills/workflow/red-team/SKILL.md) | adversarial review needed |
| `promote-knowledge` | [`workflow/promote-knowledge/SKILL.md`](.agents/skills/workflow/promote-knowledge/SKILL.md) | knowledge promotion |

#### Discovery skills

| Skill | Path | Trigger |
|---|---|---|
| `detect-project-shape` | [`discovery/detect-project-shape/SKILL.md`](.agents/skills/discovery/detect-project-shape/SKILL.md) | repository shape unknown |
| `detect-runtime` | [`discovery/detect-runtime/SKILL.md`](.agents/skills/discovery/detect-runtime/SKILL.md) | project runtime unknown |
| `detect-application-shape` | [`discovery/detect-application-shape/SKILL.md`](.agents/skills/discovery/detect-application-shape/SKILL.md) | runtime or framework detected |
| `detect-messaging` | [`discovery/detect-messaging/SKILL.md`](.agents/skills/discovery/detect-messaging/SKILL.md) | messaging evidence or requirement |
| `detect-persistence` | [`discovery/detect-persistence/SKILL.md`](.agents/skills/discovery/detect-persistence/SKILL.md) | persistence unknown |
| `detect-infrastructure` | [`discovery/detect-infrastructure/SKILL.md`](.agents/skills/discovery/detect-infrastructure/SKILL.md) | deployment or local topology unknown |
| `capture-unknowns` | [`discovery/capture-unknowns/SKILL.md`](.agents/skills/discovery/capture-unknowns/SKILL.md) | material unknown discovered |

#### Specialise skills

| Skill | Path | Trigger |
|---|---|---|
| `runtime-node` | [`specialise/runtime-node/SKILL.md`](.agents/skills/specialise/runtime-node/SKILL.md) | Node or TypeScript detected |
| `runtime-java` | [`specialise/runtime-java/SKILL.md`](.agents/skills/specialise/runtime-java/SKILL.md) | Java detected |
| `runtime-python` | [`specialise/runtime-python/SKILL.md`](.agents/skills/specialise/runtime-python/SKILL.md) | Python detected |
| `runtime-elixir` | [`specialise/runtime-elixir/SKILL.md`](.agents/skills/specialise/runtime-elixir/SKILL.md) | Elixir detected |
| `runtime-godot` | [`specialise/runtime-godot/SKILL.md`](.agents/skills/specialise/runtime-godot/SKILL.md) | Godot detected |
| `messaging-kafka` | [`specialise/messaging-kafka/SKILL.md`](.agents/skills/specialise/messaging-kafka/SKILL.md) | Kafka active role detected |
| `persistence-sql` | [`specialise/persistence-sql/SKILL.md`](.agents/skills/specialise/persistence-sql/SKILL.md) | persistent relational database |
| `persistence-document` | [`specialise/persistence-document/SKILL.md`](.agents/skills/specialise/persistence-document/SKILL.md) | document database selected |
| `persistence-redis` | [`specialise/persistence-redis/SKILL.md`](.agents/skills/specialise/persistence-redis/SKILL.md) | Redis cache or coordination selected |
| `sql-migrations` | [`specialise/sql-migrations/SKILL.md`](.agents/skills/specialise/sql-migrations/SKILL.md) | persistent relational database |
| `test-harness` | [`specialise/test-harness/SKILL.md`](.agents/skills/specialise/test-harness/SKILL.md) | runtime and application shape known |
| `container-build` | [`specialise/container-build/SKILL.md`](.agents/skills/specialise/container-build/SKILL.md) | deployable service or explicit container decision |
| `infra-local-compose` | [`specialise/infra-local-compose/SKILL.md`](.agents/skills/specialise/infra-local-compose/SKILL.md) | local runtime topology requires services |
| `infra-decision` | [`specialise/infra-decision/SKILL.md`](.agents/skills/specialise/infra-decision/SKILL.md) | infrastructure decision required after init |
| `infra-aws` | [`specialise/infra-aws/SKILL.md`](.agents/skills/specialise/infra-aws/SKILL.md) | AWS deployment evidence |
| `infra-fly` | [`specialise/infra-fly/SKILL.md`](.agents/skills/specialise/infra-fly/SKILL.md) | Fly deployment evidence |
| `ci` | [`specialise/ci/SKILL.md`](.agents/skills/specialise/ci/SKILL.md) | repository commands known |

#### Coordination skills

| Skill | Path | Trigger |
|---|---|---|
| `team-selection` | [`coordination/team-selection/SKILL.md`](.agents/skills/coordination/team-selection/SKILL.md) | task requires role selection |
| `agent-team-fallback` | [`coordination/agent-team-fallback/SKILL.md`](.agents/skills/coordination/agent-team-fallback/SKILL.md) | preferred team topology unavailable |

#### Knowledge skills

| Skill | Path | Trigger |
|---|---|---|
| `knowledge-search` | [`knowledge/knowledge-search/SKILL.md`](.agents/skills/knowledge/knowledge-search/SKILL.md) | before planning or implementation |
| `knowledge-capture` | [`knowledge/knowledge-capture/SKILL.md`](.agents/skills/knowledge/knowledge-capture/SKILL.md) | after meaningful work |

#### Tooling skills

| Skill | Path | Trigger |
|---|---|---|
| `detect-mcp` | [`tooling/detect-mcp/SKILL.md`](.agents/skills/tooling/detect-mcp/SKILL.md) | MCP expectations declared |
| `detect-superpowers` | [`tooling/detect-superpowers/SKILL.md`](.agents/skills/tooling/detect-superpowers/SKILL.md) | tooling inspection |
| `model-routing` | [`tooling/model-routing/SKILL.md`](.agents/skills/tooling/model-routing/SKILL.md) | delegation or model choice needed |

### Compatible agents

The template works with any coding agent that can read `AGENTS.md` and execute
shell commands. It is designed for and tested with:

- **Claude** (Anthropic) — reads `CLAUDE.md` (symlink to `AGENTS.md`) and
  `.claude/` hints.
- **Codex** (OpenAI) — reads `.codex/` hints.
- **GitHub Copilot** — reads `.github/copilot-instructions.md`, which points to
  `AGENTS.md`.
- **Any OpenAI-compatible or large-context model** — via direct prompting with
  the first-prompt template above.

The template does not require Superpowers, MCP servers, or external model
access. It works in CI without interactive AI tooling.

### Agent fallback

When a preferred agent topology is unavailable, the template degrades
gracefully through the [`agent-team-fallback`](.agents/skills/coordination/agent-team-fallback/SKILL.md)
skill:

1. Persistent agent team with bounded role ownership.
2. Independent subagents.
3. Sequential role passes.
4. Single lead agent using an explicit review checklist.

A single agent must never be presented as equivalent to independent review.

## Run with containers

The template ships with an empty `compose.yaml`. Generated projects make an
explicit container decision during `/init`:

- Deployable services default to a tested application image.
- Libraries, mobile apps, desktop apps and Godot projects may record
  `not_applicable` with a reason.

Commands:

```sh
.agentic-template/bin/project compose-config   # validate topology
.agentic-template/bin/project compose-test     # bounded smoke test
.agentic-template/bin/project image            # build image (when specialised)
.agentic-template/bin/project image-test       # build, start, smoke, stop, cleanup
```

## Tests

Template-level tests use the fixture-driven self-test:

```sh
.agentic-template/bin/project self-test
```

Generated projects specialise test commands during `/init`:

```sh
.agentic-template/bin/project test
.agentic-template/bin/project integration-test
.agentic-template/bin/project contract-test
.agentic-template/bin/project component-test
.agentic-template/bin/project e2e-test
```

Unspecialised test commands fail clearly and point to
`.agentic-template/bin/project init`.

## Configuration and environment variables

| Variable | Purpose |
|---|---|
| `NIX_PATH` | Optional; used when entering `nix develop` |
| `HOME` | Standard; used for git config |

No secrets or credentials are required for template-level validation. Generated
projects record their own environment variables in their project-facing README.

## Infrastructure and deployment state

Every project explicitly records:

```toon
infrastructure:
  local_topology: ...
  deployment_target: ...
  iac:
    status: required | deferred | not_applicable
    tool: ...
    validation_command: ...
    revisit_trigger: ...
```

Commands:

```sh
.agentic-template/bin/project infra-check   # formatting and static validation
```

The template never automatically applies infrastructure from CI and does not
require cloud credentials for ordinary IaC validation.

## Deliberate non-goals

- Not an internal developer platform.
- No default database, broker, Kubernetes, cloud emulator or cloud platform.
- No interactive AI tooling required in CI.
- No provider-specific paths merged into shared abstractions without explicit
  request.

## Important decisions and documentation links

| Document | Purpose |
|---|---|
| [`AGENTS.md`](AGENTS.md) | Canonical operating contract |
| [`PROJECT_PROFILE.toon`](PROJECT_PROFILE.toon) | Current project facts, inferences, decisions and unknowns |
| [`HANDOFF.toon`](HANDOFF.toon) | Current semantic work state |
| [`CUSTOMIZE_THIS_PROJECT.toon`](CUSTOMIZE_THIS_PROJECT.toon) | Bootstrap contract for generated projects |
| [`.agents/skills/CATALOG.toon`](.agents/skills/CATALOG.toon) | Lazy-loading skill router |
| [`.agents/knowledge/`](.agents/knowledge/) | Durable knowledge, questions, learnings and proposals |
| [`.agentic-template/bin/project`](.agentic-template/bin/project) | Hidden boilerplate command dispatcher |
| [`.agentic-template/templates/README_TEMPLATE.md`](.agentic-template/templates/README_TEMPLATE.md) | Required structure for a generated project README |
| [`.agentic-template/templates/AGENTS_TEMPLATE.md`](.agentic-template/templates/AGENTS_TEMPLATE.md) | Required structure for a generated project AGENTS.md |
| [`.worktrees/README.md`](.worktrees/README.md) | Agent worktree rules |
| [`docs/decisions/`](docs/decisions/) | Architecture decision records |

## AI-assisted delivery statement

This template is designed for AI-assisted delivery. Agents read `AGENTS.md`
as the source of truth, use `.agents/skills/CATALOG.toon` for lazy-loaded
specialisation, and record state in `PROJECT_PROFILE.toon` and `HANDOFF.toon`.
The `/init` lifecycle transforms the template into a specialised, truthful,
delivered project with executable commands and tested documentation.
