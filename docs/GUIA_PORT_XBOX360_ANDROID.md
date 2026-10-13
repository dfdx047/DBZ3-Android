# Guia: portar jogos de Xbox 360 recompilados para Android

Este guia junta o que se aprendeu com o port do Budokai 3 HD (este repositório) e com o estudo de
três ports que funcionam em Android: **Unleashed Recompiled Android** (Sonic Unleashed),
**Skate3-Mobile** (Skate 3) e **RB2-Android** (Raging Blast 2). Serve de ponto de partida para
qualquer port novo de Xbox 360: o que fazer primeiro, o que medir, que caminho de render escolher
e que armadilhas já são conhecidas.

Escrito a 2026-10-13. Os números de desempenho vêm de logs reais; onde algo ainda não foi
testado em aparelho, está dito.

---

## 1. Resumo em cinco frases

1. A CPU não é o problema: o código PowerPC recompilado para ARM64 corre bem até em SoCs médios.
2. O problema é a GPU: emular a GPU do Xbox 360 (Xenos) ao nível dos registos e do EDRAM, como faz
   o Xenia, custa 2 a 4 vezes mais GPU do que o jogo precisa, e os drivers de Android mais
   antigos (Qualcomm em Adreno 6xx, Mali) penalizam exatamente o que essa emulação faz.
3. Os ports que correm em hardware fraco **não emulam a GPU**: intercetam o jogo acima dela
   (funções da biblioteca D3D do XDK, ou funções do motor do jogo) e desenham com um
   renderizador Vulkan próprio.
4. A biblioteca D3D do XDK é a mesma em todos os jogos de Xbox 360 (mudam a versão e os
   endereços), por isso **o nível D3D é reutilizável entre jogos**: é aí que vale investir.
5. Antes de escrever o renderizador, mede-se: captura de frames, tempo de GPU por draw,
   shaders do jogo desmontados. Sem isso não se sabe o que otimizar.

---

## 2. Os quatro ports de referência

| | Unleashed Android | Skate3-Mobile | RB2-Android | DBZ3 (este) |
|---|---|---|---|---|
| Base | UnleashedRecomp (XenonRecomp) | ReXGlue (fork próprio) | ReXGlue SDK v0.10 | ReXGlue SDK v0.10 |
| Render | **Nativo, nível D3D** | **Nativo, nível motor + D3D** | Emulado (Xenia) + 37 patches | Emulado (Xenia) + patches 0001–0035 |
| Shaders | Ucode do jogo → HLSL **offline** (XenosRecomp) → SPIR-V embutido | **Escritos à mão** em HLSL (4,1k linhas), SPIR-V offline | Tradução Xenia em tempo de execução | Tradução Xenia em tempo de execução |
| Resolves | Textura passa a apontar para a superfície (sem cópia) | Não existem (render target próprio) | Resolve fundido em compute | Atalho direto para textura (parcial) |
| Alvo mínimo | Adreno 7xx com Turnip; Mali Valhall com Vulkan 1.3 | SD695 / Adreno 619, Mali-G57 | Adreno 7xx/8xx com Turnip | Adreno 650 (driver do sistema), Mali-G57 |
| Tamanho do render | ~8,5k linhas (`gpu/video.cpp`) | ~35k linhas + 4,1k HLSL | 0 específico (tudo no SDK) | — |

### 2.1 Unleashed Recompiled Android (SansNope)
- **Como desenha:** `UnleashedRecomp/gpu/video.cpp` substitui 42 funções da biblioteca D3D do
  jogo com `GUEST_FUNCTION_HOOK(sub_XXXXXXXX, Função)`: `CreateDevice`, `CreateTexture`,
  `LockTextureRect`/`UnlockTextureRect`, `CreateVertexBuffer`/`Lock`/`Unlock`,
  `CreateIndexBuffer`, `CreateSurface`, `SetRenderTarget`, `SetDepthStencilSurface`, `Clear`,
  `SetViewport`, `SetScissorRect`, `SetTexture`, `SetStreamSource`, `SetIndices`,
  `SetVertexDeclaration`, `CreateVertexShader`/`SetVertexShader`, `CreatePixelShader`/
  `SetPixelShader`, `DrawPrimitive`, `DrawIndexedPrimitive`, `DrawPrimitiveUP`, `StretchRect`
  (o resolve), `Present`, `GetBackBuffer`, `D3DXFillTexture`, etc.
  O jogo nunca chega a escrever comandos PM4 para a GPU: cada chamada D3D vira um comando numa
  fila (`g_renderQueue`) consumida por uma thread de render que fala com Vulkan/D3D12 (via a RHI
  "plume").
