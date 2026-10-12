# Estado do port — DBZ3-Android

Documento de continuidade: o que existe, como compilar e o que falta.
Atualizado no fim de cada fase.

## Fase atual: 1 — primeiro APK (núcleo nativo, sem Compose)

Objetivo: o jogo arrancar no Android e chegar ao menu, com logs no logcat.

## Como funciona

- `external/rexglue-sdk-0.10`: SDK ReXGlue v0.10.0 + overlay do projeto original
  (`external/dbz3/patches/rexglue-sdk/`) + `patches/NNNN-*.patch` (aplicados por ordem).
- `external/dbz3`: projeto original (novapowers0) com `generated/` produzido
  pelo codegen a partir do `yae3_xenon.xex` USA. Nunca vai para o git.
- `app/src/main/cpp/CMakeLists.txt`: compila SDK + jogo em `libmain.so`.
- `MainActivity.java` (SDLActivity) define `REX_APP_DIR`, `REX_NATIVE_LIB_DIR`
  e `REX_DBZ3_SKIP_LAUNCHER=true` e arranca o `SDL_main`.
- `cvars.txt` na pasta da app (`nome = valor`) vira variáveis `REX_NOME`: testa
  opções do runtime sem recompilar.
- `tools/profile.sh`: perfil de CPU por thread/função com o simpleperf.

### O que o patch Android muda no SDK

| Ficheiro | Mudança |
|---|---|
| `thirdparty/CMakeLists.txt` | SDL3 partilhado (`libSDL3.so`) no Android, sem X11/Wayland/ALSA |
| `src/ui/CMakeLists.txt`, `surface_android.*`, `window_sdl.cpp` | superfície Vulkan a partir do `ANativeWindow` do SDL |
| `src/ui/windowed_app_main_sdl.cpp` | `SDL_main` do Android: init do runtime e pasta de dados |
| `src/core/main_android.cpp`, `include/rex/main_android.h` | `GetAndroidApiLevel`, pastas da app, stubs de `content://` |
| `src/core/fiber_posix.cpp`, `fiber.h` | fibras em assembly AArch64 (bionic não tem `ucontext`) |
| `src/core/filesystem_posix.cpp` | "pasta do executável" = `REX_APP_DIR` |
| `src/system/gpu_plugin_loader.cpp` | plugin da GPU carregado de `REX_NATIVE_LIB_DIR` |
| `src/core/logging.cpp` | logs do runtime no logcat (tag `dbz3`) |
| `include/rex/string/numeric.h` | parser de float portátil (libc++ do NDK) |
| `src/core/CMakeLists.txt`, `memory_posix.cpp` | fontes/libs Android, `-fexperimental-library` |
| `include/rex/chrono/chrono.h` (0002) | `clock_cast` próprio: o libc++ do NDK não o tem |
| `src/core/threading_posix.cpp` (0003) | sem mutexes robustos (o bionic não os tem) |
| `include/rex/ui/windowed_app.h` (0004) | uma só app, via `GetWindowedAppCreator()` como no Linux |
| `src/audio/sdl/sdl_audio_driver.cpp` (0005) | áudio sempre em estéreo no Android (as vozes estão no canal central) |
| `src/core/threading_posix.cpp` (0006) | `WaitMultiple` sem polling (Audio Worker estava a ~50% de um núcleo) |
| `src/system/mmio_handler.cpp` (0007) | faltas de páginas vigiadas sem ler `/proc/self/maps` a cada vez |

## Compilar (no PC, CachyOS)

1. `bash tools/apply-sdk-patches.sh` (uma vez por SDK limpo)
2. `local.properties` com `sdk.dir=/home/dfdx047/Android/Sdk` e `cmake.dir=/usr`
3. `./gradlew assembleRelease`
4. `adb install -r app/build/outputs/apk/release/app-release.apk`

Dados do jogo no aparelho: `/sdcard/Android/data/pt.dfdx047.dbz3/files/DBZ3/`
com `yae3_xenon.xex` e a pasta `us/`.

## Próximos passos

- [x] Fase 1: compila, instala e joga (Snapdragon 8 Elite / Adreno 830, 2026-10-12): partida completa sem crash
- [ ] Problemas vistos no 1.º teste: véu/blur na imagem; voz sem som (canal central perdido no 5.1, corrigido em 0005, por confirmar); Main XThread a 100% de um núcleo e Audio Worker a ~54%; travões ao criar pipelines (transformações, poderes); 120 Hz a apresentar o mesmo frame (pedido 60 Hz na MainActivity); aquecimento 70–80 °C
- [ ] Ciclo de vida: app em segundo plano / superfície perdida
- [ ] Controlos por toque
- [ ] Fase 2: launcher em Jetpack Compose (escolha da pasta via SAF, verificação de hashes, opções gráficas)
- [ ] Fase 3: otimização por GPU (Adreno/Mali), Turnip, cache de shaders

## Perfil (8 Elite, 2026-10-12, `tools/profile.sh`)

- Main XThread ~40% das amostras do processo: `sub_82235918` 54% e `sub_82231110` 12% dessa thread (código do jogo; a investigar se é espera ativa).
- Audio Worker ~21%: polling em `WaitMultiple` (corrigido em 0006).
- GPU Commands ~15%: `vkUpdateDescriptorSets` do driver Adreno é o maior custo.
