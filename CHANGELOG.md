# Version history

This repository begins with the existing 0.25.0 source snapshot. Earlier development happened locally; the entries below summarize the immediately preceding changes and do not imply reconstructed Git history.

## 0.29.0 (candidate)

- Remove duplicate camera-position subtraction in schema rendering, keeping blocks, block entities, highlighting and picking on the same transform.
- Aim the camera at the geometric block center and compensate fitted distance for the removed duplicate translation.
- Three matrix regression tests pass alongside the five existing tests. Actual rendered behavior remains unverified; pan and zoom sensitivity may feel lower.
- Restore the 1.20 Assembly Line recipe page in EMI. The 8.0 snapshot still ships `assembly_line.rtui` but `loadRecipeTypeUIFromFile` throws, so a custom layout rebuilds it from that file's positions: 4x4 item grid, connector bar, four fluids, output, arrow and research slot. Confirmed in-game.
- Put the EU "Total / Usage" lines back on that page; the custom layout skipped the builder that creates them. Confirmed in-game.
- Fill the research slot with each research entry's data item, as 1.20 did, and drop the snapshot's separate `research_info` row on that page. The empty slot shows the data-orb icon under EMI's hover highlight (confirmed in-game); a recipe that actually needs research has not been checked yet.
- Accept GregTech 7.x bare capability keys (`"item"`, `"fluid"`, `"eu"`) in recipe JSON by reading them as `gtceu:` ids. The 8.0 snapshot resolved them to `minecraft:item` and rejected the recipe, so none of Productive Bees' 27 GregTech bee recipes loaded. Applied to `RecipeCapability`'s content codecs, the serializer's chance-logic map and KubeJS's `RecipeCapabilityComponent`; namespaced keys and encoding are unchanged. Headless server: bee recipe parse errors 27 → 0, KubeJS component warnings on them 81 → 0. Four unit tests cover the key handling. Not yet run in the game instance.
- `pack/kubejs/` now mirrors the instance's GregTech KubeJS scripts and data overrides so they are versioned with the addon.
- Pack script (instance KubeJS, mirrored in `pack/kubejs/server_scripts/gttweaks/progression_skips.js`): remove Modern Industrialization's titanium dust → hot ingot recipe (`materials/titanium/blast_furnace/dust`). With Industrial Foregoing generating ore → dust from raw + dust tags, it gave titanium ingots long before GregTech's HV electric blast furnace; MI keeps its intended raw-ore route (`titanium_from_raw`). Further skips are tracked in issue #1. Confirmed in game.
- Pack data (instance KubeJS, mirrored in `pack/kubejs/data/modern_industrialization/recipe/`): gate Modern Industrialization's circuits behind GregTech circuit tiers by overriding both the crafting-table (`electric_age/circuit/craft/*_asbl`) and assembler (`assembler_generated/...`) recipes under their original ids. Analog: bottom-middle copper wire → `#gtceu:circuits/lv` (assembler: copper wire 3 → 2 + 1 LV). Electronic: bottom two analog circuits → `#gtceu:circuits/mv` (assembler: 4 → 2 + 2 MV). Digital: bottom two electronic circuits → `#gtceu:circuits/ev` (4 → 2 + 2 EV). Quantum: bottom two processing units → `#gtceu:circuits/luv` (4 → 2 + 2 LuV). Processing unit unchanged. No other mod makes these circuits and nothing adds MI circuits to GregTech's circuit tags. Confirmed in game: all four circuits show the GregTech circuit inputs.
- Pack data (instance KubeJS, mirrored in `pack/kubejs/data/productivebees/recipe/centrifuge/gtceu/`): Productive Bees 1.21 dropped the honeycomb centrifuge recipes for 26 of its 28 GregTech bees (only graphite and neutronium remain). Ported ATM9's 1.20 versions to the 1.21 format: wax, the bee's raw ore at 80% (sheldonite gives raw cooperite) and 50 mB honey, gated on the raw-ore tag being non-empty. 24 confirmed loading in game (Comb Processor count 643 → 715). Molybdenum and neodymium were skipped: both have a gtceu and a chemlib bee definition under the same short name, so `productivebees:bee_exists` can read the chemlib one (ChemLib is not installed) and fail. Those two now output `gtceu:raw_molybdenum` / `gtceu:raw_neodymium` directly, gated on `neoforge:item_exists` instead of the raw-ore tag and bee check; headless server: both load. Not yet confirmed in game.
- Pack fix (instance KubeJS, mirrored in `pack/kubejs`): ATM10's `Modern Industrialization/Aluminum.js` removed recipes with `{id: /.*bauxite.*/}`, which with GregTech installed also deleted 116 non-MI recipes — GregTech's whole bauxite ore chain plus Productive Bees' `gtceu:alloy_smelter/bauxite_bee` and `bee_produce/gtceu/bauxite_bee`. Found with the recipe probe: present in the raw datapack files and at the start of the KubeJS recipe event, gone at its end. Scoped to `/^modern_industrialization:.*bauxite.*/`; headless check: old regex matches 116, scoped regex 0. A pack update will restore ATM's original file. Not yet confirmed in game.
- Pack quests (instance `config/ftbquests/quests`, mirrored in `pack/ftbquests/`): port ATM9's GregTech™ chapter group to ATM10 with `tools/port_gregtech_quests.py` — 13 chapters, 751 quests and the 12 GT reward tables, ids unchanged, group placed after Tech as in ATM9. Text moves from ATM9's `{atm9.quest...}` KubeJS keys into FTB Quests lang files (English plus the es_es, fr_fr, it_it, ja_jp, ko_kr, nb_no and pt_br translations ATM9 had; the other locales fall back to English). Items move to 1.21 components: item-filter tags and or-lists become FTB Filter System smart filters (`forge:tools/hammers` → `c:tools/hammer` etc.), dark tanks carry `cyclopscore` fluid components, names and unbreakable survive, GregTech tool NBT is dropped. Substitutions follow ATM10's own GregTech KubeJS port: End Steel Exchanger → Tier 4 Pocket Storage Unit and the EnderIO exchanger core → Tier 3 (with rewritten English text), mega blast furnace → rotary hearth furnace, `oil_medium` → `raw_oil`, raw trinium → trinium dust, raw gold/plutonium and ChemLib dusts → their current ids. The infinite polonium cell quest has no ATM10 item (the recipe was dropped), so its task is a checkmark. ATM10's lang files kept 26–28 stale entries under some of these ids (e.g. French "Botanique" on the GregStar chapter); they are replaced or removed so they fall back to English. Converter checks: 0 unresolved items, tags, fluids, dependencies, loot tables or page links; every other lang entry unchanged byte for byte. Installed in ATM10(3) on 2026-09-12 (147 files, checksums match; the prior quests folder is zipped in the gitignored `backups/`). Not yet checked in game.
- Pack item (instance KubeJS, mirrored in `pack/kubejs/startup_scripts/gttweaks/doge_coin.js` and `pack/kubejs/assets/kubejs/textures/item/doge_coin.png`): register a decorative `kubejs:doge_coin` for the GregStar quest's "GregStar Champion" reward. ATM9's quest named `gtceu:doge_coin`, which no GTCEu 1.20 build registered (7.2 kept only its texture and a stale uk_ua lang key), so the reward was an unknown item there too. The texture is GTCEu 7.2's. Not yet loaded in game.
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
