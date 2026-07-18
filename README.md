# Agentic Team Project Template

A framework for AI agents and humans to collaborate on principal-level engineering decisions. **Technology and agent agnostic.** Works with any coding agent that can read Markdown and run shell commands (Claude, Codex, Copilot, etc.). Reduces decision fatigue through evidence-driven discovery, structured semantic state, and composable workflows that adapt to your skill level and project complexity.

## What this solves

**Problem:** When an agent acts alone, humans lose visibility into trade-offs and assumptions. When humans micro-direct agents, decisions become slow. When guardrails are opaque, teams cannot learn or adapt them.

**Solution:** Make the operating rules explicit and learnable (`AGENTS.md`), keep semantic state visible and structured (`.toon` files), use composable skills that agents can invoke in any topology, and stay human-in-the-loop at irreversible or high-stakes choices.

## Value

- **Principal-level decisions, not code generation.** Architecture boundaries, right-sizing (conscious, recorded, bought-in), quality gates, role attribution, tech choices - decisions that are hard to reverse.
- **Adapts to your experience.** The workflow calibrates to the user's skill level and project complexity. A domain expert can partner with an agent on design; a junior engineer gets more coaching and structure.
- **Opinionated but learnable.** The rules are in readable Markdown, not hidden in prompt engineering. You can change them, disagree with them, or understand why they exist.
- **No magic.** Commands are explicit shell scripts under `.agentic-template/bin/`. Hooks and commands are in `CLAUDE.md` (symlink to `AGENTS.md`). Knowledge is stored locally in `.agents/knowledge/` with durable schemas.
- **Works with any agent.** No proprietary integrations. Agents read `AGENTS.md` and execute shell commands. That is it.

## How it works (for engineers)

1. **You provide project intent** - briefs, constraints, user stories, artifacts.
2. **The agent inspects the repo** - existing code, manifests, configs - and asks the smallest useful question set to resolve unknowns.
3. **The agent proposes a minimal architecture** - smallest sufficient design, recorded and bought into.
4. **You decide at hard choices** - architecture boundaries, trade-offs, what NOT to build.
5. **Development follows a loop** - calibrate, ideate, spec (TOON), ATDD, review, archive + wiki.

```
Human provides:                    Agent discovers:              Human decides at:
- Project intent                   - Runtime, framework          - Hard choices
- Briefs, artifacts                - Persistence, messaging      - Architecture boundaries
- User stories                     - Test strategy               - Right-sizing (what NOT to build)
- Constraints                      - Container, infra            - Trade-offs between personas

                           |
                           v

                    /specialise -> evidence-driven bootstrap
                                    calibrate audience, build project profile

                           |
                           v

                     Development lifecycle:
                     calibrate -> ideate -> spec (TOON) -> ATDD -> review -> archive + wiki
```

## 🚀 Get started

### 1. Create a repository from this template

On GitHub, use **Use this template**. Locally, copy the repo and point `origin` at your new repository.

### 2. Provide what you know

Update `CUSTOMIZE_THIS_PROJECT.toon`:
- **project:** name, purpose, primary users
- **domain:** core capabilities, hard constraints
- **narrative:** (optional) a free-text project brief; agents will interrogate and expand it

Add any artifacts: sketches, API drafts, package files, links.

### 3. Start with an agent

In Claude, Codex or another coding agent, read `AGENTS.md` first (or `CLAUDE.md`), then ask:

```
Help me initialise this project from the template.
Run .agentic-template/bin/project init, inspect the result, and guide me
through the smallest useful next setup steps. If my project intent is unclear,
interview me and help convert my answers, briefs and artifacts into project state.
```

The agent will:
- Inspect repository evidence (existing code, manifests, config)
- Ask the smallest useful question set to resolve unknowns
- Recommend the smallest sufficient architecture
- Populate `PROJECT_PROFILE.toon` with facts, inferences, decisions and unknowns
- Specialise runtime, testing, containers and CI from your evidence
- Rewrite the README and operating contract for your project
- Hand off to `HANDOFF.toon` so the next work continues from here

## Development lifecycle

Work flows through a small, connected loop. Agents drive it; humans steer at decision points.

