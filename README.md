# GregTech Tweaks

NeoForge 1.21.1 addon supporting GregTech CEu Modern's integration into All the Mods 10.

The project started with compatibility fixes and now includes ModularUI/EMI menu fixes, multiblock preview adjustments, circuit-panel fixes, a partial data-component ingredient, and recipe/multiblock diagnostics.

This repository contains the Java addon. The ATM9 multiblock and recipe ports live in the Minecraft instance's KubeJS scripts and are not included here. GregTech and other dependency jars are not redistributed in this repository.

## Current status

**0.25.0 is a broken development snapshot.** It builds and its seven mocked menu-routing tests pass, but in game the multiblock preview no longer rotates and the C/M/X predicate dropdowns do not respond. This is direct evidence that the tests do not represent the live input path. Use 0.24.0 until the regression is diagnosed. The cleanroom preview count change from 0.24.0 also still needs verification, and the off-centre 3D preview rotation remains unresolved.

The addon is developed against this specific stack:

| Component | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.247 |
| GTCEu | 8.0.0-SNAPSHOT+20260822-141542-ef045f0 |
| ModularUI | 3.3.1-SNAPSHOT, bundled inside that GTCEu build |
| EMI | 1.1.24+1.21.1+neoforge |
| JEI | 19.44.0.401 |
| Java | 21 |

Compatibility with other GTCEu snapshots has not been established. The snapshot version label alone is insufficient to identify the binary; see the [dependency checksums](libs/dependencies.sha256).

## Build

Populate `libs/` as described in [SETUP.md](SETUP.md), then run:

```powershell
.\gradlew.bat build
```

On a Unix-like system, use `./gradlew build`. The addon jar is written to `build/libs/`. The standard build runs the menu-routing unit tests; these use mocked UI objects and do not test mixin application or rendering in Minecraft.

## In-game diagnostics

Commands require permission level 2:

```text
/gttweaks testmachine 500
/gttweaks testmachine 500 500
/gttweaks sweep greenhouse,star_forge
```

`testmachine` checks the formed machine the player is looking at, temporarily stocking empty inputs and simulating recipe matching and conditions. The second argument is an offset for pagination. It does not execute a complete craft. Commands run on the server thread, so large sweeps can stall the game.

Known limitations include network-backed stocking inputs, recipe-generated examples instead of real used data models or bee cages, and inventory-dependent modifiers being evaluated before test inputs are stocked. Unsupported tests currently contribute to the failure count. Recipes that failed to register are absent from a sweep entirely. Treat these commands as diagnostics, not proof that every intended recipe works.

## Development notes

- [Setup and dependency staging](SETUP.md)
- [Version history and verification status](CHANGELOG.md)
- [0.25.0 EMI dropdown changes and manual checks](MENU-FIX-0.25.0.md)
- [Codex/Claude worker workflow](docs/AGENT-WORKFLOW.md)

Recipes, materials and multiblock definitions generally belong in KubeJS. This addon holds Java integration and fixes that scripts cannot reach. Avoid forcing GregTech registry initialization early; its initialization order is significant.

The current ATM10 development instance requires EMI alongside JEI and `ingredientDedupe: false` in AllTheLeaks' configuration. Those are instance requirements; this addon does not rewrite the pack's configuration.

Original project code is all rights reserved; see [LICENSE](LICENSE).
