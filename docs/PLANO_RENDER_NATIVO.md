# Plano: renderizador nativo (port completo para todas as GPUs)

Decisão de 2026-10-13: o caminho de GPU emulado (Xenia: EDRAM, transferências de render
targets, resolves, untile de texturas em compute) não chega à compatibilidade e ao desempenho
do Skate3-Mobile em Mali/Adreno antigos. Medido: Adreno 650 com o driver da Qualcomm ~39 ms de
GPU por frame; com Turnip a geometria só fica certa forçando um storage buffer fora da
especificação, e as texturas continuam erradas. O jogo passa a ser desenhado por um
renderizador próprio, como no Skate3-Mobile (Buku313) e no UnleashedRecomp.

## O que fica
- Jogo recompilado, runtime ReXGlue, áudio, ficheiros, entrada, launcher Dragon Fury,
  controlos por toque, menu no jogo, drivers, logs, texture packs.
- O caminho emulado fica como reserva (menus/cenas ainda não cobertas) até o nativo cobrir tudo.

## Como funciona (alvo)
1. Hooks nas funções de desenho do jogo (recompiladas, `REX_FUNC(sub_...)`): o jogo continua a
   correr a lógica e a montar a cena; o hook lê os dados (malhas, texturas, materiais, ossos,
   câmara, luzes, efeitos) da memória do guest.
2. Um renderizador Vulkan escrito para o Budokai 3 (shaders próprios: cel shading, contorno,
   rim light HD, partículas/ki, HUD 2D) desenha num render target normal, sem EDRAM.
3. O caminho emulado deixa de desenhar e de fazer resolves enquanto o nativo está ativo
   (como `native_render_suppress_emulated_draws` no Skate3).
4. Texturas: untile/endian no CPU uma vez, em cache; BC nativo quando a GPU tem, senão RGBA8.
5. Alvos: Vulkan 1.1 sem extensões especiais (Mali, Adreno 6xx com driver da Qualcomm, Turnip
   opcional). Depois, se fizer falta, um caminho GLES 3.

## Como se vai fazer (decisão de 2026-10-13, 10:11)
Sem fases de teste intermédias: escreve-se o renderizador completo e testa-se a build pronta.
Aparelhos de teste e logs a partir de hoje: **Retroid Pocket 5** (Adreno 650) e **Anbernic RG
Cube** (Unisoc T820, Mali-G57). O que o renderizador precisa de entrada é uma captura do que o
jogo manda à GPU; para isso a build atual tem o botão "Capturar 2 frames" no menu do jogo
(patch 0027: registos, draws, resolves, shaders e memória lida, em `logs/capture/*.dfc`, que vai
no "Exportar logs"). `tools/dfc_parse.py` imprime o mapa de passes de uma captura. Pedido: uma
captura no RP5 numa luta e outra no menu principal; a partir daí escreve-se o renderizador.

## Trabalho (ordem interna, sem builds intermédias)
- F0 — Mapa do render do jogo: que funções desenham personagens, cenário, efeitos, HUD;
  formato dos buffers (AWO/AWG, já estudados no projeto original em
  `docs/07_ports/ESTRUCTURA_DIBUJO_HD.md` e `SESION_DRAW_SEMANTICS`); passes por frame
  (há uma cadeia de resolves 1280x720 → 320x184 → 160x96 → 80x48, provavelmente brilho).
  Captura de draws no aparelho com o log já existente.
- F1 — Prova de conceito: um lutador num cenário desenhado pelo renderizador nativo, lado a
  lado com o emulado para comparar.
- F2 — Cena de combate completa (2 lutadores, cenário, efeitos, HUD) com o emulado desligado.
- F3 — Menus, cenas, ecrãs de carregamento; desligar o emulado em todo o lado.
- F4 — Afinação para GPUs fracas (resolução interna, LOD, efeitos opcionais), Mali T820,
  SD695, 865, 8 Gen x.

## Em paralelo: Tag Team e online
O projeto original (novapowers0, commit 41f03b2 de 2026-10-12) já tem `src/tagteam*.cpp`,
`src/ingame/*_panel.cpp` e `src/net/` (lockstep UDP com atraso fixo, código de sala, STUN,
UPnP do Windows). Passos: atualizar `external/dbz3`, regenerar `generated/`, compilar com
`DBZ3_FULL_SOURCES=ON`, trocar o UPnP do Windows (miniupnpc ou só STUN + porta manual) e o
clipboard por equivalentes Android. A versão online compara a data de compilação: para jogar
Android contra PC, as duas compilações têm de vir do mesmo código e ter a mesma verificação.

## Decisão de 2026-10-13, 21:17
O build com o patch 0035 (shaders especializados) é o último teste do caminho emulado. Se não
chegar a 60 fps estáveis no RP5 (driver do sistema) e não correr bem no RG Cube, passa-se ao
renderizador nativo **ao nível D3D**, como o Unleashed Recompiled Android (hooks nas ~42 funções
da biblioteca D3D do XDK, fila de comandos para uma thread de render Vulkan, resolves sem cópia,
shaders do jogo traduzidos offline). É o método descrito em `docs/GUIA_PORT_XBOX360_ANDROID.md`,
pensado para ser reutilizado noutros jogos de Xbox 360. Os hooks ao nível do motor (como o
Skate 3) ficam só para o que o nível D3D não cobrir.

## F0.1 — Mapa das funções D3D (ferramentas, 2026-10-13 22:00)
O `yae3_xenon.xex` e o `generated/` só existem no PC do utilizador, por isso o mapa faz-se lá:
1. `python3 tools/d3d_map.py` lê `external/dbz3/generated/*.cpp` (o codegen deixa a desmontagem
   PowerPC em comentário), segue constantes em registos (lis/ori/li/addi) até aos `stw` e regista
   os cabeçalhos PM4 type-3 (DRAW_INDX, DRAW_INDX_2, SET_CONSTANT e o tipo/registo do dword
   seguinte, IM_LOAD, LOAD_ALU_CONSTANT, EVENT_WRITE, VIZ_QUERY...), escritas type-0 de registos
   (RB_COPY_*, RB_SURFACE_INFO, PA_CL_VPORT...), imports do kernel (VdSwap, VdInitializeRingBuffer,
   XPhysicalAlloc...) e o grafo de chamadas. Âncoras: `sub_82235918`/`sub_82231110` (espera por
   espaço no ring buffer, já conhecidas do perfil). Resultado em `perf/d3d/d3d_map.md` (tabela
   com o intervalo provável da biblioteca D3D, funções públicas = chamadas só de fora do
   intervalo, e sugestões de nome) e `perf/d3d/d3d_map.json`.
   `--dump sub_XXXXXXXX` imprime a desmontagem de uma função para confirmar à mão.
2. Opcional, se o estático não chegar: `python3 tools/d3d_trace_gen.py` gera
   `app/src/main/cpp/d3d_trace_hooks.cpp` (um `REX_HOOK_RAW` por candidata: chamadas por frame +
   argumentos r3–r10/f1–f4/lr) e `./gradlew assembleRelease -Pdf.d3dTrace=true` compila-os;
   numa luta escreve `logs/d3d_trace.log` (vai no "Exportar logs"). Com ~400 draws/frame, a
   função chamada ~400×/frame que escreve DRAW_INDX é o DrawIndexedVertices, etc.
3. Com o `d3d_map.md` (e o trace, se houver) fecha-se a tabela endereço → função D3D em
   `docs/D3D_MAP.md`, e só então se escrevem os hooks do renderizador nativo.
