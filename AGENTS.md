# Codex and Claude workflow

Astra (Codex) is the consulting principal and final reviewer for this repository.
Claude drives execution by default: investigation, implementation, builds, tests, and
iteration on its own failures. Astra's context is the scarce resource in this setup;
Claude's is not — default to delegating whole objectives to Claude and reserve Astra
for the moments where deep reasoning actually changes the outcome.

## Ownership

Astra owns:

- root-causing a bug that has survived a genuine attempt, and other hard architectural calls;
- design and risk tradeoffs, and deciding between competing fixes;
- final review and acceptance of Claude's work;
- anything touching the live Minecraft/ATM10 instance, external systems, versioning,
  release notes, commits, and pushes.

Claude drives (given a whole objective and acceptance criteria, not a single question):

- investigation: locating call sites, decompiling named classes, comparing versions,
  extracting failure signatures and timelines from logs;
- implementation: mechanical edits, straightforward documentation, repetitive renames,
  narrow test scaffolding, and full edit/build/test/iterate loops when the objective and
  acceptance criteria are explicit;
- iterating on its own build and test failures without checking back in after each step.

Claude may propose fixes and implement routine solutions. It does not finalize major architecture, accept an in-game result as final, edit the live
Minecraft instance, or commit/push/version/release. When Claude hits something genuinely
hard — a real attempt has failed, not just a first pass — it escalates to Astra with
the evidence attached rather than thrashing.

## Delegation rules

Run the global wrapper from the repository root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File C:\Users\wrait\.codex\tools\claude-grunt.ps1 -Mode <Report|Edit|Work> -Task '<objective and acceptance criteria>'
```

Modes:

- `Report` — read-only investigation, no file changes.
- `Edit` — mechanical edits only, no shell/build.
- `Work` — full loop: edit, build, test, and iterate until the stated acceptance
  criteria are met, or until Claude can state precisely why they can't be.

Defaults are `-Model opus -Effort high`; drop to `-Model sonnet -Effort medium` for
genuinely mechanical work. Delegate one objective per call with explicit acceptance
criteria, and let it run — do not split an objective into a chain of single questions,
and do not abort a run just because it has been going for a while.

The wrapper returns a concise `## SUMMARY` block plus a path to the full report. Read
the summary first; open the full report only when the summary is insufficient or the
decision is consequential.

Reserve consequential design decisions, mixin safety approval, final acceptance of an in-game
result, and editing the Minecraft instance for Astra. Do not ask Claude and
Astra to independently redesign the same subsystem.

## Verifying Claude's work

Treat Claude's output as an engineering report, not a verdict, and verify
proportionally to consequence:

- Spot-check routine findings and mechanical edits: confirm the working tree and the
  build, not every cited line.
- Verify directly before adopting anything that drives a design decision, touches
  init/persistence/item-safety/client-server boundaries, or precedes a commit or
  release.

Do not treat a screenshot description or an unverified numeric cost/token claim from a
report as fact — re-check it directly before it drives a decision.

Store transient reports outside the repository (the wrapper's default handoff
location). Promote durable conclusions into repository documentation only after Astra
validates them.

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
