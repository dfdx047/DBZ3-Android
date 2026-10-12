# Estado do port — DBZ3-Android

Documento de continuidade: o que existe, como compilar e o que falta.
Atualizado no fim de cada fase.

## Fase atual: 2 em curso — launcher Dragon Fury escrito (branch `fase2-dragon-fury`), falta compilar e testar

Objetivo da Fase 2: launcher próprio (Dragon Fury) com importação do jogo, opções, mods,
texturas, drivers, controles por toque e atualizador. Fase 1 (jogo a arrancar e a jogar) concluída.

## Como funciona

- `external/rexglue-sdk-0.10`: SDK ReXGlue v0.10.0 + overlay do projeto original
  (`external/dbz3/patches/rexglue-sdk/`) + `patches/NNNN-*.patch` (aplicados por ordem).
- `external/dbz3`: projeto original (novapowers0) com `generated/` produzido
  pelo codegen a partir do `yae3_xenon.xex` USA. Nunca vai para o git.
- `app/src/main/cpp/CMakeLists.txt`: compila SDK + jogo em `libmain.so`.
- `LauncherActivity` (Compose) é a entrada da app; o `GameActivity.java` (SDLActivity,
  antes `MainActivity`) define `REX_APP_DIR`, `REX_NATIVE_LIB_DIR`,
  `REX_DBZ3_SKIP_LAUNCHER=true` e `REX_LOG_FILE` e arranca o `SDL_main`.
- `cvars.txt` na pasta da app (`nome = valor`) vira variáveis `REX_NOME`: testa
  opções do runtime sem recompilar.
- `tools/profile.sh`: perfil de CPU por thread/função com o simpleperf.
- Driver Vulkan alternativo: pôr o `.so` (ex. `libvulkan_freedreno.so` do Turnip) em
  `files/driver/` da pasta da app; o GameActivity copia-o para a pasta interna e o
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

Dados do jogo no aparelho: `/sdcard/Android/data/com.dfdx047.dragonfury/files/DBZ3/`
com `yae3_xenon.xex` e a pasta `us/` (o launcher importa-os por SAF). O pacote mudou
(antes `pt.dfdx047.dbz3`): desinstalar o APK antigo e importar o jogo de novo.

Só o launcher, sem o NDK (rápido para mexer na interface; o jogo não abre):
`./gradlew assembleRelease -Pdf.uiOnly=true`

## Próximos passos

- [x] Fase 1: compila, instala e joga (Snapdragon 8 Elite / Adreno 830, 2026-10-12): partida completa sem crash
- [ ] Problemas vistos no 1.º teste: véu/blur na imagem; voz sem som (canal central perdido no 5.1, corrigido em 0005, por confirmar); Main XThread a 100% de um núcleo e Audio Worker a ~54%; travões ao criar pipelines (transformações, poderes); 120 Hz a apresentar o mesmo frame (pedido 60 Hz no GameActivity); aquecimento 70–80 °C
- [ ] Ciclo de vida: app em segundo plano / superfície perdida
- [ ] Controlos por toque
- [x] Travões das transformações (0011) e travadinhas de pipelines (compilação síncrona): frame time reto, ~4–5 W (antes 15–16 W, 80–90 °C), 2026-10-12, Turnip
- [x] Driver nativo Qualcomm: decidido usar **Turnip por omissão** (ver secção do véu)
- [ ] Fase 2: launcher **Dragon Fury** em Jetpack Compose — plano completo na secção
  "Fase 2 — Dragon Fury" abaixo
- [ ] Fase 3: otimização por GPU (Adreno/Mali), Turnip, cache de shaders

## Fase 2 — Dragon Fury (launcher)

Escrito em 2026-10-12 na nuvem, sem compilador Android (o Maven do Google estava
bloqueado): **a primeira compilação é no PC**. A camada de dados, os controles e o
GameActivity foram verificados com `kotlinc`/`javac` contra o android.jar (API 35) e
stubs; a interface Compose não foi compilada.

### Onde está cada coisa (`app/src/main/java/com/dfdx047/dragonfury/`)