- **Shaders:** o ucode Xenos de cada shader do jogo é convertido para HLSL **no PC, na
  compilação** (`tools/XenosRecomp`), compilado com DXC para SPIR-V e DXIL, comprimido com zstd
  e embutido no executável. Em tempo de execução não há tradução: procura-se o shader pelo hash.
  O README do XenosRecomp avisa que foi feito para o Unleashed e que outros jogos vão precisar de
  ajustes (container dos shaders, indexação dinâmica de registos).
- **Resolves (`StretchRect`):** não copia nada. A textura de destino passa a referir a superfície
  de origem (`texture->sourceSurface = surface`); só há resolve real com MSAA.
- **Texturas:** criadas no host em `CreateTexture`; os dados chegam no `UnlockTextureRect` ou
  diretamente dos ficheiros DDS do jogo. Sem BC no GPU (Adreno com Turnip, Mali), transcodificação
  no CPU.
- **Android:** swapchain RGBA (a WSI do Android não expõe BGRA), recriação do swapchain quando o
  `ANativeWindow` muda, `ANativeWindow_setFrameRate` para passar de 60 Hz, sem MSAA por omissão,
  resolução a 50% por omissão. Drivers Turnip próprios por geração de Adreno (`TU_DEBUG=sysmem,nobin`
  no 8 Elite: ~12 ms de GPU contra ~26 ms no driver Qualcomm). Mali: só Valhall com Vulkan 1.3.
- **Lição:** é o método mais limpo e o mais reutilizável. As 42 funções existem em todos os jogos
  de Xbox 360 (mesma biblioteca do XDK); muda o endereço e alguns detalhes de versão.

### 2.2 Skate3-Mobile (Buku313, renderizador de mchughalex)
- **Como desenha:** hooks em três níveis (`src/skate3_native_render.cpp`,
  `skate3_native_scene*.cpp`, ~35k linhas):
  - funções do **motor do jogo** (RenderMesh, lista de draws da cena, registo de texturas,
    matrizes e paletas de ossos), lendo as structs do jogo por offsets descobertos à mão;
  - funções **D3D** (SetPending_AluConstants, SetPending_RenderStates, SetIndices,
    SetStreamSource, DrawIndexedVertices, DrawVertices, Set*Shader, SetViewport, Swap);
  - funções de **lógica** (gestor do "LivingWorld", spin-waits) para poupar CPU.
- O hook é uma sobreposição da função recompilada:
  `extern "C" REX_FUNC(sub_XXXXXXXX) { ...; __imp__sub_XXXXXXXX(ctx, base); ... }`.
- **Shaders escritos à mão** (`src/native/shaders/*.hlsl`), vários deles transcritos do ucode do
  jogo e comparados píxel a píxel com um interpretador de ucode; SPIR-V gerado offline
  (DXC, Vulkan 1.1). Constantes de material lidas dos bancos de constantes do jogo a cada frame.
- **Texturas:** untile e endian no CPU a partir das fetch constants, em threads de trabalho, com
  cache LRU e orçamento por RAM. Sem BC no GPU (Mali), DXT1/3/5 descomprimidos no CPU para RGBA8
  (ou RGB565). Mips de topo descartados em aparelhos com menos de 4 GB.
- **Caminho emulado:** continua a existir, mas com `native_render_suppress_emulated_draws` o
  command processor ignora draws e resolves; fica só para lightmaps, menus com
  render-to-texture e vídeos.
