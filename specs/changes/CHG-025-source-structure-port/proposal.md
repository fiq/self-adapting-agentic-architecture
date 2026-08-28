# CHG-025: The source-structure port and its conformance suite

## Why

`ADR-0005` decides that the AST is a deterministic measurement surface, filled by
frontends and consumed by capabilities that never learn which frontend produced
their input. That decision is currently a document. This change makes the
abstraction real, and makes conformance to it decidable.

The ordering matters and is the ADR's own conclusion. The suite is written before
any frontend, because the suite is the contract: a frontend that merely exists is
not a frontend that can be trusted, and "find a parser and wrap it" is only safe
if *wrapped correctly* is something a machine can decide.

## Intent

One layered model in `domain`. One port in `deterministic`. One language-agnostic
conformance suite that every frontend must pass, parameterised over a frontend
and the fixtures it supplies in its own language.

No frontend ships in this change. The Java frontend is the next one, and it will
be judged by the suite this change writes rather than by review of its adapter.

## Why a frontend is deliberately excluded

Writing the suite alongside its first implementation invites the suite to
describe what that implementation happens to do. Java would then be the contract,
and the second language would discover it.

Shipping the suite alone has a real cost — nothing observable changes for a user
— and that cost is accepted here for one reason: the suite is what an agent
contributing a frontend for an unfamiliar language is handed. Getting it wrong is
expensive later in a way it is not expensive now.

## Non-goals

- any frontend, including Java;
- the declared-locus gate, structural distance, complexity or convergence, all of
  which consume this model and none of which are built here;
- the flow layer, which no planned capability yet needs.

## Related knowledge

`ADR-0005`, `ARCH-001`, `CON-002`, `PAT-004`.
