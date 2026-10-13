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