- **Android:** colocação de threads por cluster (CP, render, áudio nos núcleos rápidos; XMA,
  ficheiros e carregamentos nos lentos), ThinLTO, limite de 4 descriptor sets (Adreno 619/650),
  compilação de pipelines assíncrona, preset "Performance" para Mali (512x288, sem sombras/SSAO/
  HDR, distância de desenho a metade).
- **Ferramentas de engenharia reversa:** trace de entradas de função (`skate3_guest_trace.cpp`),
  captura de todos os draws com bancos de constantes e snapshots de memória
  (`skate3_native_diagnostics.cpp`), desmontador de ucode, interruptor nativo/emulado (F5).
- **Lição:** chega ao hardware mais fraco, mas é engenharia reversa do motor de um jogo
  específico; nada é reutilizável noutro jogo além da infraestrutura.

### 2.3 RB2-Android (moddedrounds, a partir do port do THP8)
- Caminho **só emulado** (Xenia), com 37 patches ao SDK ReXGlue v0.10. Corre a ~60 fps num
  8 Gen 2 (Adreno 740, Turnip) com ~12 ms de GPU; o driver Qualcomm dá artefactos.
- Patches com mais efeito (ver `patches/rexglue-sdk-android/` lá):
  - **0002** presenter recompilava o pipeline final em cada frame (84% da thread de UI).
  - **0009** o armazenamento de shaders/pipelines nunca era lido no Android (`fopen("a+")`
    começa no fim do ficheiro na libc do Android).
  - **0003/0007/0010/0020** uploads e cargas de texturas antes do passe (menos passes partidos).
  - **0018** `gpu_implicit_texture_lod` e `gpu_static_texture_signs` (o mesmo problema que o
    nosso 0035 resolve, por variantes de tradução em vez de specialization constants).
  - **0021/0022** resolve fundido (compute lê o render target e escreve na memória do guest).
  - **0013/0028/0032** páginas voláteis (comparar com cópia em vez de proteger memória:
    `mprotect` chegava a 36% da thread do CP).
  - **0026/0029** custos de CPU do command processor (mapa de texturas, splice, scratch).
  - **0012** `vkWaitForFences(0)` vira espera sem fim no Turnip/kgsl: queries de conclusão.
  - **0033** vblank do guest sincronizado com o vsync do ecrã (Choreographer).
  - **0035** áudio 5.1 → estéreo.
- Hooks do jogo: só resolução (render targets de 1280x720 escalados) e o vsync do jogo trocado
  por um sleep. Afinidade de threads + ADPF (`APerformanceHint`) na app.
- **Lição:** é o máximo que se tira do caminho emulado. Suficiente em Adreno 7xx/8xx com Turnip,
  insuficiente para Adreno 6xx com driver Qualcomm e para Mali.

### 2.4 DBZ3 (este port) — o que medimos
- RP5 (SD865, Adreno 650), driver do sistema, luta 1280x720:
  - de início ~39–44 ms de GPU por frame (desenho, transfers, resolves, texturas);
  - com render targets diretos, atalho de profundidade e de cor: 17–19 ms em luta normal
    (~50–57 fps), ~26 ms com especiais (~38 fps). Resolves ainda ~5–10 ms.
- Turnip no Adreno 650: geometria certa só com a memória do guest num único storage buffer de
  512 MB (fora da especificação), texturas sempre erradas. Abandonado.
- O frame do jogo: ~400 draws e ~30 shaders; cadeia de brilho 1280→320x184→160x96→80x48;
  silhuetas dos lutadores em 512x512; 15–21 resolves por frame. O jogo inteiro usa ~33 shaders.
- Os draws mais caros são passes de ecrã inteiro (desfoque dependente da profundidade, FXAA),
  porque cada leitura de textura traduzida pelo Xenia carrega ramos por píxel (sinal da
  textura, gamma, amostragem signed) e gradientes explícitos. Patch 0035 especializa isso
  (1329 → 450 instruções no shader de desfoque).

---

## 3. Porque é que a emulação da GPU é cara em telemóveis

O Xenia emula a Xenos ao nível do hardware. Em PC sobra GPU; em telemóvel, cada um destes custa:

