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
| 0002-settings-no-char8t | `PathUtf8` com `auto`: o build Android usa `-fno-char8_t` e aí `std::u8string` não existe |
| 0003-online-android-identity | admissão do ONLINE: no Android a identidade é o hash de `libmain.so` + `librexruntime.so` + `librexgpu-xenos.so` (`REX_NATIVE_LIB_DIR`), não o `/proc/self/exe` (= `app_process` do sistema). Só joga contra o mesmo APK |

Os patches do jogo são escritos contra o projeto original **v1.4.5.1** (origin/master,
47ada97). O `apply-sdk-patches.sh` e o CMake recusam um `external/dbz3` sem `src/stage_ext.cpp`.

### Atualizar o external/dbz3 (v1.4.5.1)

O codegen não mudou entre 41f03b2 (v1.4.4.5) e 47ada97 (`dbz3_config.toml` e
`dbz3_manifest.toml` iguais): o `generated/` de uma cópia v1.4.4.5 serve. Se a cópia for mais
antiga e esses dois ficheiros mudarem, é preciso correr o codegen outra vez.

```fish
cd ~/AndroidStudioProjects/DBZ3-Android
md5sum external/dbz3/dbz3_config.toml external/dbz3/dbz3_manifest.toml
# cópia git: descarta os patches Android (voltam a ser aplicados) e salta para origin/master
git -C external/dbz3 fetch origin
git -C external/dbz3 reset --hard origin/master   # o generated/ não é seguido pelo git: fica
rm -f external/dbz3/.dbz3-android-patches
md5sum external/dbz3/dbz3_config.toml external/dbz3/dbz3_manifest.toml   # iguais? então não há codegen
bash tools/sync-upstream-overlay.sh   # ficheiros novos do overlay que não chocam com patches Android
bash tools/apply-sdk-patches.sh
```

Sem cópia git: clonar o original para outra pasta, mover o `generated/` antigo para lá e
substituir `external/dbz3`. Nunca usar `git clean` no `external/dbz3` (apagava o `generated/`).
O `sync-upstream-overlay.sh` não copia a cache de texturas nova (pack HD em segundo plano da
v1.4.5) nem o driver SDL novo: chocam com os patches 0010–0044 e 0014 e ficam por portar.

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
  **2026-10-14**: o CMake compila sempre a lista completa da v1.4.5.1 (Tag Team, `src/net/*`,
  campanhas, `stage_ext.cpp`, `novas_mods.cpp`, ecrãs do jogo); `-Pdf.fullSources` ficou sem
  efeito. Pede o `external/dbz3` em v1.4.5.1 (ver "Atualizar o external/dbz3") e os patches
  de jogo 0002/0003. Os ecrãs TAG TEAM (menu principal) e ONLINE (menu Duelo / SELECT) são
  do próprio jogo (ImGui), não do launcher. Online: UDP lockstep, sockets POSIX (já existiam no
  código original), sem UPnP fora do Windows (fica STUN + código de sala; em dados móveis/CGNAT
  o anfitrião quase nunca é alcançável: usar Wi-Fi/rede local ou porta reencaminhada).
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

### 2026-10-13 00:45 — modo rápido no driver Qualcomm
- Cores: o atalho em 1 está certo; 2 (R/B trocado) inverte as cores. O launcher passa a ter
  só ligado/desligado (o 2 fica na cvar para testes).
- Qualcomm com modo rápido: resolve 3,4 -> 2,3 ms, total 12,3 -> 11,5 ms por frame de luta;
  `sem_memoria` ~9/f. O desenho continua 6,6 ms (Turnip 3,5). Falta o log do Turnip com o
  modo rápido (o enviado era o antigo, 00:31).
- Próximo ganho possível no atalho: escrever direto na textura (imageStore, textura com
  STORAGE) em vez de buffer + cópia (~0,8 ms). Antes disso: medir 865 e Mali.

### 2026-10-13 01:21 — Turnip com modo rápido
- Luta: 10,1 ms de GPU por frame (era 11,5 sem atalho): desenho 3,8 (60 passes), resolve
  2,9 (agora é quase só o custo do próprio atalho: 9 × compute + cópia buffer->imagem),
  transfer 1,6 (24 passes), texturas 1,3. Carga do KGSL 64–74 % a 900 MHz (era 77–93 %),
  menos trocas de clock. Ainda 1–6 frames > 16,7 ms a cada 5 s (máx. 20–28 ms).
- 0022: a linha perf mostra as categorias do pior frame da janela (`pior: ...`), para ver o
  que rebenta nesses frames.
- Próximos ganhos no Odin: atalho a escrever direto na imagem (storage image, ~1 ms);
  o resto (desenho + transferências ≈ 5,4 ms, 84 passes de tiler por frame) é do método de
  emulação do EDRAM e não se corta sem outro caminho de render targets.

### 2026-10-13 02:10 — Retroid Pocket 5 (SD865, Adreno 650)
- Driver do sistema (Qualcomm 0x802EA000, Vulkan 1.1): entra no jogo, mas com os defeitos do
  driver Qualcomm antigo e ~22 FPS na luta: 44 ms de GPU por frame = desenho 13,9 + transfer
  14,5 + resolve 8,8 + texturas 6,8 (atalho desligado nesse teste). Picos de 100–900 ms.
- Turnip PurpleVK T30 (Vulkan 1.4): imagem com defeitos (não chegou à luta no log).
- O limite de 30 FPS só atrasava a apresentação: o jogo continuava a desenhar 60 frames/s.
  0023: `dbz3_frameskip` — o jogo corre a 60 mas só 1 em cada N+1 frames é desenhado (draws,
  resolves e queries de oclusão ignorados nos outros, sem apresentação). O launcher põe 1
  quando o limite é 30.

### 2026-10-13 07:18 — RP5, driver do sistema, atalho + modo rápido + 30 FPS
- Jogo a ~50 fps (só 25 desenhados), GPU 99% a 670 MHz, ~39 ms de GPU por frame desenhado:
  outros 13, desenho 11, transfer 8–9, resolve 4, texturas 2. Melhor que sem atalho (44 fps), mas
  o 865 no driver da Qualcomm não chega aos 30 desenhados. Precisa do Turnip.

### 2026-10-13 — RB2-Android (port do Raging Blast 2, mesmo SDK v0.10, BSD-3) e patch 0024
O RB2 tem 37 patches de desempenho em cima do mesmo SDK. Os que entraram já (patch 0024):
- **Presenter recompilava o pipeline do efeito final em cada frame** (`swapchain_format` nunca
  era guardado): esperava pela GPU, destruía e recriava o pipeline (FSR/CAS) a cada frame. No
  RB2 eram 84% da thread da UI no Adreno. Corrigido (RB2 0002).
- **Armazenamento de shaders/pipelines nunca era lido no Android** (`fopen("a+")` começa a ler
  no fim na libc do Android): pipelines compilados a meio do jogo em cada arranque. Seek para o
  início antes de ler (RB2 0009, só a correção).
- Interruptores de diagnóstico: `vulkan_disable_depth_unorm24` (RB2 0004), `vulkan_mask_features`
  (RB2 0036, inclui `textureCompressionBC` = texturas BC descomprimidas como no driver da
  Qualcomm), e o nosso `dbz3_vk_disable_extensions` (lista de extensões a não ativar).
