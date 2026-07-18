# Development

Nix owns developer tooling. Repository commands are the shared local and CI
entrypoints.

Start with:

```sh
.agentic-template/bin/project help
.agentic-template/bin/project init
.agentic-template/bin/project check
.agentic-template/bin/project doctor
```

## Development lifecycle

```
/specialise ─► calibrate audience + app shape (plain language), right-size
                 architecture as a recorded, bought-into choice
     ▼
/ideate ─────► short-cycle multi-persona loop (Intent → Boundary → Delivery →
    (idea or     Quality gate) → specs/changes/<id>/change.toon
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

- Specs are OpenSpec-shaped, TOON-encoded and agent-first (`specs/README.md`).
- Quality is standing, not a phase: reuse over duplication, pay in-path debt,
  docs land in the change, no silent TODOs.
- `project check-changes` validates specs; `project check-wiki` warns on wiki
  drift; `project install-hooks` opts into a non-blocking pre-commit.