1. **EDRAM e resolves.** A Xenos desenha num EDRAM de 10 MB e copia ("resolve") para a memória
   principal. O Xenia guarda os render targets em imagens do host e, a cada resolve, despeja o
   render target para um buffer, converte para o layout em tiles do Xbox na memória do guest e
   depois a cache de texturas tem de fazer o caminho inverso (untile) para o jogo o poder ler.
   São 2 a 3 passes de compute por resolve, a 1280x720, 15–21 vezes por frame.
2. **Transferências de propriedade.** Quando dois render targets partilham o mesmo pedaço de
   EDRAM (o jogo reaproveita endereços), o Xenia copia o conteúdo de um para o outro.
3. **Passes partidos.** Nos GPUs de tiles (Adreno, Mali), cada fim de passe grava o tile em
   memória e o seguinte volta a carregá-lo. Uploads, barreiras e cópias a meio do frame partem
   passes. (No RP5 medimos que isso pesava pouco; nos testes do RB2 pesava muito.)
4. **Shaders traduzidos genéricos.** Cada shader é traduzido sem saber o formato das texturas,
   o formato do render target nem que constantes vão ser usadas: cada leitura de textura leva
   `switch` por componente, duas amostragens, gamma por partes, gradientes explícitos; a
   memória do guest é lida por storage buffers com troca de endian por vértice; a regra
   0 × ∞ = 0 da Xenos custa 3–4 instruções por multiplicação.
5. **Drivers.** O driver Qualcomm dos Adreno 6xx (Vulkan 1.1) compila mal shaders grandes, não
   tem `scalarBlockLayout` (o ReXGlue tinha constantes fora do std140) e tem bugs com o caminho
   do Xenia. O Turnip resolve no 7xx/8xx mas não no 650 neste caso. O Mali não tem BC, e os
   Mali Bifrost não chegam aos requisitos.

Um renderizador nativo elimina 1, 2 e 3 (o render target é do host, os resolves viram
"esta textura é aquela imagem"), e 4 (shaders traduzidos para um caso concreto ou escritos à
mão), e precisa de muito menos do driver.

---

## 4. Que caminho escolher

```
O jogo corre no ReXGlue/Xenia em PC?
 └─ sim → medir no aparelho mais fraco que se quer suportar (secção 6)
           ├─ GPU por frame ≤ 70% do orçamento (≤ 11 ms a 60 fps) com os patches deste repo
           │    → ficar no caminho emulado; aplicar a secção 7
           └─ acima disso, ou Mali / Adreno 6xx com driver do sistema como alvo
                → renderizador nativo ao nível D3D (secção 8)
                   ├─ cobre tudo → acabou
                   └─ partes do jogo usam truques de GPU que o nível D3D não vê
                      (memexport, leitura direta do EDRAM, PM4 escrito à mão)
                        → esses casos no caminho emulado; ou hooks do motor (estilo Skate 3)
```

Regra prática: **se o alvo inclui Mali ou Adreno 6xx sem Turnip, ir para o nativo cedo.** O
DBZ3 gastou um dia de patches ao caminho emulado para passar de ~22 para ~50 fps no 865, e
ainda não chega a 60 com efeitos de ecrã; o Skate 3, com render nativo, corre num SD695.

---

## 5. Fase 0 — base do port (vale para qualquer caminho)

Checklist do que já partiu em algum dos ports e tem correção conhecida:

- **Compilação:** `-march=armv8.2-a` em tudo (jogo e SDK): atómicos LSE em linha em vez de
  chamadas `__aarch64_cas*`; `-ffp-contract=off` no código recompilado (o clang funde a*b+c e
  muda resultados em relação ao PowerPC); ThinLTO em Release (Skate 3). Evitar extensões como
  CRC sem confirmar o SoC (SIGILL em aparelhos antigos).
- **libc do Android:** `fopen("a+")` começa a ler no **fim** do ficheiro: fazer `Seek(0)` antes
  de ler caches de shaders/pipelines (senão recompila tudo a cada arranque).
