# Version history

This repository begins with the existing 0.25.0 source snapshot. Earlier development happened locally; the entries below summarize the immediately preceding changes and do not imply reconstructed Git history.

## 0.26.0 (candidate)

- Prefer Plastcrete over glass in the default upright cleanroom preview, using candidates from each position's actual pattern.
- Move energy input hatches to eligible Plastcrete floor positions before GT adjusts their facing.
- Preserve explicit block, count, and position preferences by skipping normalization on customized previews.
- Restore the 0.24.0 menu implementation; remove the 0.25.0-only mixins and their mocked routing tests. The broken experiment remains in Git history.

Build succeeds against the staged GT snapshot. Runtime application and visual verification remain outstanding; compilation is not an in-game test.

## 0.25.0

- Route GregTech EMI preview input using the current recipe page, replacing the global last-rendered screen reference.
- Refresh pointer hit-testing and the menu dispatch panel snapshot.
- Close cached dropdowns on page changes, screen removal and resize.
- Dismiss dropdowns with Escape and route typing to focused menu fields.
- Clean nested menus before closing their root; preserve the cached root menu.
- Add seven automated routing tests.

Build and unit tests pass, but in-game verification failed: the preview does not rotate and the C/M/X dropdowns do not respond. This version should not be released. See [manual checks](MENU-FIX-0.25.0.md).

## 0.24.0

- Cap optional bounded parts in expandable multiblock previews, including the Cleanroom's doors and passthrough hatches, while retaining required minimum counts and explicit player preferences.

In-game preview verification remains outstanding.

## Earlier local versions

Existing source includes ModularUI menu positioning/state fixes, preview camera fitting, EMI category icon guarding, circuit-panel fixes, partial data-component ingredients, and machine/recipe diagnostics. Detailed verified release history before 0.24.0 has not been reconstructed in this repository.
