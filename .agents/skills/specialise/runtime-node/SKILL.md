---
name: runtime-node
description: Specialise Node, TypeScript, React and package-manager conventions.
---

# Runtime: Node or TypeScript

Detect package manager from lock files first. Respect existing Nix runtime
pinning. Do not add NVM by default on NixOS.

Specialise repository commands from real scripts. Recognise React, Vite,
Next.js and server-side Node separately. Configure tests from the project
tooling rather than adding a second harness.

## Language smells (for review-loop)

`any` escaping the type system; floating promises / missing `await`; unhandled
rejections; `==` over `===`; mutable module-level state; deep prop drilling and
oversized components; effect hooks with missing or wrong dependency arrays;
barrel-file import cycles; swallowing errors in `catch`.
