# /ideate

Turn an idea or a narrative into a validated change proposal through a short,
multi-persona ideation loop.

## Usage

```
/ideate <idea>
/ideate --file <path-to-narrative>
```

- With an idea or narrative, runs a bounded loop (Intent → Boundary → Delivery
  → Quality gate) and produces `specs/changes/<id>/change.toon`.
- A narrative (inline or file) is first passed through `narrative-intake`.
- At hard choices, each relevant persona's stance is attributed as
  discourages / accepts / encourages; the lead owns synthesis.

See `.agents/skills/workflow/ideate/SKILL.md` and
`.agents/skills/workflow/narrative-intake/SKILL.md` for full guidance.
