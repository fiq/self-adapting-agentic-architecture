# /sudo

Switch the agent's active persona to a named team role or subagent, perform
a single bounded operation from that persona's perspective, then return to
the lead agent persona.

## Usage

```
/sudo <persona> <operation>
```

## Available personas

### Persistent team roles
- `product-owner`
- `architect`
- `tech-lead`
- `domain-expert`
- `knowledge-curator`

### Bounded subagents
- `researcher`
- `repository-explorer`
- `red-team-reviewer`
- `evidence-checker`
- `test-reviewer`
- `security-reviewer`
- `performance-reviewer`

See `.agents/skills/coordination/sudo/SKILL.md` for full guidance.
