# Changelog

All notable changes to Dragon Fury. Release notes in three languages are in
[`docs/releases/`](docs/releases/). Detailed engineering log (Portuguese): [`docs/ESTADO.md`](docs/ESTADO.md).

## 0.3.0 — 2026-10-14

### Performance
- New GPU path for mobile tiler GPUs (patches 0028–0048): render targets in their own images,
  screen copies written straight into textures (imageStore), texture-format-specialized shaders,
  XDK `Clear()` drawn at 1x, uploads hoisted out of render passes, early transitions of copied
  textures, EDRAM restore draws/copies skipped.
- Snapdragon 865: ~37 → 60 FPS in fights (GPU ~26 → ~14.5 ms/frame). Mali-G57: 13 → 22–24 FPS.
- Game effect switches: bloom, depth of field, FXAA (`dbz3_fx_*`).
- 30 FPS mode = frameskip + present pacing at 30 (smooth on weak GPUs).

### Features
- Tag Team and online Duel from the PC project (v1.4.5.1); online identity = the APK's native libraries.
- Spanish UI; language follows the phone (pt / es / other → en).
- Launcher: Performance and Controls tabs, first-launch GPU/driver notice, Online and Starting form switches.
- Touch controls: new "map (flight)" layout, switched automatically in Dragon Universe.

### Fixes
- Game window is always full screen on Android (16:9 handhelds showed a 1920x918 surface).
- Dragon Universe controls: main-menu input filters from the PC project no longer apply while flying over the map.
- `xxhash.h` include path and `char8_t` fix for the new upstream sources.

## 0.2.0 — 2026-10-12
- First full Android build: launcher (import, drivers, mods, textures, cheats, settings), touch controls,
  Turnip via libadrenotools, pipeline cache, log export.
