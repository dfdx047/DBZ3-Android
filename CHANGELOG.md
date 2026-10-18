# Changelog

All notable changes to Dragon Fury. Release notes in three languages are in
[`docs/releases/`](docs/releases/). Detailed engineering log (Portuguese): [`docs/ESTADO.md`](docs/ESTADO.md).

## 0.6.14 — 2026-10-18

### Changed
- The render self-test runs in any scene of the game (Dragon Universe map, menus), not only in a
  fight: the freeze replays the frame's GPU commands, so it does not depend on the game mode. Needed
  to find which optimization breaks the Dragon Universe sky.

## 0.6.13 — 2026-10-18

### Fixes
- Devices with little RAM (moto g05, Mali-G52, closed by Android for low memory while loading):
  at 6 GB or less the texture cache is smaller (256/512 MB; 128/256 MB at 4.5 GB or less, Xenia's
  default is 384/768 MB), so less memory is held by old textures. `cvars.txt` can still change it.
- The device's RAM (total and free) is now in the launcher log and in the saved log.

## 0.6.12 — 2026-10-18

### Turnip on Adreno 6xx
- 0.6.11 measured in a fight (RP5, effects on): 17–29 fps, GPU ~28 ms/frame, of which resolves
  ~17 ms (14–21 resolves/frame, 10 Mpx) and drawing ~10 ms. On the system driver the same
  resolves cost ~8–9 ms with texture-only resolves and ~16 ms when they also write memory, so the
  forced memory writes from 0072 are the main loss on Turnip.
- 0072 is now the cvar `dbz3_turnip_a6xx_resolve_memory` (SDK patch 0075) and the render
  self-test toggles it, so one run shows both the GPU time saved and whether the HUD/menus break.

## 0.6.11 — 2026-10-18

### Turnip on Adreno 6xx
- GMEM with classic render passes also crashed on the RP5 (same submission: 1280x2048 passes +
  resolves), so Adreno 6xx stays on sysmem; mode 1 (sysmem with LRZ/UBWC) ran at ~34 fps in the
  fight vs ~27 in mode 2.
- In sysmem the safe mode now keeps the tight render area (clears/loads were done on the whole
  1280x2048 EDRAM area every pass), the full-overwrite color discard and the GPU timestamps, so
  the performance log shows where GPU time goes (SDK patch 0074).

## 0.6.10 — 2026-10-18

### Turnip on Adreno 6xx
- 0.6.9 on the RP5: GMEM (render by tiles) with dynamic rendering crashed the GPU in the menu,
  with and without LRZ/UBWC (1280x2048 passes + resolve in the GPU log); only sysmem worked, at
  ~27 fps in fights. New automatic ladder for Adreno 6xx, saved modes reset once:
  0 = GMEM with classic render passes (no dynamic rendering), no LRZ/UBWC;
  1 = sysmem with LRZ and UBWC (less bandwidth than before);
  2 = sysmem without LRZ/UBWC (what worked in 0.6.8/0.6.9);
  3 = + full sync and float depth; 4 = split guest memory.
  Turnip on Adreno 7xx/8xx keeps the old ladder.

## 0.6.9 — 2026-10-18

### Performance
- **Turnip on Adreno 6xx: GMEM back on.** Since 0.6.3 every Adreno 6xx started Turnip in mode 1
  (`TU_DEBUG=sysmem,nolrz,noubwc`), a workaround for crashes whose real causes are now fixed in the
  runtime. Sysmem rendering is the most expensive of them on Adreno. Turnip now starts in mode 0
  (no TU_DEBUG flags); saved modes are reset once. The automatic ladder is still there if the GPU
  crashes: 1 = no LRZ/UBWC, 2 = + sysmem, 3 = + simple passes/full sync, 4 = split guest memory.
- SDK patch 0073 reverts 0070 (read-only buffers back to `isam`; it was not the cause).

## 0.6.8 — 2026-10-18

### Fixes
- **Turnip on Adreno 6xx: HUD and menus.** 0.6.7 changed nothing on the RP5. The 0.6.6
  self-test already showed the answer: with `dbz3_direct_resolve_skip_memory=false` the fight
  frame is complete (HUD included). On Turnip + Adreno 6xx resolves now always write game memory
  (no texture-only resolves, no render-into-texture), SDK patch 0072.

## 0.6.7 — 2026-10-18

### Fixes
- **Turnip on Adreno 6xx: HUD, menus and effects.** With 0.6.6 the fights render with textures
  on Turnip; what was still missing (HUD, menus, striped effects) are textures produced by the GPU
  (resolves), which still went through the texture-load compute shaders. On Adreno 6xx, Turnip
  reads read-only storage buffers with `isam`, and vector reads (uvec4/uvec2) came back wrong.
  Internal compute shaders now mark those buffers `Coherent` instead of `NonWritable` on Turnip
  + Adreno 6xx, so the driver uses plain buffer loads (SDK patch 0070).
- The log lists textures that still take the GPU load path on Turnip + Adreno 6xx, with the
  reason (SDK patch 0071).

## 0.6.6 — 2026-10-18

### Fixes
- **Turnip on Adreno 6xx: textures.** The self-test on the RP5 with Turnip (Rutabaga) showed the
  geometry and vertex colors correct but no textures at all (black scenery, no HUD). On Turnip +
  Adreno 6xx the textures read from game memory are now untiled, endian-swapped and decompressed
  (DXT1/3/5, DXN, DXT5A, 565, 4444) on the CPU and uploaded with a plain buffer-to-image copy,
  skipping the texture-load compute shaders (SDK patch 0069, cvar `dbz3_tex_cpu_load`).
  Textures produced by the GPU (resolves) keep the normal path.

## 0.6.5 — 2026-10-18

