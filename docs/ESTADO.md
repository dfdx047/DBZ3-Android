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
| `interfaces/graphics.h`, `graphics_system.h` (0012) | `Pause()`/`Resume()` virtuais no `IGraphicsSystem`: o libmain pausa a GPU (menus no jogo) sem precisar dos símbolos escondidos do plugin |
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
- Visual (2026-10-12, pedido do usuário): botões de vidro fosco (branco translúcido), ícones
  dourados, sem nomes por baixo; selo no canto com o botão do comando (Xbox ou PlayStation,
  ou nenhum) — ex. "LB", "A◀◀" no botão de ki. Estilos clássicos Xbox/PlayStation mantêm-se.
- Direcional: **D-pad de 8 direções por omissão** (jogo 2D: toque duplo = dash, diagonais
  para voar) ou analógico, escolhido no launcher ou no jogo.
- Engrenagem no canto de cima (no jogo): menu dos controles (direcional, estilo, selo,
  opacidade, vibração, editar layout por cima do jogo, abrir o menu do port). Com o menu
  aberto o jogo **pausa** (`nativeSyncPause`: `GraphicsSystem::Pause` + `AudioSystem::Pause`);
  o menu rápido do port (Back+Start) também pausa, verificado a 60 Hz com
  `QuickMenuDialog::IsOpen()`. Opção "Pausar nos menus" nas Configurações.

### Teste de 2026-10-12 (8 Elite / Odin 3)

- Funciona: launcher, importação, atalho, drivers (download), controles por toque, menu da
  engrenagem. O usuário gostou do visual (vidro fosco + selos).
- O dump USA do usuário (Redump) tem `data_usi.afs` em vez de `data_eng.afs` e outros tamanhos
  de `.afs/.sfd`: a verificação passou a aceitar (aviso amarelo, não bloqueia).
- Pedidos implementados a seguir:
  - controles na tela **Automático / Ligado / Desligado** (automático: tocar na tela mostra,
    usar o comando físico esconde; o Odin tem comando embutido);
  - **dois layouts, luta e menus**, trocados sozinhos pela bandeira de luta `0x823F39A0`
    (histerese de ~6 s a 0 antes de voltar aos menus); editor com os dois;
  - **botão de pause** em cima ao centro: "Opções do jogo" por toque (o que o menu rápido do
    PC muda ao vivo: filtro, nitidez, FXAA, brilho HD, packs, FPS, volume, mudo, vibração),
    aplicado com `nativeSetCvars` → `ApplyRuntimeSettingsToSdk(true)` + `SaveUserSettings()`;
    o menu clássico do PC continua acessível por um botão.
- Próximo (planeado com o usuário):
  - travadinhas de compilação de shaders (mais lisura) — logs no SD865 (Retroid Pocket 5)
    com Turnip e com o driver Qualcomm; depois 8 Elite de novo, 8 Gen 2 e 8 Gen 3;
  - corrigir o véu/verde do driver Qualcomm (pelo menos no 8 Elite);
  - abrir e otimizar no Mali (Unisoc T820 / Mali-G57);
  - ideia: **build Legacy** (APK separado) para GPUs fracas (ex. Mali-G52), com hacks e
    talvez 32-bit, sem estragar a build principal;
  - cheats que faltam (RE do save); testar mods e packs de texturas.

### Teste de 2026-10-12 à noite (Odin 3) e correções

- Layout de menus refeito: só D-pad, A (✕ confirma), Y (△ volta) e Back/Select (informação).
  O LB do layout antigo ficava por cima da engrenagem. Back/Start da luta afastados do pause.
- Troca luta/menus: histerese baixada para 1,5 s; botão para trocar à mão (ao lado da engrenagem).
- **Pausa não parava o jogo** (só o áudio): `GraphicsSystem::Pause` suspende a thread da GPU com
  `Thread::Suspend`, que no Android não parou nada. Agora a thread principal do jogo fica presa
  no hook da espera do ring buffer (`android_hooks.cpp`, `g_dragonfury_pause` + condvar) enquanto
  a pausa estiver pedida; áudio com `AudioSystem::Pause`. `nativeForceResume` ao fechar o jogo.
  (O patch 0012 continua aplicado mas já não é usado.)
- Vibração com comando físico: o SDL só faz rumble em comandos com motor próprio (o embutido do
  Odin/Retroid não tem). O comando virtual passa a anunciar rumble e fica sempre ligado;
  `DragonFuryHaptics` manda esse rumble para o vibrador do aparelho. Por confirmar que o runtime
  envia o rumble do jogador 1 também para o comando virtual.
- Botão **Exportar logs** (Sobre e Configurações): zip em `Download/` com logcat da app, log da
  sessão, `dbz3_user.toml`, `cvars.txt`, driver ativo e propriedades gráficas. Opção "Log
  detalhado" (`log_level = "debug"`).
