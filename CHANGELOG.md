# Changelog

All notable changes to Dragon Fury. Release notes in three languages are in
[`docs/releases/`](docs/releases/). Detailed engineering log (Portuguese): [`docs/ESTADO.md`](docs/ESTADO.md).

## 0.3.2 — 2026-10-16

### Features
- Import straight from the Xbox 360 disc image (.iso XGD2/XGD3 or XISO): only the Budokai 3
  executable and its USA data are extracted; no external tool needed.
- Faster import (ISO and folder): positional reads + in-kernel copy (sendfile), two files in
  parallel, free-space check before copying, import time shown at the end.

## 0.3.1 — 2026-10-15

### Fixes
- Returning from the background no longer leaves a black screen (the Vulkan surface is recreated on
  the new Android window).
- Launch orientation is respected: SDL no longer overrides it with "full sensor" (landscape /
  reversed / automatic landscape).
- Turning FXAA off no longer gives a black screen; turning bloom off also skips the bloom render
  targets and copies.
- Fewer black flashes: depth copies are never skipped, and fast resolves fall back to a full resolve
  when another texture reads that memory.
- Turnip: guest memory in a single 512 MB buffer automatically on Adreno 6xx; a custom driver that
  can't drive this GPU falls back to the system driver (shown in the Drivers tab); drivers packaged
  with extra libraries now load.

### Performance
- Render passes only load/store the area actually drawn (1280x720 instead of the whole 1280x2048
  EDRAM image): much less memory traffic on tiled GPUs (Mali, Adreno).

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
