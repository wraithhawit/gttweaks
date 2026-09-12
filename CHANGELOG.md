# Version history

This repository begins with the existing 0.25.0 source snapshot. Earlier development happened locally; the entries below summarize the immediately preceding changes and do not imply reconstructed Git history.

## 0.29.0 (candidate)

- Remove duplicate camera-position subtraction in schema rendering, keeping blocks, block entities, highlighting and picking on the same transform.
- Aim the camera at the geometric block center and compensate fitted distance for the removed duplicate translation.
- Three matrix regression tests pass alongside the five existing tests. Actual rendered behavior remains unverified; pan and zoom sensitivity may feel lower.
- Restore the 1.20 Assembly Line recipe page in EMI. The 8.0 snapshot still ships `assembly_line.rtui` but `loadRecipeTypeUIFromFile` throws, so a custom layout rebuilds it from that file's positions: 4x4 item grid, connector bar, four fluids, output, arrow and research slot. Confirmed in-game.
- Put the EU "Total / Usage" lines back on that page; the custom layout skipped the builder that creates them. Confirmed in-game.
- Fill the research slot with each research entry's data item, as 1.20 did, and drop the snapshot's separate `research_info` row on that page. The empty slot shows the data-orb icon under EMI's hover highlight (confirmed in-game); a recipe that actually needs research has not been checked yet.
- `TestMachineCommand` now carries the modified recipe through each modifier step and lets ME stocking buses and hatches serve inputs from the network. Every 0.29.0 jar built from this point includes it.

## 0.28.0 (submenu fix confirmed in-game)

- Fix child removal in ModularUI: `Widget.equals` rejects subclasses even when an object is compared with itself, making `List.remove(menu)` fail. Redirect only that list removal in `AbstractParentWidget.remove(IWidget)` to an identity lookup. Original disposal and removal callbacks still execute.
- Reproduce failed removal with actual `Menu` objects from the exact bundled ModularUI jar, then verify identity removal, sibling preservation, and cached-menu reopening. All five tests pass; in-game application remains unverified.

## 0.27.0 (failed in-game)

- Resolve submenu ancestry from attached child trees rather than potentially invalid parent pointers; always retain the root menu, remove inactive branches using actual panel children, and clear the detached buttons' flags.
- Log the first eight submenu cleanup decisions for runtime verification.
- Correct the cleanroom casing registry ID from the nonexistent `gtceu:plastcrete` to `gtceu:plascrete`. The 0.26 injection ran but reported zero replacements.
- Four branch-selection tests pass: sibling switching/reopening, deeper ancestors, returning to another root choice, and wrapped children. These do not test the live rendered UI.

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