- **Tag Team não aparece**: o CMake do Android compila só as fontes antigas do projeto original
  (sem `tagteam.cpp`, `tagteam_slots.cpp`, `tagteam_panel.cpp`, `net/*`, `campaigns.cpp`...).
  Adicionado `-Pdf.fullSources=true` (opção `DBZ3_FULL_SOURCES`, desligada por omissão) que as
  compila se existirem em `external/dbz3`. Pode pedir um `external/dbz3` mais recente, novo
  codegen do `generated/` e reaplicar o overlay ao SDK: validar com o PC ligado.
- Pedidos para depois: mais proporções de tela e patch de widescreen (RE da câmara/HUD), cheats.
- Depuração do driver Qualcomm: `tools/gpu-ab.sh <nome> [cvar=valor ...]` (A/B com captura de
  ecrã e logcat em `perf/gpu/<nome>/`).

### Por testar no aparelho

- [ ] Compila (Kotlin 2.2.10, Compose BOM 2025.09.00, AGP 8.13.0) — corrigir o que falhar
- [ ] O runtime vê o comando virtual (hotplug depois do arranque) e os botões chegam ao jogo
- [ ] Gatilhos como eixos (-1 solto / 1 premido) chegam como LT/RT
- [ ] Mapeamento LB/RB/LT/RT dos atalhos e o botão KI (orientação inicial "P1 à esquerda")
- [ ] `REX_LOG_FILE` cria `logs/dragonfury_last.log` (senão a aba Texturas fica sem log)
- [ ] Ícone, tema e textos pt/en; atalho na tela inicial; orientação invertida/automática
- [ ] Importação SAF de ~2,5 GB e verificação completa
- [ ] Drivers: lista, download e ativação (nome exato do repositório do StevenMXZ)
- [ ] Pausa: o jogo congela e volta sem travar (áudio incluído); o menu rápido do port
  continua a responder com a GPU pausada
- [ ] Menu da engrenagem e editor por cima do jogo (ComposeView num SDLActivity)

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
### Driver Qualcomm novo (8 Elite Gen 6, build 8a1dcb7e40, 0891.7.1x) — logs de 2026-10-12 21:15
- Corrige o véu e o verde (o bug era mesmo do driver antigo). Mais travadinhas e picos de
  clock do que o Turnip.
- O log estava em `debug`: 222 mil linhas `XMA: Write to unknown register (0601)` e uma
  `XELOG_GPU PRESENT` por frame em ~6 min (25 MB). Passaram a `trace` no 0013.
  **Testes de desempenho sempre com o log detalhado desligado.**
- O lado do jogo está estável: 18 393 frames, mediana 17 ms, só 41 acima de 20 ms e 3 acima
  de 33 ms; `tiron` só 4 vezes (34–56 ms), uma delas um pipeline novo (51 ms). Ou seja, a
  travadinha que se vê **não é** compilação de shaders nem o jogo atrasar.
- Swapchain em FIFO (modo 2) a 60 FPS gerados por um relógio próprio, não preso ao ecrã.
  Num ecrã a 120 Hz, 1 ms de oscilação faz frames ficarem 8 ou 25 ms na tela (judder).
  O `preferredRefreshRate = 60` era só uma sugestão: agora o GameActivity pede o modo de
  60 Hz pelo id, marca a superfície com `Surface.setFrameRate(60, FIXED_SOURCE)` e escreve no
  logcat `Ecra: ...` e `Ecra durante o jogo: X Hz` para confirmar.
- O que sobra no driver Qualcomm é o DCVS (governador de clock da GPU): o Turnip usa o
  governador do kernel, o driver da Qualcomm o seu próprio. Próximo A/B: `dbz3_native_2x_msaa`
  e `dbz3_present_dither` desligados, com o modo de 60 Hz confirmado.

### Testes de 2026-10-12 21:43–21:59 (Odin 3, Turnip vs Qualcomm novo, 0.2.0 com 0013)
- Ecrã confirmado: `Ecra: atual 120.00 Hz ... Ecra durante o jogo: 60.00 Hz`.
- Pipelines novos: o driver Qualcomm compila ~2x mais devagar que o Turnip no mesmo
  momento (10 pipelines 155 ms vs 322 ms; 16 pipelines 101 vs 222 ms). Na sessão seguinte
  os mesmos 16 custaram 3,7 ms: a cache do driver resolve depois da primeira vez (é por
  driver; trocar de driver recomeça).
