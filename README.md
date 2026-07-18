# Agentic Team Project Template

A reusable, opinionated starting point for software projects where AI agents
reduce decision fatigue through evidence-driven `/specialise`, concise semantic
state, composable skills, Nix-owned developer tooling, thin vertical slices,
test-first work, optional Superpowers workflow support, and practical Clean
Architecture boundaries.

This is the **source template**. Generated projects must no longer present
themselves as this template after `/specialise` completes. See
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
  - [Development lifecycle](#development-lifecycle)
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
    - [Agent personas](#agent-personas)
      - [Persistent team roles](#persistent-team-roles)
      - [Bounded subagents](#bounded-subagents)
    - [Agent topologies](#agent-topologies)
      - [1. Single lead agent (linear)](#1-single-lead-agent-linear)
      - [2. Sequential role passes](#2-sequential-role-passes)
      - [3. Independent subagents](#3-independent-subagents)
      - [4. Persistent agent team](#4-persistent-agent-team)
      - [Creating new persistent roles](#creating-new-persistent-roles)
      - [Creating new subagents](#creating-new-subagents)
      - [Creating new skills](#creating-new-skills)
    - [`/sudo` — perform an operation as a persona](#sudo--perform-an-operation-as-a-persona)
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
                      /specialise
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

## Development lifecycle

Work flows through a small, connected loop. Agents drive it; humans steer at
decision points.

```
/specialise ─► calibrate audience + app shape (plain language), right-size
                 the architecture as a recorded, bought-into choice
     ▼
/ideate ─────► short-cycle multi-persona loop → specs/changes/<id>/change.toon
    (idea or     Intent → Boundary → Delivery → Quality gate
    narrative)   (a narrative, inline or file, is taken in via narrative-intake)
     ▼
outside-in ► write the acceptance test for each WHEN/THEN scenario first,
   ATDD        fidelity by risk (acceptance / component-integration / subcutaneous)
     ▼
/review ────► bounded boy-scout clean-up: code, language and architectural
                 smells, inappropriate coupling
     ▼
archive change → specs/capabilities/  +  wiki-tidy keeps docs and the
                 knowledge graph current
```

- **Specs** are OpenSpec-shaped, TOON-encoded and agent-first — living
  requirements in `specs/capabilities/`, proposals in `specs/changes/<id>/`
  (`proposal.md` for rationale, `change.toon` for deltas, `WHEN/THEN`
  scenarios, acceptance and tasks). Validate with `project check-changes`.
- **Quality is standing**, not a phase: boy-scout rule, reuse over duplication,
  pay in-path debt, docs land in the change, no silent TODOs.
- **Right-sizing is conscious**: the smallest sufficient architecture is chosen
  and recorded with what is deliberately excluded and when to revisit.
- **Hard choices** show each persona's stance as discourages / accepts /
  encourages; the lead synthesises without forcing consensus.
- **The knowledge graph** (`.agents/knowledge/TAXONOMY.md`) connects knowledge,
  specs, ADRs and wiki; `project check-wiki` warns on drift and
  `project install-hooks` opts into a non-blocking pre-commit reminder.

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

`/specialise` has these phases:

```text
discover
  -> decide
  -> specialise
  -> rewrite top-level project identity
  -> validate
  -> update handoff
```

Mandatory postconditions after `/specialise`:

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
| Workflow | `init`, `discover`, `plan-task`, `specify`, `ideate`, `narrative-intake`, `test-first`, `outside-in-tdd`, `implement-slice`, `test-change`, `review-loop`, `wiki-tidy`, `update-handoff`, `handoff-maintenance`, `reassess-project-profile`, `reconcile-delivery`, `architecture-review`, `red-team`, `promote-knowledge` | Orchestrate ideation, discovery, planning, ATDD, implementation, review and delivery |
| Discovery | `calibrate-audience`, `detect-project-shape`, `detect-runtime`, `detect-application-shape`, `detect-messaging`, `detect-persistence`, `detect-infrastructure`, `capture-unknowns` | Calibrate audience, inspect repository evidence and classify unknowns |
| Specialise | `runtime-node`, `runtime-java`, `runtime-python`, `runtime-elixir`, `runtime-godot`, `messaging-kafka`, `persistence-sql`, `persistence-document`, `persistence-redis`, `sql-migrations`, `test-harness`, `container-build`, `infra-local-compose`, `infra-decision`, `infra-aws`, `infra-fly`, `ci` | Specialise runtime, persistence, messaging, testing, containers, infrastructure and CI |
| Coordination | `team-selection`, `sudo`, `adversarial-debate`, `agent-team-fallback` | Select agent roles, switch personas, debate decisions, degrade gracefully |
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
| `ideate` | [`workflow/ideate/SKILL.md`](.agents/skills/workflow/ideate/SKILL.md) | ambiguous or unspecified feature request |
| `narrative-intake` | [`workflow/narrative-intake/SKILL.md`](.agents/skills/workflow/narrative-intake/SKILL.md) | narrative provided inline or as a file |
| `test-first` | [`workflow/test-first/SKILL.md`](.agents/skills/workflow/test-first/SKILL.md) | meaningful behaviour change |
| `outside-in-tdd` | [`workflow/outside-in-tdd/SKILL.md`](.agents/skills/workflow/outside-in-tdd/SKILL.md) | implementing a change scenario |
| `implement-slice` | [`workflow/implement-slice/SKILL.md`](.agents/skills/workflow/implement-slice/SKILL.md) | thin slice implementation |
| `test-change` | [`workflow/test-change/SKILL.md`](.agents/skills/workflow/test-change/SKILL.md) | test modification |
| `review-loop` | [`workflow/review-loop/SKILL.md`](.agents/skills/workflow/review-loop/SKILL.md) | before merge or boy-scout cleanup |
| `wiki-tidy` | [`workflow/wiki-tidy/SKILL.md`](.agents/skills/workflow/wiki-tidy/SKILL.md) | wiki drift or natural task boundary |
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
| `calibrate-audience` | [`discovery/calibrate-audience/SKILL.md`](.agents/skills/discovery/calibrate-audience/SKILL.md) | audience or app shape unknown or changed |
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
| `sudo` | [`coordination/sudo/SKILL.md`](.agents/skills/coordination/sudo/SKILL.md) | agent switches persona for bounded operation |
| `adversarial-debate` | [`coordination/adversarial-debate/SKILL.md`](.agents/skills/coordination/adversarial-debate/SKILL.md) | irreversible decision or reviewer disagreement |
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

### Agent personas

The template defines two classes of actor: **persistent team roles** for
continuing responsibility and **bounded subagents** for focused, stop-when-done
work. Roles are selected by risk; do not activate every role automatically.

#### Persistent team roles

| Role | Persona | Owns | Invoke when |
|---|---|---|---|
| [Product Owner](.agents/team/product-owner.md) | Protects user value and scope | intent, acceptance evidence, non-goals, thin-slice priority | user value or scope is unclear |
| [Architect](.agents/team/architect.md) | Protects system boundaries and trade-offs | architecture recommendation, dependency direction, volatility boundaries, migration impact | architecture decisions, boundary changes, integration risk |
| [Tech Lead](.agents/team/tech-lead.md) | Protects delivery and implementation coherence | thin slices, test strategy, CI alignment, implementation risks | implementation planning, test strategy, CI changes |
| [Domain Expert](.agents/team/domain-expert.md) | Brings specialised domain constraints | domain vocabulary, regulatory/safety constraints, assumption correction | domain rules affect design, regulatory or safety constraints |
| [Knowledge Curator](.agents/team/knowledge-curator.md) | Keeps durable knowledge useful and connected | knowledge review, promotion, expiry, graph relationships | task results may create reusable knowledge or contradictions |

#### Bounded subagents

Subagents perform a single bounded task with limited context and compressed
output. They stop when the task is complete.

| Subagent | Persona | Output | Invoke when |
|---|---|---|---|
| [Researcher](.agents/subagents/researcher.md) | Answers one external or documentation question | answer, sources, confidence, unknowns | local repo evidence is insufficient |
| [Repository Explorer](.agents/subagents/repository-explorer.md) | Inspects one repo area or evidence question | facts with file paths, inferences, unknowns | file set is large or unfamiliar |
| [Red-team Reviewer](.agents/subagents/red-team-reviewer.md) | Adversarial review of a bounded proposal or patch | findings, severity, evidence, fixes | high-risk decisions, public contract changes |
| [Evidence Checker](.agents/subagents/evidence-checker.md) | Verifies claims against source evidence | supported, unsupported, ambiguous claims | claims need verification against sources |
| [Test Reviewer](.agents/subagents/test-reviewer.md) | Assesses whether tests prove the requested behaviour | gaps, overclaims, useful next checks | behavioural change with test coverage |
| [Security Reviewer](.agents/subagents/security-reviewer.md) | Reviews security-sensitive change or design | findings, severity, exploit sketch, fix direction | auth, personal data, payments, untrusted input |
| [Performance Reviewer](.agents/subagents/performance-reviewer.md) | Assesses latency, throughput or resource-sensitive work | bottlenecks, measurement plan, cheap fixes | latency, throughput or resource constraints |

### Agent topologies

The template supports multiple agent topologies. Choose the smallest useful
topology for the task. The [`team-selection`](.agents/skills/coordination/team-selection/SKILL.md)
skill guides selection.

#### 1. Single lead agent (linear)

A single agent reads `AGENTS.md`, performs discovery, implementation, and
review using an explicit checklist. This is the simplest topology and works
with any coding agent.

```
user -> lead agent -> AGENTS.md -> skills (lazy-loaded)
                          |
                          v
                   implement -> test -> review -> handoff
```

Use when: task is bounded, low ambiguity, no independent review needed.

Limitation: no independent challenge. Record this in `HANDOFF.toon` when used.

#### 2. Sequential role passes

A single agent passes through multiple persistent role personas in sequence,
changing perspective between passes. Cheaper than spawning subagents but
provides limited independence.

```
lead agent -> product owner pass -> architect pass -> tech lead pass -> review
```

Use when: task benefits from multiple perspectives but subagents are
unavailable or expensive.

Limitation: same agent, same context window. Not equivalent to independent
review.

#### 3. Independent subagents

A lead agent delegates bounded research, exploration or review tasks to
subagents. Subagents run with focused context, compressed output, and stop
when done.

```
lead agent -> delegate -> researcher subagent
                     -> repository explorer subagent
                     -> red-team reviewer subagent
                     -> security reviewer subagent
                     <- compressed findings
```

Use when: task needs independent perspectives, broad evidence gathering, or
specialised review (security, performance, testing).

#### 4. Persistent agent team

Multiple persistent team roles run concurrently with bounded role ownership.
The project lead owns final synthesis and does not force consensus.

```
product owner | architect | tech lead  <- parallel persistent roles
     |              |           |
     v              v           v
   intent     architecture   slices
     |              |           |
     +------+-------+-----------+
            |
            v
       lead synthesis
            |
            v
       HANDOFF.toon
```

Use when: complex project with sustained multi-role work, available in
agents that support concurrent sessions (e.g. Claude with multiple
conversations or an agent orchestration platform).

#### Creating new persistent roles

To add a new persistent team role:

1. Create a Markdown file under `.agents/team/` named after the role (e.g.
   `.agents/team/security-officer.md`).
2. Document the sections: Purpose, Responsibility, Questions owned, Inputs,
   Outputs, Non-responsibilities, Invoke when.
3. Reference the role in `CUSTOMIZE_THIS_PROJECT.toon` under `agents.team.roles`
   if it should be enabled by default for generated projects.
4. Update [`AGENTS.md`](AGENTS.md) if the role introduces new canonical rules.

#### Creating new subagents

To add a new bounded subagent:

1. Create a Markdown file under `.agents/subagents/` named after the subagent
   (e.g. `.agents/subagents/api-contract-reviewer.md`).
2. Document the sections: Task boundary, Required input, Expected output,
   Context budget, Completion condition, Non-responsibilities, When not to
   invoke, Expected length, Model diversity.
3. Reference the subagent in the [`team-selection`](.agents/skills/coordination/team-selection/SKILL.md)
   skill's conditional roles if it should be conditionally invoked.
4. Subagents are lazy-loaded — they do not need to be registered in
   `CATALOG.toon`; they are invoked by the lead agent when needed.

#### Creating new skills

To add a new skill:

1. Create a directory under `.agents/skills/` in the appropriate category
   (`workflow/`, `discovery/`, `specialise/`, `coordination/`, `knowledge/`,
   `tooling/`).
2. Create `SKILL.md` with frontmatter:

   ```yaml
   ---
   name: my-skill
   description: What the skill does, used for lazy-loading.
   ---
   ```

3. Document the Outcome, Procedure, Do not sections.
4. Register the skill in [`.agents/skills/CATALOG.toon`](.agents/skills/CATALOG.toon)
   with its path and trigger.
5. If the skill introduces a required file or command, add it to
   [`.agentic-template/bin/check-repo-contract`](.agentic-template/bin/check-repo-contract).

### `/sudo` — perform an operation as a persona

The [`/sudo`](.agents/skills/coordination/sudo/SKILL.md) skill switches the
agent's active persona to a named team role or subagent for a single bounded
operation, then returns to the lead agent persona. This is in-place
role-switching, not spawning a new agent.

```
/sudo <persona> <operation>
```

Available personas:

| Persistent roles | Bounded subagents |
|---|---|
| `product-owner` | `researcher` |
| `architect` | `repository-explorer` |
| `tech-lead` | `red-team-reviewer` |
| `domain-expert` | `evidence-checker` |
| `knowledge-curator` | `test-reviewer` |
| | `security-reviewer` |
| | `performance-reviewer` |

Example:

```
/sudo architect review the persistence boundary between the API and database layers
```

This reads [`.agents/team/architect.md`](.agents/team/architect.md), adopts
the architect persona for the review, produces architecture findings with
evidence and confidence, then returns to the lead agent persona.

`/sudo` is cheaper than spawning a subagent but provides no independent
context isolation. For independent review, spawn a subagent instead. Do not
chain `/sudo` calls to simulate independent review — sequential role passes
by the same agent are not equivalent.

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
explicit container decision during `/specialise`:

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

Generated projects specialise test commands during `/specialise`:

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
The `/specialise` lifecycle transforms the template into a specialised, truthful,
delivered project with executable commands and tested documentation.
