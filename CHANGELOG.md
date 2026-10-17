# Changelog

All notable changes to Dragon Fury. Release notes in three languages are in
[`docs/releases/`](docs/releases/). Detailed engineering log (Portuguese): [`docs/ESTADO.md`](docs/ESTADO.md).

## 0.5.6 — 2026-10-17

### Mali GPUs
- On Mali GPUs (detected by `/dev/mali0`, or a MediaTek/Unisoc SoC) the launcher applies once a
  preset: 540p and the game effects (bloom, depth of field, FXAA) off. On the RG Cube (Mali-G57)
  that holds 60 FPS in fights; 720p with the effects on is about 30. Everything can still be
  changed in Performance, and the preset is not applied again.

## 0.5.5 — 2026-10-17

### Fixes
- Starting the game with the game's FXAA or depth of field turned off could leave the screen black
  (patch 0062). The effect draw was always skipped, but the screen was only kept for one exact
  resolve (1x, 1280x720, base 0); anywhere else the resolve feeding the effect cleared the screen.
  Now the resolve → effect pair is learned: the effect is skipped only when its input was copied
  this frame from the same render target it draws to, and the clear of that copy is dropped from
  the next frame on. Otherwise the effect is drawn (and logged).

## 0.5.4 — 2026-10-17

### Performance (Mali)
- Resolve copies are now drawn into the texture (a fragment pass) instead of a compute dispatch
  on Mali GPUs, for color and now also depth resolves (patches 0060/0061). On the RG Cube the color
  part alone took resolves from 14.1 to 9.9 ms per frame (39.5 → 35.1 ms GPU at 720p) with an
  identical picture. Other GPUs keep compute (`dbz3_resolve_draw_any_gpu` to try it there).

### Fixes
- Self-test probe "resolves only end the pass" no longer makes the textures reload from memory.

## 0.5.3 — 2026-10-17

### Fixes
- A resolve that writes guest memory now clears the "skipped memory" record of its destination
  (patch 0060). Before, switching the resolve shortcut off for a moment (or one frame where it
  failed) marked every destination as "needs memory" for good and resolves doubled (14 → 28 ms on
  the RG Cube).
- Render self-test: GPU milliseconds per state are now in the report itself; after a state that
  changed the picture the test checks that the config gives the reference again and takes a new
  reference if a broken state left garbage in the frozen frame.

### Experiments (self-test only)
- `dbz3_resolve_draw`: resolve copies as a full-screen draw instead of compute (Mali).
- `dbz3_resolve_debug = 2`: resolves only end the pass, without the copy, to split the copy cost
  from the pass-end cost.

## 0.5.2 — 2026-10-17

### Fixes
- Render self-test freeze: in Budokai 3 the frame swap comes inside an indirect buffer, so the
  frame boundary is now taken in the primary ring after that packet (patch 0059). The test also
  measures the game effects (bloom, depth of field, FXAA) for the Mali presets.
- More diagnostics: where each "sample" barrier that breaks a render pass comes from, and why
  render-into-texture finds 0 or several textures.

## 0.5.1 — 2026-10-17

### Fixes
- Render self-test: the picture now really freezes (patch 0058: empty indirect buffers no longer
  stopped the recording, larger frames fit, replays follow the recorded order); the FPS panel is
  hidden during the test and the report warns when the noise is too high to trust the verdicts.

## 0.5.0 — 2026-10-17

### Performance (renderer, patches 0050–0057, ported from Dragon Burst)
- Generic emulation-waste cuts, all live-switchable for the self-test: repeated resolves and
  resolves read only by skipped draws are skipped (`dbz3_resolve_dedupe`), draws that write
  nothing are skipped (`dbz3_skip_null_draws`), passes that start with an opaque full quad don't
  load color (`dbz3_discard_full_overwrite`), and passes that end in a color copy to a texture are
  learned and drawn straight into that texture (`dbz3_render_into_texture`, off on Turnip).
- Each part of direct render targets has its own switch (`dbz3_rt_direct_drop_transfers`,
  `dbz3_rt_direct_clear_siblings`, `dbz3_rt_keep_dd/cd/dc/cc`); the defaults keep 0.4.0 behavior.
- Driver pipeline cache: one file per Vulkan driver (system vs Turnip).
- Internal resolution below 720p: **540p** (75%, every size of the game stays exact, the safe
  choice for Mali) and **360p** (50%). The fast resolve-to-texture paths keep working below 1x.

### Features
- **120 FPS (interpolated)** on 110 Hz+ screens: the game runs at 60 and the presenter generates a
  frame between every two; the screen is no longer forced to 60 Hz (it follows the chosen rate).
- **Render self-test** (in-game menu, during a fight): freezes the picture on the GPU, toggles each
  optimization, compares screenshots, writes `logs/autoteste.txt` + JPGs and exports the logs zip
  to Download/ by itself.
- Detailed logs: frame captures now include the GPU readback of every resolve; new lines for the
  dedupe, discard and render-into-texture steps; `game_fps` / `presented_fps` / `interp=` in the
  perf line.

## 0.4.0 — 2026-10-16

### Launcher
- New look closer to the game's menus while keeping the Dragon Fury palette: italic heavy titles,
  dragon-ball orbs on section icons, slanted orange/gold stripes, subtle speed-line backdrop.
- Home: animated ki-beam clash (blue vs gold, sparks, lightning, shock waves; drawn in code),
  community links (YouTube, Discord, GitHub, Ko-fi) as capsule buttons in each network's colors.
- Settings reorganized: new **Debug** tab (log export, verbose log, performance diagnostics,
  renderer internals, Turnip A/B fixes, cvars.txt); orientation moved to Settings; performance
  tips in Performance.
- About rewritten after the original project's README: game sheet, the NovaPowers project, this
  port, credits and license.

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