- **Swapchain:** a WSI do Android só expõe formatos RGBA; o Xenia/Unleashed assumiam BGRA.
  Recriar o swapchain quando o `ANativeWindow` muda (voltar do segundo plano = ecrã preto com
  áudio). `ANativeWindow_setFrameRate` para ecrãs de 90/120 Hz.
- **Presenter:** guardar o formato do swapchain (bug do pipeline recompilado a cada frame).
- **Constantes de shader em std140:** sem `scalarBlockLayout` (driver Qualcomm Vulkan 1.1),
  qualquer struct de constantes fora do std140 dá cores/sinais errados ou crash no compilador
  do driver (patch 0026 aqui).
- **Storage buffers:** `maxStorageBufferRange` de 128 MB parte a memória do guest em 4
  buffers e põe um `switch` em cada acesso; no Adreno 8xx é um só.
- **Espera em fences:** Turnip/kgsl pode transformar `vkWaitForFences(0)` numa espera sem fim
  e devolver `VK_TIMEOUT` numa espera infinita: repetir em vez de reutilizar buffers.
- **Threads:** CP/render/thread principal do jogo nos núcleos rápidos, XMA/ficheiros nos lentos;
  ADPF (`APerformanceHint`) com a duração do frame; sem spin-waits a ocupar um núcleo
  (`kernel_wait_poll_backoff_us`).
- **Áudio:** fold 5.1 → estéreo; relógio do áudio isolado das paragens da saída do Android
  (Unleashed: produtor com relógio próprio + recuperação de rota AAudio).
- **Ciclo de vida:** pausar as threads do guest em segundo plano; páginas de 16 KB
  (Android 15+).
- **Launcher:** importar o jogo, drivers (adrenotools), logs exportáveis sem adb, captura de
  frames no menu do jogo (secção 6).

---

## 6. Fase 1 — medir antes de otimizar

Ferramentas que já existem neste repo e devem ir para qualquer port novo:

1. **Estatísticas por janela de 5 s** (`dbz3: perf …` no log): fps, GPU ms por frame e por
   categoria (desenho, transfer, resolve, texturas, swap), resolves por frame e por tamanho,
   passes por frame, causas de fim de passe.
2. **Captura de frames** (patch 0027, botão "Capturar 2 frames"): grava registos, draws,
   resolves, shaders (ucode) e memória lida para `logs/capture/*.dfc`.
   `tools/dfc_parse.py` imprime o mapa de passes (`--draws`, `--regs`, `--dump-shaders`,
   `--dump-mem`).
3. **Tempo de GPU por draw** (patch 0034): durante a captura, cada draw no seu passe com
   timestamp; resumo por pixel shader no log e `<captura>.dfc.timing.txt`.
4. **Desmontador de ucode e tradutor no PC:** compilar `shader.cpp`, `translator*.cpp`,
   `spirv_translator*.cpp`, `spirv_builder.cpp` e o `SPIRV/` do glslang para um executável
   que lê os `.bin` do `--dump-shaders` e escreve o SPIR-V que o aparelho recebe. Validar com
   `spirv-val` e simular o que o driver faz com `spirv-opt --set-spec-const-default-value …
   --freeze-spec-const -O`. Contar instruções, amostragens e ramos antes/depois de cada
   mudança, sem esperar por um build no aparelho.

O que procurar numa captura:
- quantos draws/shaders por frame e quais são de ecrã inteiro;
- a cadeia de resolves (que render target vai para que textura, que tamanho, quantas vezes);
- que shaders dominam o tempo e porquê (leituras de textura, ALU, controlo de fluxo).

---

## 7. Fase 2 — tirar o máximo do caminho emulado

Ordem aproximada por ganho medido (DBZ3 = patches deste repo; RB2 = patches lá):

1. **Render targets diretos** (DBZ3 0028/0029): cada render target fica na sua imagem; só se
   transfere entre imagens com a mesma base/pitch/formato e MSAA diferente.
2. **Resolve direto para textura** (DBZ3 0028/0031, RB2 0021/0022): o resolve escreve a textura
   que o jogo vai ler (compute do render target → buffer → imagem), sem passar pela memória do
   guest quando ninguém a lê do CPU. Hoje cobre 6–12 dos 15–21 resolves do DBZ3; o patch 0035
   regista porque os outros ficam de fora.
