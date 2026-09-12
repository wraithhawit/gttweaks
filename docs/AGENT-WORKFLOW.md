# Working with Codex and Claude

The useful split is based on responsibility rather than file size. Claude gathers and organizes evidence cheaply; Codex decides what the evidence means and owns changes where a wrong assumption can create another regression.

Run a read-only task from the repository root:

```powershell
.\tools\claude-grunt.ps1 'Trace mouse drag from EMI RecipeScreen through ModularUI to the preview camera. Report every coordinate transform and cite methods.'
```

The script finds the newest Claude Code VS Code extension, runs Sonnet at low effort in plan mode, restricts it to read/search tools, and writes the report under `.agent-handoffs/claude/`. That directory is deliberately untracked. Keep each report task to one class, one log question, or one comparison; broad investigations waste both time and context.

For a mechanical edit whose design is already settled:

```powershell
.\tools\claude-grunt.ps1 -Mode Edit -Task 'Rename the specified diagnostic log prefix in these three files. Make no other changes.'
```

Codex should then inspect the diff, verify every factual claim used in the solution, run targeted checks, and either revise or reject the worker's output. Claude should not version, install, commit, push, or edit the live ATM10 instance.

The wrapper intentionally does not launch Claude automatically for every task. Codex decides when the work is bounded enough to delegate, then reads the saved handoff. If a report is still running after roughly two minutes, stop it and split the request into smaller pieces.

Good worker tasks are narrow and falsifiable:

- “List every caller of `ModularScreen.mouseDragged`; include descriptors and whether coordinates are absolute or relative.”
- “Extract all gttweaks lines plus the surrounding 20 lines from the latest five client logs.”
- “Compare 0.24.0 and 0.25.0 mixin configs and produce a table; do not diagnose.”
- “Decompile these three named classes and report where yaw and pitch change.”

Poor worker tasks transfer the hard decision instead of the labor:

- “Fix the EMI preview.”
- “Figure out the architecture.”
- “Make the mixins safe.”
- “Decide whether this result proves the bug is fixed.”

If Claude edits, use a clean worktree or ensure the current diff is understood first. One writer at a time is the default. Runtime Minecraft checks remain human-observed unless an automated harness exercises the exact interaction.
