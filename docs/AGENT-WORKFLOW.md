# Working with Codex and Claude

The split is based on responsibility, not file size. Claude drives execution —
investigation, implementation, builds, tests, and iteration on its own failures — and
should be given whole objectives with acceptance criteria, not single questions. Astra
(Codex) is the consulting principal: it owns hard architectural calls, design and risk
tradeoffs, final review, and anything touching the live Minecraft instance, versioning,
or commits. Astra's context is the scarce resource here, not Claude's — default to
delegating and reserve Astra for the moments where deep reasoning actually changes the
outcome.

Run the global wrapper from the repository root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File C:\Users\wrait\.codex\tools\claude-grunt.ps1 -Mode Report -Task 'Trace mouse drag from EMI RecipeScreen through ModularUI to the preview camera. Report every coordinate transform and cite methods.'
```

Modes:

- `-Mode Report` — read-only investigation, restricted to read/search tools.
- `-Mode Edit` — mechanical edits whose design is already settled, no shell/build.
- `-Mode Work` — full loop: edit, build, test, and iterate until acceptance criteria are
  met, or Claude reports precisely why they can't be.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File C:\Users\wrait\.codex\tools\claude-grunt.ps1 -Mode Work -Task 'Fix cleanroom preview hatch selection to prefer casing and floor energy hatches per the acceptance criteria in <issue/notes>. Build and test to green.'
```

Defaults are `-Model opus -Effort high`; drop to `-Model sonnet -Effort medium` for
genuinely mechanical work. The wrapper writes the full report to a handoff location
outside the repository and prints a concise `## SUMMARY` block. Read the summary first;
open the full report only when it's insufficient or the decision is consequential —
never reflexively, and never by pasting the whole file into the Astra thread.

Astra should then read the summary, verify proportionally to consequence (spot-check
routine work, verify directly anything touching init/persistence/item-safety/
client-server boundaries or preceding a commit or release), and either accept, revise,
or reject the result. Claude should not version, install, commit, push, or edit the live
ATM10 instance.

Delegate one objective per call, with acceptance criteria, and let it run to
completion — don't split an objective into a chain of single questions, and don't abort
a run just because it's taking a while; a long Claude run is cheap relative to an extra
Astra turn. If Claude gets genuinely stuck, it escalates to Astra with evidence attached
rather than thrashing.

Good delegated objectives are concrete and falsifiable, with acceptance criteria:

- "List every caller of `ModularScreen.mouseDragged`; include descriptors and whether
  coordinates are absolute or relative."
- "Extract all gttweaks lines plus the surrounding 20 lines from the latest five client
  logs."
- "Compare 0.24.0 and 0.25.0 mixin configs and produce a table; do not diagnose."
- "Implement and test the agreed fix for X; iterate until the reproduction case passes."

Poor delegated objectives transfer the hard decision instead of the work:

- "Figure out the architecture."
- "Make the mixins safe."
- "Decide whether this result proves the bug is fixed."

If Claude edits, use a clean worktree or make sure the current diff is understood
first. One writer at a time is the default. Runtime Minecraft checks remain
human-observed unless an automated harness exercises the exact interaction. Do not
treat a screenshot description or an unverified token/cost number in a report as fact —
confirm it directly before it drives a decision.