### Diagnostics
- Frame captures now record the textures the GPU loaded (SDK patch 0068): when a capture starts,
  textures are reloaded from memory and, for each one, the output of the load shader and the
  Vulkan image after the copy are read back and saved. `tools/dfc_textures.py` compares them and
  compares two captures of the same scene (system driver vs Turnip), to find where Turnip on
  Adreno 6xx breaks the textures.

## 0.6.4 — 2026-10-18

### Changed
- **Adreno 6xx (Snapdragon 865, 855, 480+…) uses the system driver even when a Turnip driver is
  selected.** With every Turnip build tested on the Retroid Pocket 5 (R6, R8, Rutabaga) and every
  compatibility mode, the game drew wrong textures and the GPU crashed; the system driver draws
  correctly at 60 fps there. The Drivers tab explains it and has a switch to use Turnip anyway.
  Turnip on Adreno 7xx/8xx is unchanged.
- On Turnip + Adreno 6xx (when forced): original texture-sign path and explicit gradients, and no
  resolve readback during frame captures (SDK patch 0067).

## 0.6.3 — 2026-10-18

### Fixes
- **Turnip on Adreno 6xx works**: the RP5 logs (Turnip R6, R8 and Rutabaga) showed the GPU crash
  on the title screen in mode 0 with every driver, and fights running at 60 fps in compatibility
  mode 1. Adreno 6xx with a Turnip driver now always starts in mode 1 (no GMEM/LRZ/UBWC), BC (DXT)
  textures are always decompressed there, and `VK_EXT_shader_stencil_export` is off (depth to
  depth transfers with exported stencil crashed the GPU during the self-test). SDK patch 0066.
- The GPU-loss log also lists the last finished submission.

## 0.6.2 — 2026-10-18

### Fixes
- **Turnip on Adreno 6xx** (Snapdragon 865 / Retroid Pocket 5, 855, 480+…): the GPU crashed or hung
  on the title screen (`VK_ERROR_DEVICE_LOST`). These GPUs now get a safe mode by themselves: no
  GPU timestamps, simulated occlusion queries, full render area and no colour-discard passes. An
  occlusion query that ended inside a render pass (invalid in Vulkan) now ends outside it on every
  GPU (SDK patch 0065).
- **Automatic Turnip compatibility**: if the GPU still crashes with a Turnip driver, the next
  start adds safer settings by itself, one step at a time (mode 1: no GMEM/LRZ/UBWC and
  decompressed BC textures; 2: simple passes, float depth and full sync; 3: guest memory split as
  the driver reports). The error window says which mode is next; the Drivers tab shows the mode
  and can reset it.
- When the GPU is lost, the log lists the GPU work that had not finished (passes, resolves,
  texture loads, queries), so the exact command can be found from a user log.

## 0.6.1 — 2026-10-18

### Changed
- In-game menu rebuilt with tabs (Game, Video, Effects, Audio, Controls, Debug), large readable
  rows on an opaque panel, and full controller navigation: LB/RB switch tabs, D-pad or left stick
  move and change values, A confirms, B goes back, Start closes. Touch works on every row too.
  It now also has dithering, image mode (16:9 / stretch), texture packs and **Save log**.
- Bottom tab bar now uses icons and names again, with an orange highlight on the current tab.
- Community links are colored buttons for each network (YouTube, Discord, GitHub, Ko-fi).
- Section titles get a small gold-orange stripe again.
- Default upscaling filter is now **Bilinear** (AMD FSR costs a lot of GPU time); existing
  installs are switched once to Bilinear.

### Fixes
- When the GPU crashes or hangs during the game (`VK_ERROR_DEVICE_LOST`, seen on the Retroid
  Pocket 5 with a Turnip driver) the launcher now explains it and suggests the system driver or
  another Turnip version.
- Update check no longer shows an error when the repository has no published release yet.

## 0.6.0 — 2026-10-18

### New
- Logs for everything the app does (game import from ISO or folder, file checks, mods, texture
  packs, drivers, updates, settings, game start and exit), in the app's language, in
  `logs/app_launcher.log` and `logs/app_game.log` (included in the saved log).
- Errors are shown in a window that explains what happened, with technical details, a
  **Save log to Downloads** button and a link to the Discord. When the game closes with an error
  (crash, out of memory, frozen) the launcher says why when you come back.
- Processor check: Dragon Fury needs an ARMv8.2 CPU. On ARMv8.0 chips (Snapdragon 665/660/835…)
  the game used to close instantly; now the launcher explains it instead of starting.
- In-game menu rebuilt with the launcher's look, replacing the PC quick menu: picture, game
  effects (bloom, depth of field, FXAA) live, sound and vibration, touch controls and diagnostics.
  Opens with Back or **Select + Start** on a controller; the PC menu's Back + Start combo is off.

### Changed
- Simpler launcher: tab bar at the bottom, plain cards and buttons without icons, smaller header
  and community links.
- New Play screen animation: rocky ground cracking with a warm glow, rocks rising, dust and
  lightning.

## 0.5.8 — 2026-10-17

### Fixes
- Resolve dedupe: a destination that a real draw reads again twice after being marked "no reader"
  is never marked again (patch 0064). A screen copy the game reuses for several things per frame
  (one of them the FXAA, when off) was dropped and revived about 1.5 times per second, and each
  revival meant a draw reading stale content.

## 0.5.7 — 2026-10-17

### Fixes
- With the game's FXAA off, the copy that feeds it could be removed by the other resolve cuts,
  so in those frames the FXAA was drawn again and the cuts switched back on, in a loop (patch
  0063). The copy is now recorded before any cut, and its screen clear is dropped up front, so
  the cuts can also skip the copy itself.

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