- `tiron` de 1,5–20 s na terceira sessão = jogo pausado no menu, não travões.
- **FSR/CAS nunca foram aplicados no Android**: sem o SDK da AMD o runtime só aceitava
  `present_effect = bilinear` ("not in allowed values"). 0015 liga o FSR 1/CAS (shaders
  próprios). O "modo FSR" só muda a resolução interna com escala 2x ou mais.
- A carga da GPU (%) não desce com opções mais leves porque o governador baixa o clock
  para a manter alta. Travar a GPU a 900 MHz (abaixo do máximo de 1100) suaviza: indica
  o governador a oscilar, não falta de GPU. O app não controla o clock sem root.
- 0016: `gpu_ms` por frame (timestamps Vulkan) na linha `dbz3: perf` e
  `logs/gpu_stats.log` (clock/carga/temperatura do KGSL a cada 5 s), com
  "Diagnóstico de desempenho" ligado nas Configurações.
- Controles na tela com comando físico ligado não funcionavam: o comando virtual era o
  jogador 2 (`SlotAssignment`). 0014 marca-o como sintético (sempre jogador 1, junto com
  o físico; a vibração também chega aos dois).
- Back do Odin (KEYCODE_BACK) com o jogo a correr abre o menu da engrenagem.
- `cvars.txt` aceita `env:NOME = valor` para variáveis do driver (ex. `env:TU_DEBUG = sysmem`).
- As partes rodadas antigas do log (`dragonfury_last.N.log`) são apagadas ao abrir o jogo.

### Testes de 2026-10-12 22:24–22:35 (Turnip e Qualcomm, FSR 1x, GPU a 900 MHz e livre)
- `logs/gpu_stats.log` funciona (o SELinux regista avc denied mas as leituras passam).
- A linha `dbz3: perf` nunca apareceu: a cvar `dbz3_perf_logging` só existia no presenter
  D3D12 (Windows). 0017 define-a fora do Windows.
- Carga da GPU a 900 MHz fixos, igual nos dois drivers: menus 36–39 %, lutas 75–93 %
  (picos 97 %). Livre: o governador salta entre 900 e 1100 MHz (4–14 trocas a cada 5 s)
  com ~77 %. Ou seja, uma luta a 720p gasta ~3/4 de uma Adreno 830: o custo é do
  emulador (EDRAM, transferências, resolves), não do jogo. Os frames ficam perto dos
  16,7 ms e qualquer descida de clock faz falhar o vsync (as travadinhas).
- 0017: perfil da GPU por categoria (timestamps fora dos render passes): desenho,
  transferências de RT, resolves, texturas, swap e outros, com passes e draws por frame.
- Troca luta/menus: a revanche às vezes fica com o layout de menus. Diagnóstico em
  `logs/controles.log` (bandeira 0x823F39A0, ponteiro 0x823D6CF4 e posições, só com o
  diagnóstico ligado) para ver o que a bandeira faz numa revanche.

### Teste de 2026-10-12 22:55 (Turnip, 900 MHz, FSR 1x) — primeiro perfil por categoria
- Luta (por frame, a 900 MHz): ~13 ms de GPU = desenho 3,9 ms (60 passes, ~400 draws)
  + texturas 4,0 + resolves 3,0 + transferências de RT 1,6 + outros 0,3. Mais de metade
  é cópia de emulação (EDRAM -> memória -> textura), não desenho. Na luta quase nenhum
  frame passa de 16,7 ms com 900 MHz fixos.
- Vídeo de abertura/menus (6–70 draws): 10–15 ms medidos com muitos frames > 16,7 ms, mas
  o KGSL diz só ~36 % de carga. Por confirmar (espera dentro da submissão?).
- A bandeira 0x823F39A0 ficou a 0 a sessão toda, lutas incluídas: era por isso que o
  layout não trocava e o "carregar ki" não seguia o lado (o seguimento do lado só corria
  com a bandeira a 1). Agora a luta é detetada pelo ponteiro 0x823D6CF4 + posições dos
  lutadores ≠ (0,0); entre assaltos as posições voltam a (0,0) e cada assalto recomeça
  com o P1 à esquerda (x = -15).
- 0018: contagem de resolves e cargas de texturas por frame (tamanhos, formatos e cargas
  repetidas da mesma textura no mesmo frame) na linha perf.

### Teste de 2026-10-12 23:15 (Turnip + TU_DEBUG=sysmem, clock livre) — resolves e texturas
- Troca luta/menus pelas posições: funciona (log `troca -> luta/menus` nos sítios certos).
- Por frame numa luta: ~20 resolves (10 de 1280x720, 5 de 320x184, 2 de 80x48, 2 de 512x512
  ou 160x96) e ~20 cargas de texturas, quase todas k_8_8_8_8 (f6) dos mesmos tamanhos, com
  ~6 recargas da mesma textura no mesmo frame. O jogo copia o ecrã inteiro ~10 vezes por
  frame para os efeitos.
