# Codex and Claude workflow

Codex is the lead engineer for this repository. Claude Code is a worker for bounded, mechanical tasks.

## Ownership

Codex owns:

- problem framing and deciding which behavior is actually broken;
- architecture, mixin targets, lifecycle and state-machine reasoning;
- interpreting runtime evidence and rejecting unsupported conclusions;
- edits that can affect initialization, persistence, item safety, or client/server boundaries;
- final review, testing, versioning, release notes, commits, and releases.

Claude may handle:

- locating call sites and producing inventories of classes, methods, logs, or recipes;
- decompiling named classes and summarizing exact control flow;
- comparing two explicitly named versions or files;
- extracting failure signatures and timelines from logs;
- straightforward documentation, formatting, repetitive renames, and narrow test scaffolding;
- mechanical edits only when Codex gives exact acceptance criteria and explicitly uses edit mode.

## Delegation rules

Use `tools/claude-grunt.ps1` when a bounded evidence-gathering or mechanical task would otherwise consume substantial context. Give Claude one concrete question, exact paths, and an expected output shape. Prefer report mode.

Do not delegate choosing a fix, deciding whether a mixin is safe, accepting an in-game result, or editing the Minecraft instance. Do not ask Claude and Codex to independently redesign the same subsystem.

Treat Claude output as an untrusted engineering report. Codex must inspect cited source or bytecode, check the working tree, and run the relevant verification before adopting it. A confident report is not evidence by itself.

Store transient reports under `.agent-handoffs/claude/`; Git ignores that directory. Promote durable conclusions into repository documentation only after Codex validates them.

## Current project constraints

- Recipes, materials, and multiblock definitions generally belong in KubeJS. Java is for integration and behavior scripts cannot reach.
- Never force GregTech registry initialization early.
- Do not infer mixin execution from a successful build or startup. Verify the affected runtime path.
- The exact GTCEu snapshot matters; inspect the staged jar rather than relying on the `8.0.0-SNAPSHOT` label.
- Preserve user work and local instance data. Do not broadly delete, move, or replace jars.