3. **Shaders especializados** (DBZ3 0035, RB2 0018): sinais das texturas como specialization
   constants e LOD implícito. Ganho por confirmar no aparelho.
4. **Pipelines e shaders guardados e lidos** (correção do `a+`), compilação assíncrona.
5. **Uploads antes do passe** (RB2 0003/0007/0010/0020): no RB2 eram ~84% dos passes; no DBZ3
   não mudou o tempo de GPU (desligado por omissão).
6. **Páginas voláteis** em vez de proteção de memória (RB2 0013/0028/0032), custo de CPU do CP.
7. **Resolução interna** abaixo de 100% para GPUs fracos + FSR1/bilinear no presenter.
8. **Frameskip** (DBZ3 0023): último recurso para manter a velocidade do jogo; a 30 fps um jogo
   de luta fica mau.

O que **não** resultou no DBZ3: eliminar resolves/restauros "redundantes" por heurística (0032)
— os efeitos dos especiais usam o mesmo shader de cópia e ficaram pretos.

Limite deste caminho, medido: 865 com driver do sistema ~17–26 ms por frame no DBZ3; RB2 ~12 ms
num 8 Gen 2 com Turnip.

---

## 8. Fase 3 — renderizador nativo ao nível D3D (o método a reutilizar)

### 8.1 Encontrar as funções D3D no executável
Todos os jogos de Xbox 360 ligam estaticamente a biblioteca D3D do XDK. As funções a encontrar
são as ~42 que o Unleashed substitui (lista na secção 2.1); o Skate 3 encontrou as mesmas
noutra versão do XDK (`SetPending_AluConstants`, `SetPending_RenderStates`,
`DrawIndexedVertices`, …). Formas de as encontrar:
- **Comparar com um jogo já mapeado:** as funções D3D têm a mesma estrutura entre jogos da
  mesma versão do XDK; procurar sequências de instruções características (escritas de pacotes
  PM4 com os mesmos opcodes, offsets da struct do device, como o `device+0x480` das fetch
  constants no Skate 3).
- **Rastrear a partir do PM4:** com a captura (secção 6), ver que função do guest escreveu cada
  pacote `DRAW_INDX`/`EVENT_WRITE` (resolve)/`SET_CONSTANT` — com um trace de entradas de função
  (`REX_FUNC_PROLOGUE` instrumentado, como o `skate3_guest_trace.cpp`).
- **Strings e asserts** do D3D de debug, quando o jogo os tem.
Resultado desta etapa: uma tabela `endereço → função D3D` por jogo. É a única parte específica
do executável; o resto do renderizador é comum.

### 8.2 Estrutura
Como no Unleashed:
- cada função D3D substituída (`REX_FUNC(sub_…)` ou `GUEST_FUNCTION_HOOK`) lê os argumentos do
  guest e põe um comando numa fila;
- uma thread de render consome a fila e fala com Vulkan;
- o caminho emulado fica desligado (o jogo não chega a gerar PM4 para essas chamadas).

### 8.3 Recursos
- **Texturas:** criadas no host em `CreateTexture`; os dados copiados no `Unlock` (untile e
  endian no CPU, uma vez, em cache). BC nativo se o GPU tiver; senão descomprimir no CPU (RGBA8
  ou RGB565 para DXT1 opaco) ou transcodificar para ETC2/ASTC. Descartar mips de topo em
  aparelhos com pouca RAM.
- **Vertex/index buffers:** criados no host; no `Unlock` copiar e trocar o endian (ou
  descodificar formatos Xenos para formatos do host). Vertex fetch nativo (atributos de vértice
  normais), não leituras de storage buffer.
- **Render targets e resolves:** superfícies do host com o tamanho real. `StretchRect`/resolve
  = a textura de destino passa a ser a imagem do render target (sem cópia); copiar só se o
  jogo voltar a desenhar no render target enquanto a textura ainda é lida, ou com MSAA.
