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
- Driver Vulkan alternativo: pôr o `.so` (ex. `libvulkan_freedreno.so` do Turnip) em
  `files/driver/` da pasta da app; a MainActivity copia-o para a pasta interna e o
  runtime carrega-o com o libadrenotools. Apagar o `.so` volta ao driver Qualcomm.

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
| `pipeline_cache.cpp`, `render_target_cache.cpp`, `device_1_0.inc` (0008) | `VkPipelineCache` do driver guardada em `cache/shaders/vulkan_driver_pipelines.bin` (o SDK não tinha nenhuma) |
| `dynlib`, `vulkan_instance.cpp` (0009) | driver Vulkan alternativo (Turnip) via libadrenotools, se `REX_VULKAN_DRIVER_DIR/NAME` estiverem definidos |
| `memory_posix.cpp`, `threading_posix.cpp` (0011) | commit de memória sem ler `/proc/self/maps` (era ~90% dos travões das transformações); `WaitMultiple` só acorda quem espera pelo objeto sinalizado |
| `dbz3_hitch.h`, `command_processor.cpp`, `pipeline_cache.cpp`, `texture_cache.cpp`, `render_target_cache.cpp` (0010) | diagnóstico de travões: cada `dbz3: tiron` (frame > 34 ms ou frames escondidos pela compilação async) diz quanto tempo foi em shaders, pipelines, pipelines provisórios, transferências de RT, texturas e espera da GPU |

### Patches no jogo (`patches/game/`, aplicados em external/dbz3)

| Patch | Mudança |
|---|---|
| 0001-unlock-all-characters | cheat `dbz3_unlock_all_characters = true` (cvars.txt): todos os personagens de fábrica no select, sem mexer no save |

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
- [x] Travões das transformações (0011) e travadinhas de pipelines (compilação síncrona): frame time reto, ~4–5 W (antes 15–16 W, 80–90 °C), 2026-10-12, Turnip
- [ ] Driver nativo Qualcomm: tentar contornar o véu/bordas verdes (bónus). Se não der, Turnip por omissão
- [ ] Fase 2: launcher em Jetpack Compose (escolha da pasta via SAF, verificação de hashes, opções gráficas)
  - Aba Adreno Tools: escolher/importar driver e descarregar direto dos repositórios
    (MrPurple666/purple-turnip, StevenMXZ, K11MCH1/AdrenoToolsDrivers); Turnip por omissão em Adreno
  - Aba Cheats (opcionais): todos os personagens (já existe: `dbz3_unlock_all_characters`),
    todas as cápsulas/transformações, zeni no máximo, fases. Os que faltam pedem RE
    (máscara de cápsulas do save/#SKC, zeni, fases) ou o save 100% (native_mods.cpp)
- [ ] Fase 3: otimização por GPU (Adreno/Mali), Turnip, cache de shaders

## Perfil (8 Elite, 2026-10-12, `tools/profile.sh`)

- Main XThread ~40% das amostras do processo: `sub_82235918` 54% e `sub_82231110` 12% dessa thread. Confirmado: é a espera do D3D do 360 por espaço no ring buffer da GPU (`cctpl/db16cyc` viram nada ao recompilar). Hook em `app/src/main/cpp/android_hooks.cpp` (yield e depois sleep de 50 µs).
- Audio Worker ~21%: polling em `WaitMultiple` (corrigido em 0006).
- GPU Commands ~15%: `vkUpdateDescriptorSets` do driver Adreno é o maior custo.

## Véu/verde na imagem (investigação)

- Piora com a câmara longe, melhora perto: suspeita do efeito de profundidade de campo/bloom.
- Sem efeito (2026-10-12): `depth_float24_convert_in_pixel_shader`, `readback_resolve=fast`,
  `gamma_render_target_as_unorm16=false`, `half_pixel_offset=false`.
- Turnip (Gen8 V37, 2026-10-12): véu e bordas verdes **desaparecem**. É bug do driver Qualcomm.
  O Turnip fica como opção; próximo passo: comparar o log do dispositivo Vulkan
  (extensões/features) dos dois drivers para achar o que contornar no nativo.

## Travões (investigação)

- Persistem com o Turnip, nos mesmos sítios: transformação, carregar ki, close-up na
  personagem, especial no máximo.
- 0010 mede a causa de cada travão: `adb logcat -d -s dbz3 | grep tiron`.
- 2026-10-12 (Turnip): os travões grandes (150–400 ms, em pares) não são da GPU nem
  do disco (`dbz3_io_logging`: nenhuma leitura lenta, máximo 0,5 ms). No perfil, durante
  um par: Main XThread a 100% ~180 ms e logo a seguir os 6 XThreads de trabalho do jogo
  ~240 ms, ambos com metade ou mais do tempo dentro do kernel. Próximo: perfil com pilha
  (`tools/profile-hitch.sh`) para saber que syscalls/faltas são.
- 2026-10-12 (perfil com pilha): a causa é `MmAllocatePhysicalMemoryEx` →
  `memory::AllocFixed` → `IsRangeFullyMapped`, que lia `/proc/self/maps` inteiro em cada
  commit. Os workers ainda perdiam tempo em `XEvent::Set` (notify global do 0006).
  Corrigido em 0011. Compilação async desligada por omissão (MainActivity): sem ela não
  há frames escondidos e os pipelines síncronos custam < 1 ms.
- Resultado do 0011 (2026-10-12, Turnip): travões de 150–420 ms desapareceram; o pior
  frame na partida de teste foi 46 ms (primeira vez de um efeito: 7 shaders, 4 ms).
- Atenção: o `dbz3_user.toml` (settings.cpp do projeto original) sobrepõe cvars do SDK.
  Para compilação síncrona usar `dbz3_async_shaders = false`, não `async_shader_compilation`.
