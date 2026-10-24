# Local dependency jars

This directory's jar files are intentionally ignored by Git. Obtain them from the matching development instance; this repository does not redistribute them.

`dependencies.sha256` records every jar staged for the initial 0.25.0 snapshot, including mods used by the optional development runs. The build currently includes all `libs/*.jar` as compile-only dependencies and test dependencies.

GregTech's pinned artifact is `gtceu-1.21.1-8.0.0-SNAPSHOT+20260822-141542-ef045f0.jar`. Extract its nested `META-INF/jarjar/modularui-mc1.21.1-3.3.1-SNAPSHOT.jar` here as well. A different snapshot may require different mixin targets even when its version string still says `8.0.0-SNAPSHOT`.

See [SETUP.md](../SETUP.md) for staging and checksum verification.

`rsapg-0.1.9.jar` (or newer, from github.com/wraithhawit/rs-advanced-pattern-grid) is needed to compile the optional rsapg recipe-tree integration in `rs/treedump`; gttweaks runs without rsapg installed.
