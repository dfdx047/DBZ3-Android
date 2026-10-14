# Resumo para continuar numa conversa nova (2026-10-13, 21:30)

Colar este ficheiro (ou apontar para ele) no início da próxima conversa.

## O projeto
- Port para Android do **DBZ Budokai 3 HD Collection** recompilado (ReXGlue SDK v0.10, projeto
  original novapowers0/dbz-budokai-3-hd-collection em `external/dbz3`), com o launcher Compose
  **Dragon Fury** (pacote `com.dfdx047.dragonfury`).
- Repositório `dfdx047/DBZ3-Android`, branch `fase2-dragon-fury`. O utilizador compila em
  `~/AndroidStudioProjects/DBZ3-Android` (CachyOS, shell fish).
- Comando de build (dar sempre o comando completo):
  ```
  cd ~/AndroidStudioProjects/DBZ3-Android && git pull origin fase2-dragon-fury && bash tools/apply-sdk-patches.sh && ./gradlew assembleRelease
  ```
- Respostas em português; comandos git em inglês, curtos; comandos sempre completos.
- Aparelhos de teste e logs: **Retroid Pocket 5** (SD865, Adreno 650, driver do sistema
  Vulkan 1.1) e **Anbernic RG Cube** (Unisoc T820, Mali-G57). O RG Cube ainda não foi testado com
  os builds recentes.
- Objetivo: 60 fps desenhados (sem frameskip) no 865 e no Mali-G57, sem depender de Turnip; a
  correr também em 6/7 Gen 1, 695, 480+. Port completo: incluir **Tag Team e online**.

## Onde parámos
- Caminho emulado (Xenia) levado ao limite com os patches 0024–0035 (render targets diretos,
  atalhos de resolve de cor e profundidade, captura de frames, tempo por draw, shaders
  especializados). Medição final no RP5 (log 21:20): **45–52 fps em luta, GPU 19–22 ms**;
  desenho ~9 ms, resolves 8–10 ms (17–21 por frame), GPU a 99%. Não chega a 60.
- **Decisão (do utilizador): fazer o renderizador nativo.** Turnip no Adreno 650 está fora
  (texturas sempre erradas).
- Ainda por confirmar: se os especiais (Kamehameha, transformação, modo vermelho) já não ficam
  pretos com a eliminação de restauros desligada (0034).

## Documentos a ler primeiro (em `docs/` e no projeto claude.ai)
- `GUIA_PORT_XBOX360_ANDROID.md` — método geral, estudo do Unleashed Android, Skate3-Mobile e
  RB2-Android, porque a emulação é cara, árvore de decisão, checklist.
- `PLANO_RENDER_NATIVO.md` — plano e decisão do render nativo.
- `ESTADO.md` — histórico completo com números de cada teste.

## O render nativo a fazer (nível D3D, como o Unleashed Recompiled Android)
1. **Mapear as funções D3D do XDK no `yae3_xenon.xex`** (CreateDevice, CreateTexture,
   Lock/UnlockTextureRect, Create/Lock/Unlock Vertex/IndexBuffer, CreateSurface, SetRenderTarget,
   SetDepthStencilSurface, Clear, SetViewport, SetScissorRect, SetTexture, SetStreamSource,
   SetIndices, SetVertexDeclaration, Create/Set Vertex/PixelShader, SetRenderState/
   SetSamplerState, constantes de shader, DrawPrimitive, DrawIndexedPrimitive, DrawPrimitiveUP,
   Resolve/StretchRect, Present/Swap). Referências de nomes: Unleashed
   `UnleashedRecomp/gpu/video.cpp` (42 `GUEST_FUNCTION_HOOK`) e Skate3-Mobile
   `src/skate3_native_render.cpp` (SetPending_AluConstants, SetPending_RenderStates,
   DrawIndexedVertices…). Formas: trace de entradas de função a partir de quem escreve os pacotes
   PM4 (DRAW_INDX, resolves, SET_CONSTANT) nas capturas; padrões de código entre jogos com o mesmo
   XDK; os docs do projeto original (`docs/07_ports/ESTRUCTURA_DIBUJO_HD.md`, `SESION_DRAW_SEMANTICS`).
2. **Arquitetura:** hooks `REX_FUNC(sub_…)` que põem comandos numa fila → thread de render Vulkan
   1.1 (sem extensões obrigatórias); caminho emulado desligado enquanto o nativo está ativo.
3. **Recursos:** texturas criadas no host e preenchidas no Unlock (untile/endian no CPU, cache,
   BC → RGBA8/RGB565 no Mali); vertex/index buffers do host com vertex fetch nativo; render
   targets do host; **resolve = a textura passa a ser a imagem do render target (sem cópia)**.
4. **Shaders:** o jogo usa só ~33. Traduzir offline (XenosRecomp do Unleashed, adaptado) para
   SPIR-V embutido; rever à mão os pesados (desfoque PS 4DFCBC49057E5721, FXAA PS
   F5F4D761A981B87D, PS 555EBAAEF8F61839). Os `.bin` de ucode saem de
   `tools/dfc_parse.py --dump-shaders`.
5. **Frame do jogo (das capturas):** 2 clears MSAA 4x pitch 640; cena 1280x720; por lutador
   resolve de profundidade → 1EC2F000, de cor → 1F35F000, silhueta 512x512 → 1E2D3000 e draws de
   restauro (PS 6E57A6E305D5D69B cor, ADCA7FBBCBF53B57 profundidade); cadeia de brilho
   1280→320x184→160x96→80x48; HUD; frontbuffer 1F6F8000. ~400 draws/frame.
6. Interruptor nativo/emulado no launcher para comparar; medir com as estatísticas
   `dbz3: perf` e o tempo por draw.

## Ferramentas já feitas
- Captura de frames no menu do jogo ("Capturar 2 frames") → `logs/capture/*.dfc` (+
  `.timing.txt` com tempo de GPU por draw); "Exportar logs" leva tudo.
- `tools/dfc_parse.py` (mapa de passes, `--draws`, `--regs`, `--dump-shaders`, `--dump-mem`).
- Tradutor de shaders compilado no PC (desmontador de ucode + SPIR-V do Xenia) e SPIRV-Tools para
  validar/simular — receita no `GUIA_PORT_XBOX360_ANDROID.md`, secção 6.

## Pendentes paralelos
- Tag Team e online: atualizar `external/dbz3` para o upstream recente (`src/tagteam*.cpp`,
  `src/ingame/*_panel.cpp`, `src/net/`), regenerar `generated/` no PC do utilizador, compilar com
  `DBZ3_FULL_SOURCES`, trocar UPnP do Windows (miniupnpc ou STUN + porta manual) e clipboard.
- Testes no RG Cube (Mali-G57).
- Commits com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` e a linha
  `Claude-Session` da sessão.
