---
name: detect-personas
description: Detect whether a project needs project-specific personas or stakeholder stand-ins, propose them, materialise them as first-class /sudo targets, and record the decision.
---

# Detect Personas

## Purpose

Identify whether a specialised project benefits from additional persistent team
roles or stakeholder stand-ins beyond the template defaults (product-owner,
architect, tech-lead, domain-expert, knowledge-curator).

Personas make user value, regulatory, operational, or accessibility concerns
first-class during `/sudo`, ideation, review and acceptance.

## Trigger

Run during `/specialise`, after `calibrate-audience` and before architecture
recommendation, when:

- `CUSTOMIZE_THIS_PROJECT.toon` or narrative intake contains user, stakeholder,
  regulatory, compliance, accessibility or operational references.
- The app shape or domain implies distinct actor viewpoints (e.g. patient +
  clinician, rider + dispatcher, player + moderator).
- The project profile records a `compliance`, `security`, `operations`, or
  `ux_accessibility` concern as material.

## Procedure

1. Inspect evidence:
   - `CUSTOMIZE_THIS_PROJECT.toon.narrative`
   - `specs/capabilities/` (if narrative intake produced them)
   - `PROJECT_PROFILE.toon.unknowns` (look for compliance, privacy, ops, UX)
   - `PROJECT_PROFILE.toon.audience.app_shape` and `language_by_role`
   - Any prior `knowledge/risks/` or `knowledge/domains/` entries.

2. Classify persona need:
   - **None needed** — the template defaults plus subagents cover all
     foreseeable perspectives. Record this explicitly.
   - **Inferable from evidence** — the narrative strongly implies one or more
     personas (e.g. "healthcare app" implies `compliance-officer` and
     `end-user/patient`). Propose them with defaults.
   - **Ambiguous** — evidence hints at stakeholders but does not specify them.
     Ask the smallest useful question set.

3. If personas are needed, for each propose:
   - `name` — kebab-case file name (e.g. `compliance-officer`,
     `end-user-novice`, `on-call-sre`).
   - `purpose` — one line.
   - `responsibility` — what this persona protects or advocates for.
   - `questions_owned` — what this persona asks at hard choices.
   - `invoke_when` — task types that benefit from this perspective.
   - `do_not_invoke_when` — guard against misuse.

4. Materialise personas:
   - Write each persona to `.agents/team/<name>.md` using the
     [persona template](#persona-template).
   - Ensure the file follows the same structure as existing team roles
     ([`product-owner`](.agents/team/product-owner.md),
     [`architect`](.agents/team/architect.md), [`tech-lead`](.agents/team/tech-lead.md)).
   - Update `.agents/team/README.md` to list the new personas under a
     "Project-specific personas" heading.

5. Record the decision in `PROJECT_PROFILE.toon`:

   ```yaml
   personas:
     detected_by: detect-personas
     materialised:
       - name: compliance-officer
         reason: healthcare domain requires regulatory viewpoint
     explicitly_none: false
     revisit_when:
       - domain changes materially
       - new compliance regime introduced
   ```

6. Update `sudo` skill awareness:
   - No file change needed in `.agents/skills/coordination/sudo/SKILL.md`;
     it already reads `.agents/team/<persona>.md` dynamically.
   - Ensure the new persona names appear in `sudo` documentation if the
     skill is updated in the same change.

## Do not

- Create personas when evidence is absent and the user declines.
- Create more than five project-specific personas in a single `/specialise`;
   prefer the most material two or three and defer the rest with a
   `revisit_when` trigger.
- Merge stakeholder stand-ins into the generic subagent set; persistent
   project roles belong in `.agents/team/`.
- Leave personas as abstract mentions in `PROJECT_PROFILE.toon` without
   materialised definition files.

## Persona template

```markdown
# <Persona Name>

## Purpose

<One-line purpose: what this persona protects or advocates for.>

## Responsibility

- <Responsibility 1>
- <Responsibility 2>

## Questions owned

- <Question this persona asks at hard choices 1>
- <Question this persona asks at hard choices 2>

## Inputs

<What this persona needs to be effective.>

## Outputs

<What this persona produces.>

## Non-responsibilities

<What this persona does not own, to avoid scope creep or concensus forcing.>

## Invoke when

<Specific task types or decision points.>

## Do not invoke when

<Guardrails against misuse.>

## Efficiency

<Model class or context compression guidance.>
```

## Output

- Zero or more new `.agents/team/<name>.md` files.
- Updated `.agents/team/README.md`.
- Updated `PROJECT_PROFILE.toon.personas` map.