| Pasta / ficheiro | O quê |
|---|---|
| `LauncherActivity.kt`, `ui/` | launcher Compose: abas Início, Mods, Texturas, Cheats, Drivers, Configurações, Sobre; retrato (abas no topo) e paisagem (barra lateral) |
| `ui/theme/Theme.kt` | tema **Dragon Fury** (laranja/azul/amarelo, claro e escuro) e **Material You** (Android 12+) |
| `res/values`, `res/values-pt` | inglês (padrão) e português; celular em `pt-*` → português, outro idioma → inglês; escolha manual nas Configurações (AppCompat, `locales_config.xml`) |
| `data/GameConfig.kt`, `TomlLite.kt` | opções do jogo no `dbz3_user.toml` (merge, mantém o que o menu F1 escreve); o launcher relê-o ao voltar do jogo |
| `data/GameFiles.kt` | importar o jogo por SAF (pasta com `yae3_xenon.xex`/`default.xex` + `us/`), SHA-256 do xex, tamanhos/hashes dos .afs (`baserom.md`) |
| `mods/ModRepository.kt` | mods no formato do original: `.disabled`, `manifest.txt`, tipos, instalar .zip (zip-slip), desembrulhar, pack com `mods/` na raiz, verificações, conflitos |
| `textures/` | validação dos packs (nome, cabeçalho PNG/DDS, formato DDS, fator 1–4×, duplicados, RAM), importar imagens soltas, log do runtime |
| `drivers/DriverRepository.kt` | drivers AdrenoTools (.zip com meta.json/.so): baixar das releases, importar, ativar (copia para `driver/`), voltar ao do sistema |
| `update/UpdateChecker.kt` | `releases/latest` do GitHub; changelog em `## PT` / `## EN`; baixa o .apk e abre o instalador |
| `controls/` | modelo do layout (JSON), ações do jogo, editor (`ControlsEditorActivity`) |
| `game/GameActivity.java` | jogo; orientação (paisagem / invertida / automática), log em `logs/dragonfury_last.log`, overlay |
| `game/GameOverlay.kt`, `TouchControlsView.kt`, `HelperEngine.kt` | controles por toque, estilos Dragon Fury / Xbox / PlayStation, macros a 60 Hz |
| `org/libsdl/app/DragonFuryPad.java` | comando virtual: regista um joystick Android no SDL (`nativeAddJoystick`) e manda botões/eixos pelas funções JNI do SDLControllerManager |
| `cpp/game_state_jni.cpp` | lado do P1 (para "trás") a partir das posições dos lutadores; a base da memória vem do hook em `android_hooks.cpp` |

### Decisões

- Pacote `com.dfdx047.dragonfury`, nome **Dragon Fury**, desenvolvedor dfdx047.
- Ícone original: esfera de 3 estrelas com aura e um "3" num selo (`docs/icone-dragon-fury.svg`).
- **Turnip opcional** (muda a decisão da Fase 1): nenhum driver ativo por omissão; num Adreno
  o launcher mostra uma vez um aviso a recomendar o Turnip. Fontes: MrPurple666/purple-turnip,
  StevenMXZ/freedreno_turnip-CI (nome do repositório por confirmar), K11MCH1/AdrenoToolsDrivers.
- Filtros oferecidos: Bilinear, CAS, FSR 1. **FSR 3/DLSS fora**: no Vulkan o FSR 3 do projeto
  passa a cor como profundidade e como vetores de movimento, jitter 0 e reset em todos os
  frames, ou seja, só espacial (era o "não funciona bem"). Confirmar que o build tem
  `REX_HAS_FIDELITYFX_SDK`; sem ele `present_effect` só aceita `bilinear`.
- Proporção: 16:9 original (`present_letterbox = true`) ou esticar. Não há patch de
  widescreen no projeto; tela cheia real em 20:9 pede RE (FOV + HUD).
- Texturas no Vulkan: carrega packs; não há dump nem upscale (só D3D12). A cache de imagens
  descodificadas do runtime tem 2 GB fixos (`kDbz3PackCacheBudget`): baixar num patch Android.
- Cheats: só `dbz3_unlock_all_characters` funciona; cápsulas/transformações/zeni/cenários
  aparecem como "em breve" (pedem RE do save).
- Atualizador: aponta para `dfdx047/DBZ3-Android`; funciona quando o repositório ficar público.

### Controles (Budokai 3, Xbox 360)