- No Vulkan cada resolve é: dump do render target para o buffer EDRAM + cópia EDRAM ->
  memória do guest (o "direct resolve" é um stub: `TryResolveCopyDirectly` acaba sempre em
  `DumpRenderTargets`) e depois a carga da textura volta a ler a memória e a desfazer o
  tiling. ~6,6 ms de ~11,5 ms. Alvo seguinte: atalho render target -> textura para os
  resolves de ecrã inteiro (e/ou o direct resolve real), atrás de uma cvar para A/B.
- TU_DEBUG=sysmem: sem ganho (desenho 3,4 ms a ~1050 MHz ≈ 3,9 ms a 900 MHz).
- Carregar ki ainda falha quando a câmara troca de lado (não é só cruzar os lutadores).
  `logs/lutadores.bin` (diagnóstico) grava os blocos dos lutadores a cada 0,5 s e em cada
  botão de macro, para encontrar o campo de "lado"/espelho por diferença.

### Teste de 2026-10-12 23:31 — lado do P1 e sair para o launcher
- `lutadores.bin`: no bloco da batalha (ponteiro 0x823D6CF4) a câmara está em +400 (posição
  vec4) e +416 (alvo vec4); +160/+176 e o campo s16 em lutador+6054/+6066 são o yaw de cada
  lutador (0x4000 = 90°). No início: câmara (0,13,50) -> alvo (0,13,0), P1 em x=-15 à
  esquerda. Lado no ecrã = sinal de dot(P2-P1, (-fz, fx)) com f = alvo - câmara. Bate com o
  teste: 6 toques seguidos no "carregar ki" quando a câmara já tinha trocado (23:31:18) e o
  app ainda mandava "trás = esquerda". `nativeP1Side` usa agora a câmara (margem de 0,15).
- O jogo corre no processo ":game"; "Sair para o launcher" (dois toques) termina esse
  processo e abre o launcher. LauncherPrefs com MODE_MULTI_PROCESS para não gravar por cima
  do que se mudou no menu dentro do jogo.
- 0019: descreve uma vez cada resolve grande (origem, destino, formato, endian, troca R/B) e
  cada textura grande carregada, para escrever o atalho render target -> textura.

### 2026-10-13 — atalho render target -> textura (0020)
- Log de 23:59 (0019): os resolves de ecrã inteiro são cor k_8_8_8_8 do RT na base 0
  (pitch 16 tiles), sem MSAA, destino k_8_8_8_8 tiled pitch 1280, endian 2 (às vezes 0),
  `copy_dest_swap=1`; a textura carregada a seguir tem a mesma base, 1280x720 f6, tiled,
  pitch 40, mesmo endian, sem mips. Também há resolves de profundidade (src_select=4,
  dest_fmt 22) que ficam no caminho normal.
- 0020: depois do resolve normal (a memória do guest continua certa), se a textura desse
  destino já existir, um compute lê o RT (vista de transferência, uint ou unorm), troca R/B
  se o resolve pede, escreve num buffer linear e copia para a imagem; a textura fica
  marcada como carregada e a carga (untile) seguinte não acontece. Só com um único RT 8888
  1x na base certa, retângulo em (0,0), destino linear = pitch e escala 1x.
- `dbz3_direct_resolve_texture`: 0 desligado, 1 ligado (o launcher põe 1), 2 ligado com R/B
  invertido (teste da ordem dos canais). A linha perf mostra `atalho=N/f`.
- Carregar ki pelo lado da câmara: confirmado a funcionar (teste do utilizador).

### Testes de 2026-10-13 00:18–00:31 (atalho 0020)
- O atalho funciona: ~9 de ~10 resolves de ecrã inteiro por frame passam por ele e as
  cargas de texturas caem de ~20 para ~12 por frame (texturas 3,7 -> 1,4 ms no Turnip).
  Mas o atalho custa ~1,7 ms (compute + cópia buffer->imagem) e o resolve normal continua:
  ganho líquido pequeno (Turnip 11,5 -> 11,0 ms por frame de luta).
- Driver Qualcomm: desenho 6,4 ms contra 3,4 no Turnip (resolve/texturas mais baratos, mas
  no total 12,3 ms). Turnip continua melhor.
- Frames com GPU > 16,7 ms: ~2 a cada 5 s; frames do jogo > 25 ms: quase nenhum.
- 0021: modo rápido (`dbz3_direct_resolve_skip_memory`, launcher liga por omissão): quando
  a textura já existe, salta o dump e o resolve para a memória do guest e só preenche a
  textura (a memória fica com o conteúdo antigo). A linha perf mostra `sem_memoria=N/f`.
- Por confirmar: cores com o atalho em 1 (normal) vs 2 (R/B trocado).