- `-march=armv8.2-a` também no SDK (atómicos LSE, CMakeLists da app).

Turnip nos Adreno 6xx: diferenças em relação ao driver da Qualcomm no RP5 — o Turnip tem
dynamic rendering, `VK_EXT_shader_stencil_export`, texturas BC nativas, GMEM/LRZ/UBWC próprios.
Launcher, aba Drivers, "Correções do Turnip" (pref `turnip_compat`, aplicado no GameActivity
antes do cvars.txt): 1 sysmem, 2 nolrz+noubwc, 3 caminhos simples (sem dynamic rendering, sem
stencil export, BC descomprimido, depth float32), 4 tudo, 5 flushall+syncdraw (diagnóstico de
barreiras). Testar 4 primeiro; se corrigir, bissetar com 1/2/3.

Próximos do RB2 a portar (maiores): resolve fundido (0021/0022: compute lê o render target e
escreve direto na memória do guest, sem dump do EDRAM), hoisting de uploads/cargas de texturas
para fora dos passes (0003/0007/0020/0026: menos quebras de pass no tiler, ataca os "outros"),
LOD implícito nos shaders (0018), páginas voláteis (0013/0028/0032), afinidade de threads/ADPF.
O RB2 usa Turnip v26.0.0-rc08 R8 (K11MCH1) nos 6xx/7xx.

### 2026-10-13 07:41–07:48 — RP5 com Turnip (R5, R6, PurpleVK T30): modos 1–5 sem efeito
- Tudo com glitches em todos os modos, incluindo sysmem, nolrz/noubwc, flushall/syncdraw e
  caminhos simples: não é GMEM, LRZ, UBWC nem falta de barreiras. O jogo corre (texturas
  carregam, resolves normais), a imagem é que sai errada.
- Diferença entre o Turnip do RP5 (Adreno 650) e o do Odin (830, funciona): só
  `maxStorageBufferRange` (128 MB contra 2 GB) e sparse binding. Com 128 MB a memória do guest
  (512 MB) fica partida em 4 storage buffers e cada acesso dos shaders (vertex fetch, memexport)
  passa por um switch; no Odin é um só buffer. Patch 0025: `dbz3_vk_max_storage_buffer_range_mb`
  força o valor; modo 6 das correções do Turnip = 512 (um só buffer, fora da especificação, mas o
  hardware A6xx deve aguentar).

### 2026-10-13 07:58 — RP5, Turnip R6, memória num só bloco (modo 6)
- Funciona sem crash, a 60 fps com 12–15 ms de GPU por frame (no driver da Qualcomm eram ~39).
- Nas capturas a geometria está certa (cenário e lutadores no sítio); o que está errado são as
  texturas e as cores: paredes com a textura da relva, prédios em grelha de pontos, lutadores
  pretos, faixas pretas/amarelas, e no menu retângulos com riscas horizontais (cara de
  resolve/textura lida com o tiling errado). A correção da memória fica.
- As correções do Turnip passaram a interruptores independentes (pref `turnip_compat_flags`):
  1 um só bloco, 2 sysmem, 4 nolrz/noubwc, 8 BC descomprimido, 16 sem dynamic rendering/stencil
  export, 32 depth float32, 64 flushall/syncdraw. Próximo teste: 1 + atalho de efeitos desligado;
  1 + BC descomprimido; 1 + o resto.

### 2026-10-13 — Skate3-Mobile (Buku313) e patch 0026
- O Skate 3 não usa o caminho de GPU do Xenia em jogo: hooks nas funções de render do jogo leem
  meshes/texturas/constantes e um renderizador Vulkan próprio desenha a cena (~25k linhas
  específicas do jogo); o caminho emulado fica desligado. Por isso corre num SD695. Para o DBZ
  seria um projeto à parte (engenharia reversa do motor do Budokai 3).
- Portável já: constantes do sistema dos shaders fora do std140. O ReXGlue pôs
  `user_clip_planes` (vec4[6]) no offset 28 e `texture_swizzled_signs`, `color_exp_bias` e as
  constantes do EDRAM também desalinhados; só é válido com scalarBlockLayout, que o driver da
  Qualcomm do RP5 (Vulkan 1.1) não tem — o Skate3 crashava no compilador desse driver. Patch
  0026: preenchimento para tudo ficar nos alinhamentos do std140 (verificado com offsetof).
  Pode explicar parte dos defeitos do driver nativo no RP5 (cores, sinais das texturas).
- Também a considerar: afinidade das threads (CP/GPU nos núcleos grandes, XMA/ficheiros nos
  pequenos), ThinLTO, `vulkan_spirv_optimize` (SPIRV-Tools antes do driver).

### 2026-10-13 13:29 — primeira captura de frames (RP5, luta, driver do sistema) e patch 0028
Captura 20261013-132922.dfc: 2 frames, ~400 draws e 33 shaders por frame. Estrutura de um
frame de luta (`tools/dfc_parse.py`):
1. 2 draws a 4x MSAA pitch 640 (restauro da profundidade do frame anterior a partir da
   textura 24_8 em 1EC2F000, zfunc always).
2. Cena do lutador 1: 137 draws a 1280x720 (cenário + lutador), depois **resolve de
   profundidade** → 1EC2F000, **cópia da cor** → 1F35F000, passe de **512x512** (pitch 560,
   21 draws: silhueta do lutador para o contorno/aura) → 1E2D3000, 1 draw que desenha 1F35F000
   de volta (restaura a cor) e 2 draws que escrevem 1EC2F000 em color@1328 (restauram a
   profundidade na zona do depth buffer), 69 draws do lutador 2 com o mesmo ciclo, 28 draws
   de efeitos.
3. Brilho: 1280x720 → 320x184 → 160x96 → 80x48 (2 draws por nível) e composição.
4. HUD: 77 draws e resolve com limpeza → 1F35F000; 2 draws finais → frontbuffer 1F6F8000.
Conclusão: o jogo é simples na GPU (33 shaders, passes limpos) e o custo todo vinha da
fidelidade ao EDRAM (transferências entre configurações, dump + resolve). O caminho novo é
"render targets diretos", não um renderizador por engenharia reversa do motor:
- Patch 0028 `dbz3_rt_direct`: cada configuração de render target guarda a sua imagem e nunca
  há transferências entre elas (o jogo restaura cor e profundidade ele próprio); as limpezas
  por resolve aplicam-se também às imagens irmãs com a mesma base; resolves de profundidade
  vão direto do depth buffer para as texturas k_24_8 (R32F) e k_8_8_8_8 ([stencil, d_lo,
  d_mid, d_hi]) do destino, sem dump; atalho de cor passa a aceitar resolves pequenos (brilho).
- `vulkan_sparse_shared_memory = false` no toml (como RB2 e Skate3).
- Launcher: interruptor "Render targets diretos" (ligado por omissão).
Esperado no RP5 com o driver da Qualcomm: transfer (8–9 ms) e resolve (4 ms) quase a zero;
"outros" (13 ms) deve cair com o fim dos dumps. Risco: partes da imagem a faltar se algum
passe dependesse do conteúdo transferido (desligar o interruptor confirma).

