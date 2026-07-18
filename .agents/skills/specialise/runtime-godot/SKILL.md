---
name: runtime-godot
description: Specialise Godot runtime, export and headless test conventions.
---

# Runtime: Godot

Detect `project.godot`, Godot version, GDScript, C#, native extensions,
GDExtension, OpenXR, Android export and Quest or VR concerns.

Treat Godot separately from Python. Prefer fast script tests, headless tests,
scene-level behaviour tests and platform/device validation as separate fidelity.

## Language smells (for review-loop)

Per-frame allocations in `_process`; `get_node` path lookups in hot loops;
deep scene-tree coupling via absolute paths; god scripts; untyped GDScript on
boundaries; signals bypassed by direct cross-node calls; logic in `_ready`
that belongs in a resource; unbounded node instancing without pooling.