- X soco, Y chute, A defesa, B ki. Gatilhos = atalhos do próprio jogo (manual PS2:
  L1 agarrão = soco+defesa, R1 transformação = soco+chute+defesa, L2 Modo Hyper = os 4,
  R2 contra-ataque = soco+chute). Assume-se L1/R1/L2/R2 → LB/RB/LT/RT no HD: **confirmar**.
- Carregar ki = segurar defesa + trás, trás (segurando). Botão "KI": sequência
  A → A+trás (4f) → A (4f) → A+trás segurado até soltar. "Trás" segue o lado do P1.
- Layout padrão (paisagem): analógico à esquerda; soco/chute/ki/defesa em losango à
  direita; KI, Agarrão (LB), Transformar (RB), Hyper (LT), Contra (RT) à volta; Back e Start
  em cima. O D-pad existe mas vem escondido.
- Editor: arrastar, tamanho, opacidade, esconder, adicionar botões do comando, botões do
  port e macros (combinação ou sequência por frames, com "segurar último passo").

### Por testar no aparelho

- [ ] Compila (Kotlin 2.2.10, Compose BOM 2025.09.00, AGP 8.13.0) — corrigir o que falhar
- [ ] O runtime vê o comando virtual (hotplug depois do arranque) e os botões chegam ao jogo
- [ ] Gatilhos como eixos (-1 solto / 1 premido) chegam como LT/RT
- [ ] Mapeamento LB/RB/LT/RT dos atalhos e o botão KI (orientação inicial "P1 à esquerda")
- [ ] `REX_LOG_FILE` cria `logs/dragonfury_last.log` (senão a aba Texturas fica sem log)
- [ ] Ícone, tema e textos pt/en; atalho na tela inicial; orientação invertida/automática
- [ ] Importação SAF de ~2,5 GB e verificação completa
- [ ] Drivers: lista, download e ativação (nome exato do repositório do StevenMXZ)

## Perfil (8 Elite, 2026-10-12, `tools/profile.sh`)

- Main XThread ~40% das amostras do processo: `sub_82235918` 54% e `sub_82231110` 12% dessa thread. Confirmado: é a espera do D3D do 360 por espaço no ring buffer da GPU (`cctpl/db16cyc` viram nada ao recompilar). Hook em `app/src/main/cpp/android_hooks.cpp` (yield e depois sleep de 50 µs).
- Audio Worker ~21%: polling em `WaitMultiple` (corrigido em 0006).
- GPU Commands ~15%: `vkUpdateDescriptorSets` do driver Adreno é o maior custo.

## Véu/verde na imagem (investigação)

- Piora com a câmara longe, melhora perto: suspeita do efeito de profundidade de campo/bloom.
- Sem efeito (2026-10-12): `depth_float24_convert_in_pixel_shader`, `readback_resolve=fast`,
  `gamma_render_target_as_unorm16=false`, `half_pixel_offset=false`.
- Turnip (Gen8 V37, 2026-10-12): véu e bordas verdes **desaparecem**. É bug do driver Qualcomm.
  Comparação dos logs do dispositivo: as mesmas features; o nativo só não tem
  `VK_EXT_non_seamless_cube_map`/`memory_budget`. Testes A/B no nativo (2026-10-12):
  `snorm16_render_target_full_range=false` melhora um pouco mas não resolve;
  `direct_host_resolve=false`, `vulkan_dynamic_rendering=false`, `gpu_3d_to_2d_texture=false`
  pioram. O nativo também tem frame time irregular, GPU no clock máximo e consumo a saltar
  para 9–10 W. **Decisão: Turnip por omissão em Adreno.** Retomar o nativo só com captura
  RenderDoc do efeito de profundidade de campo.

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
  Corrigido em 0011. Compilação async desligada por omissão (GameActivity): sem ela não
  há frames escondidos e os pipelines síncronos custam < 1 ms.
- Resultado do 0011 (2026-10-12, Turnip): travões de 150–420 ms desapareceram; o pior
  frame na partida de teste foi 46 ms (primeira vez de um efeito: 7 shaders, 4 ms).
- Atenção: o `dbz3_user.toml` (settings.cpp do projeto original) sobrepõe cvars do SDK.
  Para compilação síncrona usar `dbz3_async_shaders = false`, não `async_shader_compilation`.