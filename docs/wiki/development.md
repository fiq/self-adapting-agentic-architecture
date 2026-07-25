# Development

Nix owns developer tooling. Repository commands are the shared local and CI
entrypoints.

Start with:

```sh
.agentic-template/bin/project help
.agentic-template/bin/project startup
.agentic-template/bin/project init
.agentic-template/bin/project check
.agentic-template/bin/project doctor
```

Use `.agentic-template/bin/project docs` when you know the kind of work but not
which document to read first.

## Development lifecycle

```
/specialise ─► calibrate audience + app shape (plain language), right-size
                 architecture as a recorded, bought-into choice
     ▼
/ideate ─────► short-cycle multi-persona loop (Intent → Boundary → Delivery →
    (idea or     Quality gate) → structured change artifact
    narrative)
     ▼
outside-in ► acceptance test per WHEN/THEN scenario, fidelity by risk
   ATDD        (acceptance / component-integration / subcutaneous)
     ▼
/review ────► bounded boy-scout clean-up: code, language and architectural
                 smells, inappropriate coupling
     ▼
archive change → specs/capabilities/  +  wiki-tidy keeps docs and the
                 knowledge graph current
```

- Specs are OpenSpec-shaped, structured and agent-first (`specs/README.md`).
- The repo-native context store (`docs/context-store.md`) ties specs, profile,
  handoff, knowledge and validation into one query path.
- TOON is preferred for state/contracts; S-expressions are preferred for
  rules/compute when a project needs a compact policy DSL.
- Quality is standing, not a phase: reuse over duplication, pay in-path debt,
  docs land in the change, no silent TODOs.
- `project check-changes` validates specs; `project check-wiki` warns on wiki
  drift; `project install-hooks` opts into a non-blocking pre-commit.
- Non-trivial handoff notes include the spec reference or no-spec rationale,
  fitness-function delta or no-change rationale, validation and knowledge
  update.
