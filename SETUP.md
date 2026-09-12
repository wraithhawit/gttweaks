# Building GregTech Tweaks

## Requirements

- JDK 21, with `JAVA_HOME` pointing to it.
- The Gradle wrapper included in this repository.
- Local copies of the dependency jars listed in [libs/README.md](libs/README.md).

The current build uses jars staged from the development modpack rather than Maven coordinates for mod dependencies. A fresh clone therefore needs `libs/` populated before it can compile. The Gradle wrapper and normal NeoForge build dependencies are downloaded on the first build; `--offline` works only after those dependencies have been cached.

## Stage dependencies

Copy the jars named in `libs/dependencies.sha256` from the matching Minecraft instance into `libs/`. ModularUI is nested at `META-INF/jarjar/modularui-mc1.21.1-3.3.1-SNAPSHOT.jar` inside the pinned GregTech jar; extract that nested jar into `libs/` for compilation. Do not unpack mod contents into the source tree.

The checksum manifest records the exact files used for the initial 0.25.0 repository snapshot. In PowerShell, verify them with:

```powershell
Get-Content libs/dependencies.sha256 | ForEach-Object {
    $expected, $name = $_ -split '  ', 2
    $actual = (Get-FileHash -LiteralPath (Join-Path 'libs' $name) -Algorithm SHA256).Hash
    if ($actual -ne $expected) { throw "Dependency checksum mismatch: $name" }
}
```

## Build and test

```powershell
.\gradlew.bat build
```

This compiles the addon, packages its jar, and runs the JUnit/Mockito routing tests. It does not launch a client or validate rendered menus. The artifact is `build/libs/gttweaks-<version>.jar`.

## Optional development runs

The `runClient` and `runServer` tasks stage `libs/*.jar` plus the instance's GregTech KubeJS scripts into `run/client` or `run/server`. The current build retains the original developer's default instance path; pass your own Minecraft directory explicitly:

```powershell
.\gradlew.bat runClient "-Pinstance=D:/path/to/instance/minecraft"
.\gradlew.bat headlessCheck -PexitAfterCheck=true "-Pinstance=D:/path/to/instance/minecraft" -PrecipeSweep=greenhouse,star_forge
.\gradlew.bat validateKubeJS "-Pinstance=D:/path/to/instance/minecraft"
```

`validateKubeJS` also requires that instance's generated ProbeJS types in `.probe`. Development runs contain a subset of the full modpack; missing pack dependencies can prevent scripts or recipes from loading. `gt_infinity_cells.js` is deliberately excluded from the development staging task because its extra dependencies are not staged.

Check the verification summaries and errors in `run/server/logs/latest.log`. A successful process exit or zero recipe-match failures does not establish that every intended recipe registered.

## Install

Close Minecraft, retain the previous addon jar outside the instance's `mods/` directory, and put the new jar in `mods/`. Keep only one gttweaks version there. For 0.25.0, follow the manual menu checks in [MENU-FIX-0.25.0.md](MENU-FIX-0.25.0.md) before marking the UI fixes verified.
