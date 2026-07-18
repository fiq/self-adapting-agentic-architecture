---
name: runtime-elixir
description: Specialise Elixir, Mix, Phoenix, Ecto and OTP project conventions.
---

# Runtime: Elixir

Detect Mix, OTP applications, Phoenix, LiveView, Ecto, Oban, Broadway, ExUnit
and release configuration. Use Elixir and Erlang versions from project evidence
where available.

Prefer ExUnit, boundary tests, Ecto sandbox where applicable and OTP process
behaviour tests where semantics matter.

## Language smells (for review-loop)

Rescuing without re-raising; overusing processes as mutable state; unsupervised
processes; `with` chains that hide error paths; N+1 Ecto queries; business
logic in controllers or LiveViews; atoms from untrusted input; large context
modules; ignoring `{:error, _}` returns.
