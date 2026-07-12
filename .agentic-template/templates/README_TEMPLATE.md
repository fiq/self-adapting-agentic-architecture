# README Template

After `/init`, replace the template README with a project-facing README.
Do not append to the template README. Keep this template as hidden source
material under `.agentic-template/templates/`.

## Required sections

A generated project README must include:

1. **Project name and purpose** — what the project is and why it exists.
2. **First user or primary consumer** — who this serves first.
3. **Delivered or intended thin slice** — what is working now or intended next.
4. **Runtime architecture diagram** — an ASCII or image diagram of the system.
5. **Repository structure** — key directories and their purpose.
6. **Run locally** — how to start the project in a development shell.
7. **Run with containers** — container build and run instructions, or an
   explicit "not applicable" with a reason.
8. **Tests** — how to run the test suite via `.agentic-template/bin/project`.
9. **Configuration and environment variables** — what env vars or config
   files the project uses.
10. **Infrastructure and deployment state** — where the project deploys and
    what IaC status is recorded.
11. **Deliberate non-goals** — what the project intentionally does not do.
12. **Important decisions and documentation links** — ADRs, wiki and specs.
13. **AI-assisted delivery statement** — where relevant, state that AI agents
    assisted delivery and where the operating contract lives.

## Template markers that must not remain

- "AI-first Project Template"
- "This is a reusable, opinionated template"
- Template onboarding instructions from the source template README.

## Validation

`.agentic-template/bin/project check-readme` fails when template markers
remain or required sections are missing.