- **Constantes:** os bancos de constantes do device (VS 256 float4, PS 224 float4) num uniform
  buffer por draw; os "dirty ranges" que o D3D já mantém dizem o que mudou.

### 8.4 Shaders
Duas opções, por ordem de preferência:
1. **Tradução offline** (XenosRecomp no Unleashed): extrair todos os shaders do jogo (dos
   ficheiros ou das capturas), converter para HLSL/GLSL no PC, compilar para SPIR-V e embutir.
   Precisa de adaptar o leitor do container de shaders ao jogo. Como os jogos usam poucos
   shaders (DBZ3: ~33), dá para rever os mais pesados à mão.
2. **Tradução em tempo de execução** com o tradutor do Xenia, mas **especializada**: formato
   das texturas, render target e regras de 0 × ∞ conhecidos por pipeline (specialization
   constants ou variantes), LOD implícito, vertex fetch nativo. É o meio-termo se o jogo
   tiver muitos shaders.
Os shaders mais caros (pós-processamento de ecrã inteiro) podem ser trocados por versões
escritas à mão, identificadas pelo hash do ucode (como o Skate 3 fez para vários efeitos).

### 8.5 O que fica no caminho emulado
Só o que o nível D3D não cobre: escrita direta de PM4 pelo jogo, memexport, leituras do EDRAM
sem resolve. Detetar com a captura: se aparecer PM4 que não veio de uma função D3D substituída,
é um destes casos.

---

## 9. Fase 4 — afinar por gama de GPU

- **Adreno 7xx/8xx:** Turnip recente (RB2: K11MCH1 v26.0.0-rc08 nos 6xx/7xx; StevenMXZ Gen8 nos
  8xx; Unleashed: `TU_DEBUG=sysmem,nobin` no 8 Elite). Driver Qualcomm do sistema como reserva.
- **Adreno 6xx:** driver do sistema (Vulkan 1.1): std140 estrito, no máximo 4 descriptor sets,
  shaders pequenos, sem dynamic rendering. Turnip só se o jogo não depender de storage buffers
  grandes.
- **Mali:** driver do sistema; sem BC; Valhall (G57, G610, G710…) sim, Bifrost não. Preset de
  desempenho: resolução interna 50–60%, sem sombras/AO/HDR extra, distância de desenho reduzida
  (Skate 3: 512x288 chega ao G57).
- **Opções por omissão:** sem MSAA, anisotrópico 4x, resolução por gama de aparelho, FSR1 ou
  bilinear no presenter.

---

## 10. Checklist de um port novo

1. Recompilar o jogo (ReXGlue codegen), confirmar que corre em PC.
2. Shell Android deste repo (launcher, drivers, logs, captura) + Fase 0 completa.
3. Primeiro arranque no aparelho mais fraco do alvo; tirar log de 5 min e duas capturas (menu e
   jogo).
4. Mapa do frame (`dfc_parse.py`), tempo por draw, shaders desmontados.
5. Decidir o caminho (secção 4). Se nativo: tabela de funções D3D (8.1) antes de escrever código
   de render.
6. Renderizador nativo com o caminho emulado como reserva e um interruptor para comparar os dois
   lado a lado.
7. Afinar por gama (secção 9); medir sempre com o mesmo cenário e o mesmo aparelho.

---

## 11. Referências
- Unleashed Recompiled Android: https://github.com/SansNope/UnleashedRecomp-Android
  (`UnleashedRecomp/gpu/video.cpp`, `tools/XenosRecomp`, `docs/GRAPHICS_DRIVERS.md`, `CHANGELOG.md`).
- Skate3-Mobile: https://github.com/Buku313/Skate3-Mobile
  (`src/skate3_native_*.cpp`, `src/native/shaders/`, `src/skate3_android_defaults.cpp`).
- RB2-Android: https://github.com/moddedrounds/RB2-Android
  (`patches/rexglue-sdk-android/`, `PROGRESS.md`, `ANDROID.md`).
- Este repositório: `docs/ESTADO.md` (histórico com números), `docs/PLANO_RENDER_NATIVO.md`,
  `patches/` (0001–0035), `tools/dfc_parse.py`.