### 2026-10-13 13:46 — render targets diretos: ecrã preto (patch 0029)
Sem nenhuma transferência não há imagem, nem no vídeo de abertura. Causa: o Budokai 3 limpa
cor e profundidade com 2 draws de ecrã inteiro a **4x MSAA e pitch 640** (mesmos tiles do
EDRAM que 1x a 1280); sem a transferência 4x→1x a imagem de 1280 nunca é inicializada e o
depth buffer fica com lixo. 0029: no modo direto ficam só as transferências entre chaves com a
mesma base, pitch em tiles, formato e profundidade/cor, mas MSAA diferente (a "limpeza");
caem as outras, incluindo as restaurações profundidade↔cor de ecrã inteiro (2 por lutador),
que eram o grosso dos 8–9 ms. Nota: "Created 83 graphics pipelines from Vulkan storage" —
a cache de pipelines passou a ser lida no arranque (patch 0024).

### 2026-10-13 14:09 — RP5, driver do sistema, render targets diretos (0029): imagem OK
- GPU 27 ms por frame desenhado (eram 39): outros 13 → 0.3, transfer 8–9 → 1.5. Sobram
  **desenho 14.5 ms** (33 passes, ~420 draws), **resolve 8 ms** e texturas 2.4.
- O atalho de profundidade nunca se aplicou: `GetResolveInfo` troca o formato de destino dos
  resolves de profundidade para k_24_8 (22) e eu só aceitava k_8_8_8_8. Patch 0031 corrige e
  regista o motivo quando o atalho não se aplica. Esperado: resolve 8 → ~2 ms e menos uma
  carga de 1280x720 por lutador.
- Ainda a 30 fps (frameskip) o jogo "fica ruim": a lógica corre a 60 mas só metade é desenhada;
  o alvo tem de ser 60 desenhados. Caminho: vértices reais + shaders do jogo reescritos à mão
  (ver PLANO_RENDER_NATIVO.md).

### 2026-10-13 19:47 — RP5, driver do sistema, 0031 (atalho de profundidade a funcionar)
- Luta: GPU ~24–27 ms por frame desenhado: desenho 13–15 ms (26–35 passes), resolve 8–9 ms,
  transfer 1.6, texturas 2.4 → 0.2 (o atalho de profundidade evita as cargas). Menus: 9–13 ms.
- Mapa de leitores dos resolves (captura de luta): por lutador o jogo copia profundidade
  (→1EC2F000) e cor (→1F35F000) e volta a desenhá-las por cima do ecrã (draw de restauro da
  cor + 2 draws que reescrevem a profundidade via cor em color@1328) depois do passe de
  512x512. No modo direto esse ecrã não é tocado entretanto: restauro e cópia são inúteis.
- Patch 0032 `dbz3_rt_elide_restores` (ligado com os render targets diretos):
  - draws de restauro (ecrã inteiro, opacos, ≤6 índices, que leem uma textura resolvida neste
    frame do render target onde desenham, sem escritas nele desde o resolve) são ignorados;
  - resolves que no frame anterior só tiveram restauros como leitores e foram sobrescritos por
    outro resolve para o mesmo destino no mesmo frame não são feitos; se alguém ler a textura
    a sério, esse resolve volta a ser feito para sempre (no máximo 1 frame com textura velha).
  - Esperado na luta: −4 resolves de ecrã inteiro e −6 draws/−2 passes de ecrã inteiro por
    frame. Log a cada 600 frames: "rt_direct: N resolves eliminados, M restauros ignorados".

### 2026-10-13 19:59 — RP5, driver do sistema, 60 FPS sem frameskip, 0032
- ~44 fps em luta (era ~37 a 60 sem frameskip), GPU 22.4 ms: desenho 11.8 ms em **54 passes**,
  resolve 8 ms, transfer 1.7. Eliminação: ~2 resolves e ~10 "restauros" ignorados por frame.
- **Bug**: nos especiais (Kamehameha, transformação, modo vermelho) o efeito de ecrã inteiro
  ficava preto. Causa: os efeitos também desenham uma textura do ecrã por cima dele, opacos,
  mas transformada; a deteção tratava-os como restauros (e a cópia deixava de ser feita).
- Patch 0033:
  - restauro só com os 2 pixel shaders de cópia do jogo (`dbz3_rt_restore_shaders`,
    6E57A6E305D5D69B cor e ADCA7FBBCBF53B57 profundidade, vistos na captura); leituras a sério
    proíbem a eliminação desse resolve durante 120 frames (600 se já tinha sido eliminado).
  - **Uploads antes do passe** (`dbz3_hoist_uploads`, do RB2 0003): os uploads da memória
    partilhada que o passe ainda não leu são gravados antes do início do passe, em vez de o
    terminar. O RB2 mediu ~84% dos passes de um frame a nascer disto; aqui 54 passes por frame
    para ~25 mudanças reais de render target.
  - Estatística de fins de passe por causa (com diagnóstico ligado, a cada 600 frames:
    "dbz3: fins de passe N/f | causa X/f ...").

### 2026-10-13 20:17 — 0033 no RP5: sem ganho, especiais continuam pretos (patch 0034)
- Fins de passe por frame: 54 → ~34 (desenho 26 passes): os uploads deixaram de partir passes
  ("barreira de buffer (upload)" 0/f). Sobram "barreira de imagem" ~24/f e mudanças de render
  target ~7/f. Mas o tempo de GPU ficou igual (~23 ms, ~42 fps): o custo não estava nos
  passes partidos.
- Os especiais continuam pretos: o pixel shader de cópia 6E57A6E305D5D69B é genérico (também
  desenha os quads do brilho e, provavelmente, os efeitos dos especiais).
- Patch 0034: `dbz3_rt_elide_restores` e `dbz3_hoist_uploads` passam a desligados por omissão
  (sem ganho medido; a eliminação de restauros parte os especiais). Novo: **tempo de GPU por
  draw** durante uma captura (cada draw no seu passe, timestamp antes de cada um); resumo por
  pixel shader no log ("dbz3: perfil por draw") e detalhe em `<captura>.timing.txt` (vai no
  "Exportar logs"). Serve para saber que shaders/draws pesam nos 12 ms de desenho.

### 2026-10-13 20:38 — tempo por draw no RP5 e patch 0035 (shaders especializados)
- Com 0034 (eliminação de restauros desligada): ~50–57 fps em luta normal (GPU 17–19 ms),
  ~38 fps com especiais/efeitos de ecrã (GPU ~26 ms: desenho 14 ms, resolve 9.6 ms em 21
  resolves/frame, 10 deles 1280x720; atalho só em 6–12).
- Tempo por draw (captura): os mais caros são passes de ecrã inteiro com muitas leituras de
  textura: PS 4DFCBC49057E5721 (desfoque de 9 amostras dependente da profundidade, ~3 ms),
  PS F5F4D761A981B87D (FXAA, 9 amostras, ~2.5 ms), PS 555EBAAEF8F61839 (13 amostras).
  O jogo inteiro usa só ~33 shaders.
