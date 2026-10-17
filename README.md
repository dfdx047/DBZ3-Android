<p align="center"><img src="docs/banner.png" alt="Dragon Fury — Dragon Ball Z: Budokai 3 HD for Android" width="100%"></p>

<p align="center">
<b>English</b> · <a href="README.pt-BR.md">Português (Brasil)</a> · <a href="README.es.md">Español</a>
</p>

# Dragon Fury

**Dragon Fury** is a native Android port of *Dragon Ball Z: Budokai 3 HD* (from the
*Budokai HD Collection* on Xbox 360). It is built on the PC recompilation by
[novapowers0](https://github.com/novapowers0/DBZ-Budokai-3-HD-Collection) (ReXGlue SDK) and
brings it to ARM64 phones and handhelds, with its own launcher, touch controls, Tag Team, online
and a GPU path rewritten for mobile GPUs.

> **No game files are included.** You need your own copy of the game (USA version).

## Highlights

- **60 FPS at the native 720p on a Snapdragon 865** (Retroid Pocket 5), with the system Qualcomm
  driver. Started at ~37 fps; the gain came from rewriting how the Xbox 360 GPU work maps onto
  mobile tiler GPUs (see [Performance](#performance)).
- Runs with the **system driver** on Adreno and Mali. **Turnip** also works well (optional).
- **Tag Team** mode and **online** Duel (1P vs 2P) from the PC version.
- Launcher with **mods**, **HD texture packs**, **cheats**, driver manager, per-device performance
  options, touch controls editor and log export.
- English, Portuguese (Brazil) and Spanish — picked from the phone's language automatically.

## Requirements

| | |
|---|---|
| Android | 11 or newer, 64-bit (arm64-v8a) |
| GPU | Vulkan 1.1 — Adreno 6xx or newer recommended; Mali (Valhall, e.g. G57/G610/G710) works |
| Storage | ~3 GB free (the game is copied into the app, ~2.5 GB) |
| Game | *Dragon Ball Z: Budokai HD Collection* — **USA** Xbox 360 copy |

### Tested devices

| Device | SoC / GPU | Result |
|---|---|---|
| Retroid Pocket 5 | Snapdragon 865 / Adreno 650 | 60 FPS locked in fights (system driver and Turnip) |
| AYN Odin 3 | Snapdragon 8 Elite | Runs (first device the port booted on) |
| Anbernic RG Cube | Unisoc T770 / Mali-G57 | ~22–24 FPS in fights; use the 30 FPS mode and turn effects off |

Other Snapdragon 865-class chips and newer (Snapdragon 8 Gen 1/2/3, Dimensity 1100+, recent
Exynos) are expected to run it at or near full speed. Reports are welcome.

## Installation

1. Download the latest `DragonFury-*.apk` from [Releases](../../releases) and install it.
2. Copy the `.iso` of your Budokai 3 HD Collection (USA) disc to your device.
3. Open Dragon Fury → **Import .iso** → pick the file. Only the Budokai 3 executable and its data
   are extracted from the image (about 2.5 GB, a few minutes); the executable is checked by
   SHA-256. You can delete the `.iso` afterwards.
   - Already extracted the disc? **Extracted folder** → pick the `DBZ3` folder (it contains
     `yae3_xenon.xex` and the `us/` folder).
4. Press **Play**.

## Launcher

| Tab | What it does |
|---|---|
| **Home** | Ki clash animation, play, import the game (.iso or folder), community links (YouTube, Discord, GitHub, Ko-fi), shortcuts. |
| **Performance** | 30/60/120 FPS, internal resolution (360p–2160p), game effects (bloom, depth of field, FXAA), image filters (FSR 1, CAS, sharpness), aspect ratio, performance tips. |
| **Controls** | Touch controls (layouts for menus, fights and the Dragon Universe map; on-screen editor, opacity, vibration) and physical controller (dead zone, rumble). |
| **Mods** | Install mods from `.zip` (same format as the PC version's `mods/` folder). |
| **Textures** | Import and manage HD texture packs. |
| **Cheats** | Unlock all characters (more cheats are being reverse-engineered). |
| **Drivers** | System driver or Turnip; download drivers in-app (Mr. Purple, StevenMXZ, K11MCH1) or import a `.zip`. |
| **Settings** | Audio, game language, Tag Team / Online / starting form switches, orientation, theme (Dragon Fury or Material You), app language, updates. |
| **Debug** | Log export, verbose log, performance diagnostics, renderer internals, Turnip A/B fixes, `cvars.txt`. |
| **About** | Game sheet, the original project (NovaPowers), this port, credits and license. |

On first launch the app shows a short note about drivers and GPUs; it can be reopened from **Performance**.

## Performance

The game is light for a modern phone, but emulating the Xbox 360 GPU (Xenos, with its 10 MB
EDRAM) on a mobile tiler GPU was the bottleneck: every "pass break" forces the GPU to write the
whole 1280x720 color + depth buffers to memory and read them back. This port:

- keeps each render target in its own image instead of copying EDRAM contents around;
- writes copies of the screen straight into the texture the game reads (no round trip through
  emulated memory);
- specializes the translated shaders per texture format;
- draws the XDK `Clear()` (a half-width 4x MSAA rectangle on Xbox 360) directly at 1x;
- moves memory uploads out of render passes and transitions copied textures early;
- skips EDRAM "restore" draws and copies that only existed because of the Xbox 360 EDRAM layout.

Result on the Snapdragon 865: ~37 → 60 FPS (GPU time per frame ~26 → ~14.5 ms). On the Mali-G57:
13 → 22–24 FPS. The full log of measurements is in [`docs/ESTADO.md`](docs/ESTADO.md) and the
reusable method in [`docs/GUIA_PORT_XBOX360_ANDROID.md`](docs/GUIA_PORT_XBOX360_ANDROID.md)
(Portuguese).

### Tips for weaker GPUs (Mali, Adreno 6xx below the 865)

- **Performance → 30 FPS mode**: the game keeps running at full speed and draws every other frame,
  presented at an even 30 FPS.
- **Performance → Game effects**: turn off bloom, depth of field and FXAA.
- **Performance → Internal resolution → 540p**: draws 56% of the pixels and every size of the game
  stays exact (360p is the lightest).
- On 120 Hz screens, **Performance → Frame rate → 120 (interp.)** generates a frame between every
  two (needs a steady 60 FPS first).
- After each update, the **render self-test** (in-game menu, during a fight) tells which
  optimization changes the picture on your GPU and how much each one is worth; the logs zip goes to
  Download/ at the end.

## Drivers

The port was optimized on **Qualcomm's system driver**: Snapdragon 865 and newer run it with the
driver that ships with the device. **Turnip** (open-source Adreno driver) also works well now and
may use less power on some devices; choose it in **Drivers** if you want. Mali devices use the
system driver.

## Tag Team

Team battles from the PC version: **TAG TEAM** appears in the main menu. Pick your team and swap
fighters during the fight with **Back/Select** (the "swap" button on the touch controls).
Can be turned off in **Settings**.

## Online (1P vs 2P)

1. Both players must use **the same Dragon Fury APK version** (Android plays with Android only; it
   can't play against the PC version) and the same mods.
2. In the **Duel** menu, choose the fifth row, **ONLINE**.
3. **Host:** press **Create match**. A 10-letter **room code** (`XXXXX-XXXXX`) appears.
   **Guest:** type the room code (or the host's IP) and press **Join**.
4. The host presses **Start**; pick **1P vs 2P** — the host is player 1.
5. Press **X** in the Duel menu to leave.

Network notes:
- There is no automatic port opening (UPnP) on Android. **Same Wi-Fi network** works out of the
  box (use the host's local IP). Over the internet the host must forward **UDP port 7777** on the
  router, or both use a VPN such as Tailscale/ZeroTier.
- Mobile data usually can't host (carrier NAT).
- The match runs in lockstep: a stable connection matters more than speed. Pausing the app ends the
  session.

## Mods, textures and cheats

- **Mods:** same format as the PC version (one folder per mod). Character packs must already contain
  the assembled `_roster` folder (the Python builder doesn't run on Android). Character mods need
  the USA copy.
- **HD textures:** import packs in the **Textures** tab.
- **Cheats:** "Unlock all characters" (does not touch your save). Capsules, Zeni and stages are
  being reverse-engineered.

## Building from source

The repository contains the Android project and patches, not the game or the generated code.

1. Clone this repo into `~/AndroidStudioProjects/DBZ3-Android`.
2. Put the ReXGlue SDK v0.10 in `external/rexglue-sdk-0.10` and the
   [original project](https://github.com/novapowers0/DBZ-Budokai-3-HD-Collection) in
   `external/dbz3`, with `generated/` produced by the ReXGlue codegen from your own `yae3_xenon.xex`.
3. `bash tools/sync-upstream-overlay.sh && bash tools/apply-sdk-patches.sh`
4. `./gradlew assembleRelease`

Details in [`docs/ESTADO.md`](docs/ESTADO.md).

## Roadmap

- More optimization for Mali GPUs (own shaders for the game's ~33 shaders, native renderer).
- Fix the occasional black flash in menus on Mali.
- More cheats (capsules, Zeni, stages) and widescreen patch.

## Credits

- [novapowers0](https://github.com/novapowers0/DBZ-Budokai-3-HD-Collection) — PC recompilation,
  Tag Team, online, mods.
- ReXGlue SDK and the [Xenia](https://xenia.jp) project — recompilation runtime and GPU emulation.
- SDL, Vulkan, libadrenotools, Turnip/Mesa and the driver packagers (Mr. Purple, StevenMXZ, K11MCH1).
- Android port, launcher and mobile GPU work: **dfdx047**, with Claude (Anthropic).

## Disclaimer

Dragon Fury is a fan project, not affiliated with Bandai Namco, Toei Animation or Microsoft.
*Dragon Ball* is a trademark of its respective owners. No copyrighted game data is distributed;
you must own the game.
