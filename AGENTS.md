# Claude and Codex workflow

Claude (Claude Code) is the lead engineer for this repository. It owns investigation,
design, implementation, builds, tests, instance edits, and — with the user's go-ahead —
commits and versioning.

Astra (Codex) is a consultant. It is called only when Claude is stuck after a genuine
attempt, before a version is declared stable, or when the user asks for a second
opinion. Astra's context is the scarce resource; the consult is designed to spend it
only on moments where deep reasoning changes the outcome.

## When Astra is consulted

Claude calls Astra through the global consult script:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File C:\Users\wrait\.codex\tools\ask-astra.ps1 -Question '<problem>' -Context <files>
```

The consult runs read-only. When consulted, Astra should:

- think about the supplied problem rather than restate it, assuming the obvious things
  have been tried;
- give its best root-cause hypothesis with supporting evidence, the competing hypothesis
  to rule out next and the observation that would rule it out, and a concrete next
  diagnostic or fix;
- for a stability check, review the changes for correctness, init/persistence/
  item-safety/client-server risks, and anything that should block calling the version
  stable;
- not take over the work, redirect scope, or propose a broad redesign unless the
  evidence forces one.

Claude treats Astra's answer as engineering input and verifies it before acting.

If the user starts a session directly in Codex, Codex may work normally, but should
leave commits, versioning, and live-instance edits to the user's explicit instruction
and should not re-delegate the whole objective back to Claude through
`claude-grunt.ps1` unless the user asks.

## Verification standards

- Spot-check routine findings and mechanical edits: confirm the working tree and the
  build, not every cited line.
- Verify directly before adopting anything that drives a design decision, touches
  init/persistence/item-safety/client-server boundaries, or precedes a commit or
  release.
- Do not treat a screenshot description or an unverified numeric cost/token claim as
  fact — re-check it directly before it drives a decision.
- An in-game result is accepted when the user confirms it.

Store transient reports and consult outputs outside the repository (the scripts'
default locations). Promote durable conclusions into repository documentation once they
are verified.

## Current project constraints

- Recipes, materials, and multiblock definitions generally belong in KubeJS. Java is for
  integration and behavior scripts cannot reach.
- Never force GregTech registry initialization early.
- Do not infer mixin execution from a successful build or startup. Verify the affected
  runtime path.
- The exact GTCEu snapshot matters; inspect the staged jar rather than relying on the
  `8.0.0-SNAPSHOT` label.
- Preserve user work and local instance data. Do not broadly delete, move, or replace
  jars.