```
/specialise --> calibrate audience + app shape (plain language)
                  right-size: smallest sufficient architecture, recorded buy-in
      |
      v
/ideate ------> short-cycle multi-persona loop -> specs/changes/<id>/change.toon
     (idea or     Intent -> Boundary -> Delivery -> Quality gate
     narrative)   At hard choices: show persona stances (discourages/accepts/encourages)
      |
      v
outside-in -> acceptance test per WHEN/THEN scenario, fidelity by risk
    ATDD        (acceptance / component-integration / subcutaneous)
      |
      v
/review ------> boy-scout cleanup: code, language and architectural smells, coupling
      |
      v
archive -------> specs/capabilities/ + wiki keeps docs and knowledge graph current
```

**Key principles:**
- **Specs are TOON-encoded, agent-first.** Living requirements in `specs/capabilities/`, proposals in `specs/changes/<id>/` with deltas, `WHEN/THEN` scenarios and acceptance tests (ATDD bridge).
- **Quality is standing, not a phase.** Boy-scout rule, reuse over duplication, pay in-path debt, docs land in the change, no silent TODOs.
- **Right-sizing is conscious.** The smallest sufficient architecture is chosen, explicitly. What is excluded and why are recorded. Revisit conditions are named.
- **Knowledge forms one graph.** `AGENTS.md` + `PROJECT_PROFILE.toon` + specs + `HANDOFF.toon` + `.agents/knowledge/` + wiki all connect via `TAXONOMY.md`. Agents search before acting.

## Philosophy

### Agent-agnostic

This template works with any agent that can:
- Read Markdown files
- Execute shell commands
- Interpret TOON (a small YAML subset)
- Follow a text-based operating contract

No proprietary APIs. No agent-specific prompting tricks. The contract is learnable.

### Evidence-driven, not opinionated

The template does not force choices. It:
- Inspects repository evidence first (existing code, manifests, config)
- Recommends the smallest sufficient option
- Asks only high-impact questions
- Records unknowns, not secret assumptions

Example: if you have a `package.json`, Node is inferred. If you have both Python and Java, the agent asks which is primary. If you have neither, the agent asks what you are building.

### Principal-level, not code-generation

This is not a scaffolding tool. It is for:
- Making architecture boundary decisions
- Choosing what NOT to build (right-sizing)
- Reconciling trade-offs between personas
- Keeping technical debt visible and paid
- Keeping quality standing, not deferred
- Recording durable decisions (ADRs)

Code generation happens *inside* the lifecycle, not as the whole point.

### Humans stay in the loop

At every hard choice, agents surface the trade-off:

```
choice: add a message broker now
  architect     discourages (no async evidence yet)
  tech-lead     accepts     (isolated, reversible)
  product-owner encourages  (unblocks the next capability)
  -> [human decides]
```

No consensus required. The lead decides, informed by who cares what.

## 🏃 Run locally

Enter the Nix development shell:

```sh
nix develop
```

Or use the documented one-shot ladder (pinned container -> npx fallback).

Run the canonical commands:

```sh
.agentic-template/bin/project help
.agentic-template/bin/project init       # Bootstrap a new project
.agentic-template/bin/project check      # Run all repo checks
.agentic-template/bin/project ready      # Is this project ready to ship?
.agentic-template/bin/project doctor     # Diagnostic summary
```

## 🛠️ Skills and agent compatibility

This template is built on **composable skills** - reusable workflows for ideation, discovery, planning, testing, refactoring, review and delivery. Skills are lazy-loaded via `.agents/skills/CATALOG.toon` and do not require all of them at once.

### The development loop in skills

| Skill | Trigger | Purpose |
|---|---|---|
| `calibrate-audience` | Project shape known | Establish skill level, app shape, record right-sizing |
| `ideate` | Ambiguous feature request | Multi-persona loop -> validated spec |
| `narrative-intake` | Narrative provided | Turn free text into a structured change proposal |
| `specify` | Meaningful behaviour change | Create proportional OpenSpec-shaped TOON specs |
| `outside-in-tdd` | Implementing a change | Start from acceptance test, fidelity by risk |
| `review-loop` | Before merge | Boy-scout cleanup, smells, coupling |
| `wiki-tidy` | At task boundaries | Keep docs and knowledge graph current |

### Compatible agents

