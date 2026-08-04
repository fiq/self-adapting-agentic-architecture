# Glossary

Use this page for stable project terms that appear across specs, knowledge
entries, ADRs and handoffs. Prefer adding or clarifying a term here before
inventing a near-synonym in another Markdown file.

## Agentic Control Plane

The product role where SAAA coordinates model calls, context, validation,
scoring and audit rather than acting as another model. `ADR-0003` uses this
term with "smart bridge".

## Agentic Loop

The workflow that proposes, realizes, checks, scores and records candidate
changes. Models may propose or repair, but deterministic code validates and
scores.

## Architecture Fitness Function

An automated check that a structural characteristic of SAAA itself still holds,
in the sense used by *Building Evolutionary Architectures*.
`.agentic-template/bin/check-architecture-boundaries`, run by
`project lint`, is the first one: model-provider imports may not appear in
`modules/domain` or `modules/deterministic`, and the check fails when a scanned
layer directory is missing so a rename cannot make it pass vacuously.

It grades the repository, never a candidate. Distinct from a Candidate Fitness
Function, which grades a candidate and shares nothing with this beyond the name.

## Benchmark

A measurement instrument. In SAAA a benchmark is a JMH microbenchmark producing
`BenchmarkEvidence(name, value, unit)`, which is one input to one objective. A
benchmark never decides anything.

The genetic-programming literature often uses "benchmark" for a fixed evaluation
suite. SAAA calls those Corpora and reserves "benchmark" for the instrument.

## Candidate Fitness Function

`PhenotypeFitnessScorer`: the hard gates, weighted objectives and promotion
threshold that turn evidence about one candidate into `PROMOTE` or `DISCARD`.

It grades a candidate, never the repository. Distinct from an Architecture
Fitness Function.

## Capability

A living behavior contract under `specs/capabilities/`, keyed as `CAP-*`.
Capability pages say what the system promises after accepted changes land.

## Change

An in-flight or historical proposal under `specs/changes/<id>/`, keyed as
`CHG-*`. Changes map scenarios to tests and tasks.

## Corpus

A fixed evaluation suite. `GoldenCorpus.java` is a regression corpus for the
Candidate Fitness Function; the `saaa-ablate retrieval` TSV is a corpus of task
rows. The genetic-programming literature would call these benchmarks; see
Benchmark for why SAAA does not.

## Invariant

A property a candidate must satisfy. Binary for the promote-or-discard decision
and not tradeable against any objective, but carrying a magnitude used to rank
candidates that have already failed, so a near miss stays distinguishable from a
total miss.

## Knowledge Node

A structured Markdown entry under `.agents/knowledge/`, keyed by its category
prefix such as `ARCH-*`, `CON-*`, `Q-*` or `RISK-*`. Knowledge nodes preserve
durable facts, open questions and decisions without turning the wiki into a
changelog.

## Objective

A graded property that compounds into a candidate's score, evaluated only once
every invariant has passed.

## Northbound Interface

An interface exposed by SAAA to callers. `ADR-0003` names MCP and a narrow
OpenAI-compatible API as complementary northbound interfaces.

## Ontology

The project-owned vocabulary of concept types and relationship names used to
keep documentation traversable. The current ontology is the taxonomy in
`.agents/knowledge/TAXONOMY.md`; `Q-008` tracks whether it needs to become more
formal as Markdown volume grows.

## Severity Class

The partition that orders invariant violations and decides who may set a
threshold: integrity, safety, correctness, shape. Integrity voids a run rather
than ranking it, because a compromised measurement is not a statement about
fitness.

## Smart Bridge

The product role where clients call SAAA first, and SAAA routes to existing
models or agents while adding context packaging, validation, scoring and audit.
This is distinct from SAAA pretending to be a better model.

## Southbound Agent Adapter

An adapter SAAA uses to call an external agent rather than a raw model endpoint.
The same deterministic boundary applies: an external agent may propose or
repair, but SAAA still validates and scores.

## Southbound Adapter

An adapter SAAA uses to call an underlying model, datastore or tool.
`CHG-004`'s OpenAI-compatible LangChain4j wiring is southbound.

## Wiki Page

A short human-facing explanation under `docs/wiki/`. Wiki pages summarize and
link to canonical specs, ADRs and knowledge nodes; they should not duplicate
TOON state or handoff history.
