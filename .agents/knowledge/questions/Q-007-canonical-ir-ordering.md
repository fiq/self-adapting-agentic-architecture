---
id: Q-007
type: question
title: Should canonical mutation IR be order-insensitive for set-like fields
status: open
summary: The canonicalizer preserves declared order for loci, evidence and gates, so two contracts that differ only in list order produce different canonical forms even though the validator accepts both.
owners:
  - architect
relates_to:
  - CON-001
  - Q-002
evidence:
  - application/src/main/java/io/github/selfadaptingagenticarchitecture/application/MutationContractCanonicalizer.java
  - application/src/test/java/io/github/selfadaptingagenticarchitecture/application/MutationContractCanonicalizerTest.java
  - specs/changes/CHG-002-live-loop-policy/design.md
review_after: 2026-10-26
---

# Canonical IR Ordering for Set-like Fields

`MutationContractValidator` checks `required_evidence` and `hard_gates` as
membership, so order does not affect acceptance. `MutationContractCanonicalizer`
emits them in declared order, so two accepted contracts listing the same
evidence in a different order canonicalize differently.

That weakens one stated reason for choosing S-expressions: comparing canonical
forms to tell whether two mutations are the same mutation.

Two defensible answers:

- treat declared order as part of the contract, and compare canonical forms
  only within one proposal lineage;
- sort set-like fields before emission, and keep order significant only for
  `loci`, where sequence may carry intent.

Deciding this needs a real consumer. Nothing compares canonical forms yet, so
the current behaviour is documented rather than changed. Revisit when candidate
deduplication, crossover parent matching or IR-level diffing lands.