- **Claude** (Anthropic) - reads `CLAUDE.md` (symlink to `AGENTS.md`)
- **Copilot** (GitHub) - reads `AGENTS.md` + `.github/copilot-instructions.md`
- **Codex** (OpenAI) - reads `AGENTS.md`
- Any agent that can read Markdown and execute shell commands

### Agent personas (persistent roles) 👥

The template defines persistent team roles that agents can adopt via `/sudo`:
- **Product Owner:** scope, intent, acceptance criteria
- **Architect:** boundaries, trade-offs, reversibility
- **Tech Lead:** thin slices, test strategy, delivery
- **Domain Expert:** domain rules, validation paths
- **Knowledge Curator:** durable knowledge, graph consistency

### Agent topologies (how agents coordinate)

1. **Single lead + fallback checklist** - one agent, explicit review gates (cheapest)
2. **Sequential role passes** - lead switches personas via `/sudo` (scalable)
3. **Independent subagents** - separate agents per role (most rigorous)
4. **Persistent team** - dedicated roles with long-term memory (highest fidelity)

Pick the topology that matches your risk and budget.

## Run with containers

Every project makes an explicit container decision. Deployable services and web applications default to a tested application image unless evidence supports a documented exception.

```sh
.agentic-template/bin/project image
.agentic-template/bin/project image-test
docker run -it $(docker build -q .)
```

See `README_TEMPLATE.md` (generated README) for runtime instructions.

## 🧪 Tests

Default to test-first for meaningful behaviour.

**Testing posture:**
- **Outside-in, boundary-in ATDD.** Start from acceptance test, drive inward.
- **Fidelity by risk.** Acceptance, component-integration, or subcutaneous - choose per scenario.
- **Real dependencies where they matter.** Use Testcontainers for lifecycle-managed integration tests.
- **Testing trophy:** strong unit feedback + strong integration confidence + a few E2E paths.

```sh
.agentic-template/bin/project test
.agentic-template/bin/project integration-test
.agentic-template/bin/project e2e-test
```

## ⚙️ Configuration and environment variables

Project configuration is declared in:
- `AGENTS.md` - operating rules and canonical commands
- `PROJECT_PROFILE.toon` - project facts, inferences, decisions, unknowns
- `HANDOFF.toon` - active work state, completed work, next actions
- `.agents/knowledge/` - durable decisions (ADRs), patterns, risks, questions, learnings

Environment variables are documented in the generated `README.md`.

## Infrastructure and deployment state

Every project records:
- **Local topology:** Nix devshell, containers, Compose
- **Deployment target:** cloud provider, serverless, self-hosted, deferred
- **IaC status:** required, deferred, not applicable

See `PROJECT_PROFILE.toon.infrastructure` and the generated README.

## 🚫 Deliberate non-goals

This is **not**:
- An internal developer platform (IDP). No defaults for databases, brokers, Kubernetes, or runtimes.
- A code-generation service. It is for decision-making.
- Black-box magic. All rules are readable; all state is structured text.
- Coupled to a single agent or platform.
- A replacement for human architecture review. It is a tool to make review more structured.

## 📚 Important decisions and documentation links

- **Architecture:** see `docs/architecture/` (ADRs)
- **Decisions:** see `docs/decisions/` (ADR index)
- **Knowledge:** see `.agents/knowledge/` (taxonomy, domains, systems, risks, questions)
- **Durable rules:** see `AGENTS.md` (operating contract)
- **Development lifecycle:** see `docs/wiki/development.md`

## AI-assisted delivery statement

This template was designed with AI agents as first-class collaborators. The structure, workflows and rules reflect a philosophy: agents reduce decision fatigue when operating under an explicit, learnable contract that keeps humans in the loop at irreversible choices.

The `/specialise` bootstrap, the development lifecycle, the persona topology, the knowledge graph, and the CI contract were all developed with agent assistance. They have been shaped by what makes agent-human collaboration effective, not just what makes sense to humans alone.

If you use this template, you are opting into:
- **Composable skills** that agents can invoke
- **Semantic state** (TOON files) that agents can parse and update
- **Readable operating rules** (this contract) that agents and humans share
- **Persistent topologies** (personas, subagents) that preserve context across sessions
- **Hard choice attribution** (persona stance) so you know who favours what trade-off

The goal is not to replace you. It is to make it faster and clearer for you and an agent to collaborate on decisions that matter.