- Causa: cada `tfetch` traduzido pelo Xenia lê os sinais da textura das constantes do sistema
  e decide em tempo de execução, por pixel e por componente: `switch` unsigned / signed /
  biased / gamma (com a conversão gamma por partes), duas amostragens condicionais (unsigned e
  signed) e gradientes explícitos (`OpDPdxCoarse` + `Grad`, que em Adreno e Mali corre a uma
  fração da taxa normal). Medido no PC (tradutor compilado à parte + spirv-opt a simular a
  especialização): PS 4DFC 1329 → 450 instruções, 20 → 10 amostragens, 52 → 4 ramos;
  F5F4 1405 → 575; 555E 1764 → 574; shaders de personagem 405 → 258.
- Patch 0035:
  - `dbz3_spec_texture_signs` (ligado): os 8 bits de sinais de cada fetch constant passam a
    specialization constant (SpecId 1000 + índice); o `PipelineDescription` guarda os sinais
    das texturas usadas (versão do armazenamento de pipelines sobe: o primeiro arranque volta
    a compilar os pipelines).
  - `dbz3_tex_implicit_lod` (ligado): `tfetch2D` nos pixel shaders com LOD implícito + Bias.
  - Opção no launcher: "Shaders especializados" (liga/desliga os dois).
  - Diagnóstico do atalho resolve→textura de cor: motivo por destino ("atalho resolve->textura
    nao aplicado (motivo N) dest=… + texturas existentes nesse endereço"), para saber porque é
    que só 6–12 dos 15–21 resolves usam o atalho.

### 2026-10-13 21:20 — 0035 no RP5 (último teste do caminho emulado): veredito, render nativo
- Shaders especializados ativos ("sinais de textura como spec constants: true, LOD implicito:
  true"), sem erros de Vulkan.
- Luta: **45–52 fps**, GPU 19–22 ms por frame (antes 25–26 ms nas mesmas cenas). O **desenho
  caiu de ~14 para ~9 ms** (o 0035 funcionou), mas os **resolves continuam em 8–10 ms** (17–21 por
  frame) e o GPU está a 98–99% a 670 MHz. Só cenas leves chegam a 60 (GPU ~14–15 ms).
- Diagnóstico do atalho de cor: as primeiras ocorrências de cada destino falham por não existir
  ainda textura (motivo 6, normal no 1.º frame); os 80x48 por pitch 96 ≠ largura 80 (motivo 3);
  alguns 1280x720/320x184 vêm de um render target 4x MSAA (motivo 5).
- **Veredito:** o caminho emulado não chega a 60 fps no 865 com o driver do sistema.
  Segue-se o renderizador nativo ao nível D3D (ver `PLANO_RENDER_NATIVO.md` e
  `GUIA_PORT_XBOX360_ANDROID.md`). Resumo para continuar numa conversa nova:
  `docs/RESUMO_CONTINUACAO.md`.

### 2026-10-13 22:00 — início do render nativo: ferramentas do mapa D3D
- `tools/d3d_map.py`: mapa estático das funções da biblioteca D3D do XDK a partir do
  `generated/` (PM4 escritos, registos, imports Vd*, grafo de chamadas, intervalo da
  biblioteca, API pública, sugestões de nome) → `perf/d3d/d3d_map.md` + `.json`.
- `tools/d3d_trace_gen.py` + `-Pdf.d3dTrace=true`: hooks de rastreio das candidatas
  (chamadas por frame e argumentos) em `logs/d3d_trace.log`, para confirmar no aparelho.
- Testado com código sintético no formato do codegen do ReXGlue v0.10; falta correr no
  `generated/` real (PC). Procedimento em `PLANO_RENDER_NATIVO.md`, secção F0.1.

### 2026-10-13 23:18 — primeiro arranque num Mali (Unisoc T770, Mali-G57) e capturas do RP5
- T770 (GSI TrebleDroid, Android 13): o caminho emulado **nem arranca**. O Xenia exige
  `vertexPipelineStoresAndAtomics` (escritas em storage buffers no vertex shader, para o
  memexport) e o driver do Mali-G57 não o tem: "Couldn't choose a compatible Vulkan physical
  device" → ecrã preto. Confirma o renderizador nativo, que não pode depender dessa feature.
- RP5: duas capturas (luta) com 33 shaders únicos no total (`dfc_parse --dump-shaders`);
  é a entrada para a tradução offline dos shaders.
- Patch 0036: `vertexPipelineStoresAndAtomics` deixa de ser obrigatório (só é usado por memexport
  em vertex shaders, que o Budokai 3 não usa; um draw que o peça é ignorado). Objetivo: o caminho
  emulado arrancar no Mali-G57 enquanto o renderizador nativo não está pronto. As texturas BC
  já têm descompressão no SDK quando o driver não as suporta.
- 23:40, Mali com 0036: passou do vertexPipelineStoresAndAtomics e parou no `fillModeNonSolid`
  (wireframe, que o Mali não tem). Patch 0037: no Android `vulkan_require_geometry_shader` e
  `vulkan_require_fill_mode_non_solid` passam a falso por omissão (o SDK já tem caminho sem
  eles) e as verificações de paridade exata do render target cache (formatos 16-bit, transferências
  bit-exatas) passam a avisos em vez de impedir o arranque.

### 2026-10-14 00:10 — render nativo, passo 1: resolve direto na textura (patch 0038)
- Mali a correr (0037): menus ~20 fps, luta ~10 fps, GPU ~98 ms/frame: **desenho 54 ms**,
  resolve 18, texturas 15, transfer 10 (atalho dos efeitos desligado nesse teste). No Mali o
  custo é o desenho (shaders do Xenia pesados para a G57); no 865 são os resolves.
- 0038: o atalho resolve→textura passa a escrever **diretamente na imagem da textura**
  (imageStore, sem buffer intermédio nem cópia buffer→imagem) e cobre os casos que ficavam de
  fora: altura arredondada (320x184 → textura 320x180, 160x96 → 160x90, 80x48 → 80x45),
  pitch ≠ largura (80 com pitch 96), render target 4x MSAA (média das amostras) e procura do
  render target pela chave exata (as imagens irmãs do modo direto confundiam o dump). Também a
  profundidade (k_24_8 → R32F, k_8_8_8_8 → bytes). As texturas candidatas (2D tiled sem mips,
  RGBA8 ou R32F) são criadas com STORAGE quando o atalho está ligado. Sem STORAGE fica o
  caminho antigo (buffer + cópia).
- Esperado: resolves de 8–10 ms para ~2–3 ms no 865; `atalho=` perto de `resolves=` na linha perf.

### 2026-10-14 00:07–00:11 — 0038 nos dois aparelhos
- RP5: ~50 fps em luta (era 45–52 com picos piores), GPU ~19,5 ms: desenho 9, resolve 8,
  transfer 1,7, texturas 0 (eram 2,4). `atalho=` = `resolves=` (~20/f, todos sem memória do guest).
  O utilizador nota o jogo mais fluido e sem dessincronização do áudio.
- RG Cube (Mali-G57): 12–14 fps, GPU 70–83 ms: desenho 47–54, resolve 10–16, transfer 8,7.
  Ecrã a piscar a preto (já acontecia antes do 0038). 75 fins de passe por frame, 35 por
  uploads (barreira de buffer) — no Mali os passes custam mais: testar `dbz3_hoist_uploads`.
- O resolve continua a ~8 ms no 865 com só ~20 dispatches de compute: suspeita de bolhas (cada
  resolve fecha o passe e espera a GPU esvaziar). 0039: `dbz3_resolve_debug = 1` (cvars.txt)
  faz os resolves sem trabalho nenhum, só para medir quanto deles é custo real.

### 2026-10-14 00:23–00:27 — dbz3_resolve_debug (RP5) e captura no Mali
- RP5 com `dbz3_resolve_debug = 1` (resolves sem trabalho nenhum): a categoria "resolve"
  **continua em ~9 ms** (e as texturas voltam a 7 ms porque passam a carregar da memória).
  Ou seja, os ~8 ms de "resolve" não são os dispatches: são o fim do passe (o tiler grava a
  cor + profundidade de 1280x720 na memória) e a espera da GPU que o resolve força. Fazer as
  texturas lerem o render target sem resolve não ganharia nada; o custo é a quebra de passe.
- Mali, captura com tempo por draw (cada draw no seu passe, por isso inflacionado): o
  pré-passo 4x MSAA do início do frame (2 draws, PS A4A965C189287B99, pitch 640) custa ~8 ms,
  e a transferência 4x → 1x que se segue ~9 ms. FXAA (F5F4…) ~5 ms, fundo (CB06…) ~3,7 ms.
  `dbz3_hoist_uploads` no Mali: sem ganho.
- 0040: `dbz3_skip_msaa_prepass` (cvars.txt, desligado por omissão) salta o pré-passo 4x
  (pitch 640) e, com isso, a transferência 4x → 1x. Com render targets diretos a imagem 1x já
  guarda o frame anterior, que é o que o pré-passo reconstrói. Por validar visualmente.

### 2026-10-14 00:45–01:20 — o "pré-passo 4x" é o Clear() do D3D (patch 0041)
- 0040 (`dbz3_skip_msaa_prepass`) deu ecrã preto nos dois (só 6 draws por frame): o pré-passo
  não pode ser saltado. Fica desligado (não usar).
- Desmontagem (ferramenta xdisasm feita a partir do SDK: shader.cpp + translator_disasm.cpp):
  VS 1E6883FCCDE1F688 = `vfetch` posição xyz + cor de vf95 (stride 28 B) → oPos/o0;
  PS A4A965C189287B99 = `oC0 = r0`. São os shaders embutidos do Clear() do XDK: um
  retângulo (RECTLIST) com posições em píxeis (PA_CL_VTE_CNTL = 0x300) num render target
  4x MSAA de pitch 640 (= 1280 a 1x no EDRAM), draw 0 cor+profundidade, draw 1 só
  profundidade (RB_MODECONTROL 5). Depois o render target cache transfere 4x → 1x.
- 0041 `dbz3_clear_to_1x` (cvars.txt): esse retângulo passa a ser desenhado direto na imagem
  1x (posições ×2 na memória do guest, RB_SURFACE_INFO 1x com pitch ×2). Sem draw 4x não há
  transferência 4x → 1x. Esperado: −2,5 ms no 865, até −17 ms no Mali.

### 2026-10-14 01:18–01:31 — 0042 a funcionar (Clear direto na imagem 1x) e patch 0043
- 0041 nunca disparava: exigia ≥84 B no fetch constant 95, mas o D3D declara 48 B (12 dwords)
  nos draws UP. 0042: sem tocar na memória do guest; só durante o draw do Clear (cor e só
  profundidade) liga a escala do viewport ×2 e põe RB_SURFACE_INFO a 1x com pitch ×2; os
  registos são repostos no fim do draw.
- Resultado: **transfer 1,6 → 0,12 ms no RP5 e 8,7 → 0,8 ms no Mali**. RP5 ~54–55 fps em luta
  (GPU ~18 ms: desenho 8,8 ms/64 passes, resolve 8,3), raramente abaixo de 50, várias vezes 60.
  Mali 13–14 fps em luta (GPU ~74 ms: desenho 52 ms/70 passes, resolve 16), menus sem piscar;
  o HUD ainda pisca durante a luta.
- Fins de passe por frame em luta: **Mali 73** (uploads 36,5, barreira de imagem 26,7, mudança
  de render target 6,2), RP5 40–52. Só ~6 são mudanças reais de alvo. No Mali cada fim de passe
  com o ecrã tocado custa ~0,5 ms (carga + gravação dos tiles de 1280x720): é o grosso do
  "desenho".
- 0043:
  - `dbz3_resolve_early_transition` (ligado): no fim do resolve direto (imageStore) a textura
    passa logo para leitura; antes a transição ficava para o draw que a amostra e fechava o
    passe aberto.
  - Diagnóstico: "barreira de imagem" passa a dizer o destino (amostrar / carga / copia /
    general / render target).
  - Testar também com `dbz3_hoist_uploads = true` (uploads da memória partilhada antes do passe).

### 2026-10-14 02:00–02:04 — 0043 + dbz3_hoist_uploads (patch 0044)
- **Mali: desenho 52 → 29 ms, passes 70 → 33, 13 → 19–20 fps em luta** (até ~30 em cenas leves);
  o HUD deixou de piscar; o menu ainda pisca preto às vezes. Fins de passe 73 → ~37/f: os uploads
  passaram de 36,5 para ~0,1/f (`dbz3_hoist_uploads` funciona agora). Resolve continua ~16 ms.
- **RP5: 54–60 fps em luta** (GPU 14,6–17,3 ms: desenho ~8 ms/30 passes, resolve 6–8 ms).
- Sobram ~22 "barreira de imagem (amostrar)" por frame que a transição antecipada não apanhou,
  e ~5,5 "(render target)" que são na verdade mudanças de alvo (a barreira do alvo novo fecha o
  passe antigo antes de o código contar a mudança).
- 0044: transição antecipada também no caminho de resolve por cópia de buffer
  (`Dbz3CopyToResolveTexture`); diagnóstico "dbz3: transicoes para leitura no draw" (a cada 3000
  transições: uso anterior, tamanho, formato e base das texturas mais frequentes).
- Pedidos do utilizador para depois: modo 30 fps liso (já existe `dbz3_frameskip = 1`: o jogo a
  60, desenha 1 em cada 2), opções para desligar efeitos (bloom etc.) em aparelhos fracos.

### 2026-10-14 02:17–02:22 — 0044 e frameskip no Mali; patch 0045
- Mali com `dbz3_frameskip = 1`: o contador marca 38–40 (frames do jogo), mas só metade é
  desenhada (~19 imagens/s); GPU ~51 ms por frame desenhado (desenho ~29, resolve ~15, transfer 3).
  O "quase 50 fps" visto é o jogo a correr mais perto da velocidade certa, não mais imagens.
- O diagnóstico de transições do 0044 nunca disparou: as ~15 "barreira de imagem (amostrar)" por
  frame não vêm das texturas; são os próprios resolves (o render target passa a leitura para o
  compute do resolve e isso fecha o passe). Fins de passe que sobram ≈ resolves (~16) +
  mudanças de alvo (~17). RP5 igual: 56–57 fps, GPU 16–17 ms.
- Linha do tempo da captura de luta (por lutador): resolve da profundidade → 1 draw → cópia da
  cor → passe 512x512 (pitch 560) → restauro da cor (1 draw) → **restauro da profundidade como
  cor em color@1328** (2 draws, PS ADCA7FBBCBF53B57, outra configuração de render target) →
  resto do lutador. Com render targets diretos o passe de 512x512 nunca toca na profundidade de
  1280x720, por isso esse restauro é inútil e custa 2 mudanças de alvo por lutador.
- 0045: `dbz3_skip_depth_restore` (cvars.txt, desligado por omissão) salta esses draws
  (PS ADCA… com base da cor = base da profundidade). Esperado: −6 a −8 fins de passe por frame.

### 2026-10-14 02:32–02:36 — 0045 a funcionar; patch 0046
- **RP5: 59–60 fps em luta** (GPU ~16 ms: desenho 7,5 ms/23 passes, resolve ~8). **Mali: 20–24 fps**
  (GPU 41–47 ms, desenho 21–28, resolve 11–15, transfer 2,7). Fins de passe 37 → 28/f nos dois.
  Visualmente sem problemas na profundidade.
- Leitores dos resolves (captura de luta): as cópias de profundidade dos lutadores 1 e 2 só eram
  lidas pelo restauro via cor (agora saltado); a do 3.º alimenta o desfoque 4DFC/555E/FXAA. As
  cópias de cor 1 e 2 só são lidas pelo draw de restauro (VS B4ED697CB92C1759 de passagem, PS
  6E57 `oC0 = tfetch`, vértices (0,0,0,0)(1280,0,1,0)(0,720,0,1)).
- 0046: o restauro (0032, `dbz3_rt_elide_restores`) passa a exigir cópia identidade nos vértices
  (`dbz3_rt_restore_identity_only`, ligado): os especiais usam o mesmo PS com vértices
  transformados e já não contam como restauro. Com `dbz3_rt_elide_restores = true` esperam-se
  −4 resolves de ecrã inteiro e −4 fins de passe por frame.
- Guia (GUIA_PORT_XBOX360_ANDROID.md) atualizado: resultados, ordem dos patches por ganho,
  padrões do D3D do XDK (A–D) e o plano da ferramenta.

### 2026-10-14 03:06–03:11 — 0046 nos dois; 865 a 60 fps; patches 0047/0048, launcher, Tag Team/online
- **RP5: 59,9 fps em luta, GPU ~14,5 ms** (desenho ~7,4 ms/22 passes, resolve ~6,5). Turnip também
  funciona bem com o caminho novo (teste do utilizador, sem ajustes). **Mali: 22–24 fps**, GPU 42–44
  ms (desenho ~22,7, resolve 12–14, transfer ~2,1, outros ~3,6). A eliminação apanha ~2 resolves e
  ~1,7 restauros por frame; especiais sem pretos.
- Mali, veredito: as texturas não pesam (categoria texturas = 0; as DXT pequenas são descomprimidas
  uma vez para RGBA8). ASTC/transcoder de BC não mudaria o tempo de GPU. O custo é o desenho com os
  shaders traduzidos e os fins de passe. Para o Mali: modo 30 fps (frameskip + frame cap 30, ritmo
  certo) e desligar efeitos.
- 0047: interruptores de efeitos (`dbz3_fx_bloom`, `dbz3_fx_dof`, `dbz3_fx_fxaa`, ligados por
  omissão; só com `dbz3_rt_direct`) e os validados passam a ligados por omissão
  (`dbz3_clear_to_1x`, `dbz3_hoist_uploads`, `dbz3_skip_depth_restore`, `dbz3_rt_elide_restores`).
- 0048: proporção cortada nos portáteis 16:9 — a janela do SDL não era ecrã inteiro (o jogo cria-a
  com fullscreen=false para o launcher do PC): superfície e swapchain de 1920x918 num ecrã de
  1920x1080. No Android a janela passa a ser sempre ecrã inteiro (SDK) e o GameActivity entra em
  modo imersivo (reforço). Esperado nos logs: "Window size: 1920x1080".
- Launcher: abas novas **Desempenho** (30 FPS, resolução 1x/2x/3x — só escalas inteiras —, efeitos
  do jogo, imagem, proporção, otimizações da GPU) e **Controles** (toque + comando físico); aviso de
  GPU/drivers no primeiro arranque (também no Sobre); interruptores Online e Forma inicial no Jogo.
- Tag Team e online: o CMake compila sempre a lista completa do projeto original v1.4.5.1 (é
  preciso atualizar o external/dbz3, ver "Atualizar o external/dbz3"); patches de jogo 0002
  (char8_t) e 0003 (identidade do online pelas .so do APK: Android só joga com Android com o mesmo
  APK). Sem UPnP fora do Windows: online por Wi-Fi local ou porta reencaminhada.
- Fica por portar do upstream: pack HD em segundo plano (v1.4.5/1.4.5.1), que choca com os patches
  de textura 0010–0044.

### 2026-10-14 — controles no Dragon Universe (voo livre pelo mapa)
- Queixa: na luta os controles funcionam, no mapa do Dragon Universe não (analógico, LB/RB, X).
- Causa provável (código do projeto original v1.4.4.5+, compilado no Android desde hoje): o hook de
  `XamInputGetState` (`netplay_hooks.cpp`) trata o byte de ecrã `0x824BA110 == 5` como "menu
  principal". O Dragon Universe entra pela fila 0 desse menu e o byte pode ficar a 5 lá dentro; aí
  `MenuRowsTracked()` punha **as palancas a 0** (só cima/baixo do analógico passavam, como cruceta,
  cortada a 120 ms por `DuelMenuFilter`), **X abria o painel TAG TEAM** (que bloqueia o comando) e a
  fila TAG TEAM falsa podia comer os botões. Afeta comando físico e virtual (é depois do SDL).
- Patch de jogo 0004: `campaigns.cpp` anota a hora do estado "voar pelo mapa" (`sub_821C4DA0`,
  `g_dbz3_map_fly_ms`); `online_panel.cpp` só trata o ecrã 5 como menu principal se o próprio menu
  (`sub_8218C5D8`) correu há < 1 s e ninguém voa pelo mapa (`MainMenuLive()`): filtros, X → TAG TEAM e
  a fila TAG TEAM. Fora disso o mando chega ao jogo sem mexer. Luta e Duelo não mudam.
- Controles por toque: terceiro layout **Mapa (voo)** (analógico sempre, A/B/X/Y, LB/RB/LT/RT,
  Back/Start; D-pad escondido), trocado sozinho por `nativeMapFlag` (histerese ~1,5 s); a deteção de
  luta devolve 0 no mapa (ponteiro velho da última batalha). Editor com os três; botão de troca
  menus → luta → mapa.
- Por testar: voar com o analógico físico (RP5/Odin) e com o da tela; LB/RB/LT/RT e X no mapa; menu
  principal: fila TAG TEAM e X → TAG TEAM continuam a funcionar (se não, `sub_8218C5D8` não corre
  todos os frames e é preciso afinar `MainMenuLive`).

### 2026-10-14 noite — logs Exynos 2600, Dimensity 1100 (Mali-G77) e SD480+ (patch 0049, v0.3.1)
Aparelhos: Galaxy S26 (Xclipse 960, driver do sistema), Retroid Pocket 4 Pro (MT8797 = Dimensity
1100, Mali-G77), Moto g53 (SM4350 = SD480+, Adreno 619, Turnip A7xx V26).
- **Xclipse 960:** 60 fps em luta, GPU ~10 ms (resolve ~5). Só problema: 18 "resolve 1EC2F000
  eliminado mas lido a sério" em ~4 min.
- **Mali-G77:** 35–38 fps em luta, GPU ~24 ms = desenho 13 ms (22 passes), resolve 7,5, transfer 1,4,
  outros 1,8, swap 0,75. Fins de passe ~27/f. 7 eliminações erradas do resolve 1EC2F000.
- **SD480+ com Turnip:** o driver carrega e encontra a GPU ("Turnip Adreno (TM) 619"), mas o driver diz
  `maxStorageBufferRange` 128 MB: a memória do guest fica partida em 4 buffers — o mesmo caso que deu
  geometria/texturas erradas no RP5 com Turnip (corrigido lá com "memória num só bloco"). As duas
  sessões não chegaram à luta. O logcat mostra também `setOrientation() requestedOrientation=13`.
- **Captura (Mali e Xclipse):** o resolve que alimenta o FXAA (ecrã → textura) tem **limpeza da cor**;
  o FXAA redesenhava o ecrã a partir dessa textura. Com o FXAA desligado o ecrã ia limpo (preto) para o
  frontbuffer: era o "não renderiza quando desligo o FXAA". DOF desligado estava certo.
- **renderArea:** todos os passes do jogo começavam com a renderArea da imagem inteira do render target
  (1280x**2048** para um ecrã de 1280x720, o EDRAM todo). Num GPU de tiles (Mali, Adreno) a carga e a
  gravação dos tiles são proporcionais à renderArea: ~2,8x mais tráfego de memória por fim de passe.

Patch 0049 (SDK):
- **Segundo plano → ecrã preto:** a superfície Vulkan ficava presa ao `ANativeWindow` antigo. No Android
  o `SDL_EVENT_WINDOW_MINIMIZED` desliga o presenter e o `RESTORED` recria a superfície com a janela nova
  (o SDL só manda o RESTORED depois do surfaceCreated). A `AndroidNativeWindowSurface` passa a guardar a
  sua própria referência (`ANativeWindow_acquire/release`).
- **`dbz3_tight_render_area` (ligado):** a renderArea de cada passe do jogo é ajustada no fim do passe à
  união dos scissors dos draws (o Clear() do D3D usa scissor 8192: aí conta o retângulo dos 3 vértices,
  ×2 quando redirecionado para 1x), alinhada a 32 px. Os passes de transferência ficam com a área toda.
- **FXAA desligado:** salta a limpeza da cor nesse resolve (só nos frames com FXAA, resolve 1280x720 da
  base 0). **Brilho desligado:** além da cadeia, saltam-se draws e resolves em render targets com pitch
  ≤ 320 nos frames com a cadeia (~8 passes e ~8 resolves por frame a menos).
- **`dbz3_rt_elide_depth_resolves` (desligado):** só resolves de cor podem ser eliminados; a eliminação
  dos de profundidade errava (frame com a profundidade velha = piscada).
- **Modo rápido do resolve:** se uma textura que o atalho não encontrou for carregada da memória desse
  destino até 2 frames depois, esse destino passa a ter sempre o resolve completo (log "textura … lida
  da memoria depois de um resolve so para textura").
- **Turnip:** `dbz3_vk_turnip_single_shared_buffer` (ligado) aplica sozinho os 512 MB num só buffer no
  Turnip quando o driver diz menos. O driver alternativo é testado (instância + GPUs) antes de ser usado;
  se não servir para esta GPU (ex.: Turnip só de A7xx num Adreno 8xx) usa-se o do sistema e o motivo vai
  para `logs/vulkan_driver_status.txt` (mostrado na aba Drivers). `vkWaitForFences` repete com
  VK_TIMEOUT (Turnip/KGSL).
- Log "tiron" só para frames bem acima da média (em aparelhos lentos as 300 linhas acabavam logo).

App (v0.3.1):
- **Orientação:** o `GameActivity` sobrepõe `setOrientationBis` (o SDL pedia FULL_USER ao criar a janela
  e o jogo seguia o sensor até retrato).
- **Drivers:** ativar copia todos os `.so` do pacote (drivers Qualcomm empacotados trazem dependências) e
  grava `.library` com a biblioteca principal; o `GameActivity` copia todos e usa essa. Aba Drivers mostra
  o resultado do último arranque.

Por testar: voltar do segundo plano (luta e menus); FXAA/brilho/DOF desligados; Mali-G77 e G57 com a
renderArea justa (esperado: muito menos "desenho"/"resolve" por fim de passe); Turnip no SD480+ e no RP5
sem a opção manual; Odin 3 com o Turnip que falhava (a aba Drivers diz se caiu para o do sistema).

### 2026-10-17 — v0.5.0: cortes do Dragon Burst, autoteste, abaixo de 720p, 120 FPS (patches 0050–0057)

Base: `docs/pt-BR/RESUMO_BURST_LIMIT.md` do OptXbox, §2 (primeiro os cortes genéricos, depois medir, render
nativo só se um passe continuar no topo). Os patches do Dragon Burst foram aplicados sobre o SDK do BL
(fork `burstlimit`, 465f9ef) para ter o código final de cada passo e portados à mão para o DF (o DF não
tem os passos 1–3b do BL, o `DBZ3_DRAW_RET` nem o `freeze_game`).

SDK:
- **0050** (DB 0058) captura `.dfc`: no 1.º frame cada resolve é lido de volta da GPU (registo 8,
  `resolve_readback=1`); durante a leitura os atalhos sem memória/elisão ficam desligados.
- **0051** (DB 0068 sem o post_lite + 0069) `dbz3_resolve_dedupe`: resolve repetido (sem draws, mesmos
  parâmetros, destino já com a imagem) e destino lido só por draws saltados (efeitos desligados,
  restauros, pré-passo 4x, restauro da profundidade) durante 120 escritas deixam de ser resolvidos;
  `dbz3_skip_null_draws`. Linha `dbz3: dedupe: N draws nulos, M resolves repetidos e K sem leitor`.
- **0052** (DB 0070) `dbz3_discard_full_overwrite`: só com dynamic rendering. No DF a área do Clear()
  do D3D (scissor 8192) é o retângulo **interior** dos vértices; num passe limitado a área justa
  (`dbz3_tight_render_area`) fica desligada (alargar escreveria lixo).
- **0053** (DB 0073/0075/0076/0078/0079) interruptores do modo direto (`dbz3_rt_direct_drop_transfers`,
  `_clear_siblings`, `dbz3_rt_keep_dd/cd/dc/cc`, pares `dbz3_rt_dc_*`) e cache de pipelines por driver.
  **Por omissão como na 0.4.0** (keep_* desligados): o autoteste diz se algum faz falta no B3.
- **0054** (DB 0071–0074, 0077, 0078) passo 4 `dbz3_render_into_texture` (BGRA, texturas sem STORAGE,
  render pass sem dynamic rendering, desligado no Turnip). Diferenças do DF: só com
  `dbz3_direct_resolve_skip_memory` (não escreve a memória), nunca num destino de `g_dbz3_need_memory`, o
  intervalo entra no `g_dbz3_skipped_memory` (deteção de leitura pela memória), e um restauro que copia
  de volta uma textura feita pelo passo 4 nunca é elidido (`Dbz3RitIsStaleSource`). Todas as chaves do
  autoteste passaram a `HotReload` (efeitos, área justa, pré-passo 4x, clear_to_1x, restauro, elisão da
  profundidade, early transition).
- **0055** (BL 94a1274 genérico + DB 0089 + partes da 0091 que servem ao DF) `draw_resolution_percent`
  no Vulkan: RTs a 1x, jogo no canto, viewport/scissor escalados, resolves e texturas de dados resolvidos
  no tamanho menor (páginas resolvidas seguidas também abaixo de 1x), `ps_param_gen`, amostras de
  oclusão, área do descarte (para dentro) e da área justa (para fora). O atalho resolve → textura, o modo
  sem memória e o passo 4 continuam abaixo de 1x (guest vs host: `dbz3_guest_width/height`,
  `Dbz3TextureWidth/Height`). Logs `Rendering below 1x: 75% (960x540 of 1280x720)` e `dbz3: resolucao
  interna`. Tamanhos do B3 (1280x720, 320x184, 160x96, 80x48, 512x512) exatos a 75% e a 50%.
- **0056** `dbz3_freeze_frame` (novo, não existe no DB): a GPU para logo depois da troca que fecha o
  frame F e repete F a ~60 Hz a partir de cópias do ring e dos indirect buffers, com os registos do
  início de F; interrupções e WAIT_REG_MEM são ignorados nas repetições; ao descongelar voltam os
  registos do ponto de paragem e o ring continua. O jogo fica à espera da GPU (para em 1–2 frames).
- **0057** (DB 0092) interpolação de frames no presenter (`dbz3_frame_interpolation`, qualidade 1),
  `PresentStats`, Hz da tela, linha `Apresentacao`, `game_fps`/`presented_fps`/`interp=` na perf. FPS do
  jogo para o presenter = 60/(1+frameskip) (o B3 não tem `frame_rate`).

App:
- `GameActivity.requestDisplayRate(hz)` + `DisplayRates` (substitui o `requestSixtyHz`): 60 Hz ou 120 Hz
  com o 120 FPS interpolado; a taxa real vai para o runtime (`NativeGameState.setDisplayHz`).
- Desempenho: taxa de quadros 30/60/120 (o 120 só aparece em telas ≥ 110 Hz) e resolução
  360p/540p/720p/1440p/2160p (`draw_resolution_percent` + `dbz3_resolution_scale`).
- `RenderSelfTest` (menu do jogo, só numa luta): congela com `dbz3_freeze_frame`, referência = config
  atual, uma chave de cada vez, "TUDO desligado", `logs/autoteste.txt` + JPGs, exporta o zip sozinho.
  JNI novos: `nativeSetCvarsLive` (sem gravar o toml), `nativeGetCvar`, `nativeLogLine`,
  `nativeSetDisplayHz`, `nativePresentInfo`.

Sombra/silhueta 512x512 (pitch 560): na captura do Dimensity 1100 (Mali-G77, 14/10 19:20) são 42 draws
em 2 frames (21 por frame, os dois lutadores), 11 206 índices, **3,98 ms de 168 ms (2,4%)** do tempo por
draw (que no Mali é inflado pelo flush por draw). É limitado por vértices/overhead, não por
preenchimento: baixar só a sombra pouparia < 1%. **Decisão: não vira opção.** A 540p o passe já vai a
384x384 com o resto. A/B pela linha perf no RG Cube só se o autoteste/perf mostrar o contrário.

Por testar (RP5, RG Cube, Odin 3): autoteste em cada um (tudo "igual"? algum `com transferencias …`
diferente?); `dbz3: dedupe:` e `dbz3: passo 4:` no log; perf a 720p vs 540p no Cube (esperado: cargas
~0 a 540p, GPU bem abaixo de 42 ms); 120 interpolado no Odin 3 (`presented_fps` ~120, `interp=`).

### 2026-10-17 14:09 — 1.º autoteste (RG Cube, 540p, modo 30 FPS) e v0.5.1 (patch 0058)

- **O congelar não pegou**: nenhuma linha `dbz3: congelar`, ruído 50% e a luta acabou durante o teste
  (última foto no menu "Battle Again?"). Causa provável: um indirect buffer de 0 dwords marcava
  "excesso" e a gravação recomeçava em todos os frames; limite de 4096 IBs também curto. 0058: IBs vazios
  ignorados, limite 65 536 IBs / 96 MB, repetição pela ordem gravada (cursor) e avisos no log (troca fora
  do ring principal, frame grande demais, 60 repetições). O autoteste desliga o painel de FPS
  (`debug_overlay`, desenhado na imagem) e avisa quando o ruído passa de 5%.
- As chaves mudam ao vivo (a linha perf acompanha cada estado). GPU por frame desenhado a **540p**:
  ~29 ms (desenho 13, resolve 9,5, transfer 2, outros 3,6; resolves 10,3/f, todos pelo atalho;
  cargas 0) contra 42–50 ms a 720p antes: **30 FPS estáveis no Cube**. Sem cada parte (ms/frame):
  transferências saltadas 43, limpar irmãs 58, keep cd/dc 42–45, RT diretos 78, resolve direto 45,
  TUDO desligado 109.
- `dedupe`: 0/0/0 (com todos os efeitos ligados não há resolves sem leitor). Passo 4: 1 assinatura
  (bloom 240x135), 1 feito e depois banida; o resto falha em "texturas" (n ≠ 1 a 75%).
- Descarte: só o bloom passa; recusados por cobertura desconhecida o FXAA (VS A505100C5BC9C52F, PS
  F5F4…) e o restauro (VS 95DD91E3E9AE3A74, PS ADCA…). Próximo: ler o layout desses VS na captura e
  aceitar se cobrirem o scissor.
- Fins de passe 30/f, metade por "barreira de imagem (amostrar)" (14/f): alvo seguinte no Mali.

### 2026-10-17 14:29 — 2.º autoteste no RG Cube (720p, 60) e v0.5.2 (patch 0059)

- Foco agora só no RG Cube (o RP5 e o Odin 3 já fazem 60 a 720p).
- O 0058 avisou "troca fora do ring principal": no Budokai 3 o `VdSwap` vai dentro de um indirect
  buffer. 0059: a troca só marca `g_swap_seen`; a fronteira do frame é tratada no ring principal,
  depois do pacote que a trouxe (`Dbz3FreezeAtFrameEnd`).
- Luta a 720p no Cube: GPU ~42 ms (desenho 19,5, **resolve 16**, transfer 2,6, outros 3); resolves
  21/f (1280x720: 10, 320x184: 5, 80x48: 2, 512x512: 2; 19 pelo atalho); fins de passe 26/f, **14/f
  "barreira de imagem (amostrar)"** (no menu só 2/f). 0059 separa essa causa pelo layout antigo
  (general/imageStore, cópia, alvo de cor, nova…). Passo 4: "texturas" 1911 falhas a 720p; o 0059 diz
  se são 0 ou várias e regista os destinos.
- O autoteste passa a medir também os efeitos (brilho, DOF, FXAA) para as predefinições do Mali.
