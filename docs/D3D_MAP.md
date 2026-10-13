# Mapa da biblioteca D3D do XDK no `yae3_xenon.xex` (USA)

Fonte: `tools/d3d_map.py` e `--dump-range` sobre o `generated/` real (2026-10-13, 22:00–22:05):
11 389 funções; biblioteca D3D em **0x8222D4A8–0x8224EFF8** (594 funções). O jogo chama-a
quase só a partir de uma camada própria em **0x820A3000–0x820AB800** (um "GfxDevice" do
jogo: ~40 funções pequenas, cada uma a embrulhar uma ou duas chamadas D3D) e das rotinas de
texturas em 0x8225xxxx–0x8228xxxx. O dispositivo D3D é sempre `r3` (o jogo guarda-o em
`8(r31)` do seu objeto).

Confiança: **A** confirmado (imports/pacotes/argumentos claros); **B** forte; **C** hipótese.

## Descoberta principal: o device guarda uma sombra completa dos registos da Xenos

Os `SetRenderState_*` do XDK não emitem PM4: escrevem o valor no campo do registo dentro do
device e ligam bits "dirty" em `device+16/+24/+32/+40` (64 bits cada). No desenho, a função
`sub_8223E7C8(dev, máscara, registo_base, ponteiro_sombra)` despeja os blocos sujos como
pacotes type-0. Blocos vistos no DrawIndexedVerticesUP (`sub_8223BF08`):

| Sombra no device | Registos | Conteúdo |
|---|---|---|
| +10368 | 0x2000… | RB_SURFACE_INFO, RB_COLOR_INFO, RB_DEPTH_INFO (+10376 = 0x2002) … |
| +10444 | 0x2100… | VGT / PA (janela, scissor) |
| +10528 | 0x2180… | SQ_PROGRAM_CNTL, SQ_CONTEXT_MISC … |
| +10548 | 0x2200… | RB_DEPTHCONTROL (+10548), RB_BLENDCONTROL0 (+10552), RB_COLORCONTROL (+10556), … |
| +10596 | 0x2280… | PA_SU_SC_MODE_CNTL, PA_CL_VPORT_* … |
| +10680 | 0x2300… | RB_COPY_* (resolve) |
| +10832 | 0x2380… | PA_SU_POLY_OFFSET_* (depth bias) |
| +10112 | 0x4900… | constantes bool/loop |
| +1920 | 0x4000… | constantes ALU do VS (256 × vec4) |
| +6016 | 0x4400… | constantes ALU do PS |
| +(48+i)×24 | fetch i | fetch constants de textura (6 dwords) |
| +(3209+…)/+1780 | fetch 95-… | fetch constants de vértices (stream 0 = fetch 95) |

Consequência para o renderizador nativo: **não é preciso apanhar os ~60 Set\***. No momento
de cada draw, a sombra do device já tem o estado completo em formato Xenos (o mesmo que o
Xenia descodifica). Basta apanhar draws, clears, resolves, present e criação/lock de
recursos, e ler a sombra + shaders + fetch constants nesse instante.

## API usada pelo jogo

### Desenho (o caminho de todos os ~400 draws/frame)
| Função | Nome | Conf. | Notas |
|---|---|---|---|
| sub_8223BF08 | **DrawIndexedVerticesUP** | A | 1 chamador (820A5320); índices e vértices vêm em ponteiros da RAM do jogo; flush dos blocos sujos por `8223E7C8`, shaders por `8223F8F8`, constantes por `8223E438` |
| sub_8223BEC0 | **DrawVerticesUP** (dev, prim, count, pData, stride) | A | copia `count×stride` (memcpy `822F56C0`) para o espaço devolvido por `8223BA00`; chamadas vistas: RECTLIST (8), 3 vértices, stride 16 |
| sub_8223BA00 | BeginVertices (interno) | B | reserva espaço no command buffer e escreve DRAW_INDX |

O jogo **não usa** vertex/index buffers para desenhar: tudo passa por UP. Bate com a captura
(315 IBs diferentes em 318 draws: são cópias dentro do command buffer).

### Alvos, clear, resolve, present
| Função | Nome | Conf. | Notas |
|---|---|---|---|
| sub_82230A60 → 822303F8 | SetRenderTarget (dev, idx, surface) | B | Present chama-a com o backbuffer (+15236) |
| sub_82230788 | SetDepthStencilSurface | A | guarda em +12832, escreve RB_DEPTH_INFO (+10376) |
| sub_8223AE78 | **Clear** (dev, count, rects, flags, color, z, stencil) | A | flags vistas 15 (cor), 48 (z+stencil), 32 |
| sub_8223DF08 | **Resolve** (dev, flags, srcRect, destTex, destPoint, level, slice, clearColor, z, stencil, params) | A | corpo em `8223CDB0` |
| sub_8223B1C8 | BeginTiling (flags, count, rects, clearColor, z, stencil) | B | chama os internos do Clear, guarda retângulos |
| sub_8223B538 | EndTiling (flags, rects, destTex, clearColor, z, stencil, params) | B | chama Resolve 2× |
| sub_8223B058 | SetPredication / sincronização de tiling | C | chamada antes/depois dos tiles |
| sub_82239AE8 | **Present** (Resolve para o front buffer + Swap) | A | corpo do Swap em `82239380` (VdSwap) |
| sub_8222FA58 | SetViewport (dev, pViewport) | A | +13052..+13064 |
| sub_822302B8 | SetViewport com flag (+12288) | C | |

### Shaders e constantes
| Função | Nome | Conf. |
|---|---|---|
| sub_82233EA8 / sub_82234090 | CreateVertexShader / CreatePixelShader (ucode) | B |
| sub_822336B8 / sub_822338C0 | SetVertexShader (+13068) / SetPixelShader (+13072) | B |
| sub_822334D0 / sub_822335A8 | SetVertexShaderConstantF / SetPixelShaderConstantF (start, data, count) | A |
| sub_82233BE0 | CreateVertexDeclaration | B |
| sub_82233AD8 | (+12240, dirty de shader) — SetVertexDeclaration? | C |

### Recursos
| Função | Nome | Conf. |
|---|---|---|
| sub_82237ED8 | CreateTexture | B |
| sub_82237FF8 | CreateCubeTexture/CreateSurface | C |
| sub_82237C88 / 82237D60 / 82237E28 | GetSurfaceLevel / GetCubeMapSurface / GetVolumeLevel | B |
| sub_82237D40, 822381C8, 82238238 | LockRect / LockBox de texturas | B |
| sub_822362F8 / sub_82236310 | lock da memória (espera a fence do recurso, `82234260`) | B |
| sub_822345A8 / sub_82234680 | CreateVertexBuffer / CreateIndexBuffer | B |
| sub_82234670 / sub_82234730 | VertexBuffer/IndexBuffer Lock | B |
| sub_82235788 / sub_822357D8 | VertexBuffer/IndexBuffer Unlock (invalida cache da GPU) | B |
| sub_822344C8 / sub_82235228 | AddRef / Release | A |
| sub_82238258 | **SetTexture** (dev, sampler, tex) → fetch constant em +(48+s)×24 | A |
| sub_8222FB50 | SetStreamSource (dev, stream, vb, offset, stride) | A |
| sub_8222FC70 | SetIndices (+12812) | A |
| sub_82236328, 82236448, 82236568, 822368E8, 82237010, 82237710, 822361C0, 82236258, 822341D0, 82237C78 | XG (layout/tamanho/formato de texturas, GetLevelDesc, GetLevelCount) | B |

### Device
| Função | Nome | Conf. |
|---|---|---|
| sub_822360E8 | Direct3D_CreateDevice (corpo em 82246908) | A |
| sub_82235C98 | Release do device | B |
| sub_822327A0 | BlockUntilIdle | B |
| sub_8222FDC8 | SetGammaRamp (1536 B) | A |
| sub_82235D20 | FlushHiZStencil / evento de cache | C |
| sub_8222FD88, 82238EB8, 8222DE88, 8222F010, 82240F40, 82240278 | configuração do device (chamadas no arranque, 820A7980) | C |

### SetRenderState_* (escrevem a sombra; não precisam de hook)
8222D790, 8222D7F0, 8222D828, 8222D8B8, 8222D948, 8222D9D8, 8222DAD8, 8222DB48, 8222DBB8
(blend: +12280/12284 → RB_BLENDCONTROL0–3 em +10552/+10584/+10588/+10592), 8222DC50,
8222DCB0, 8222DEF8 (ZWriteEnable), 8222DF28 (ZFunc), 8222DF60 (StencilEnable),
8222DEB8 (ZEnable), 8222DFD8, 8222E008, 8222E040, 8222E078 (stencil func/ops), 8222E178,
8222E198, 8222E1B8 (stencil ref/masks, +10497–10499), 8222E298 / 8222E360 (depth bias),
8222E458, 8222E9C0, 8222ED18, 8222EEB8, 8222EEE8, 8222EF50, 8222EF80. Mais 215 funções
pequenas sem chamadores diretos (tabela de setters).

### Internas úteis
| Função | O que é |
|---|---|
| sub_822324F0 | reservar espaço no command buffer |
| sub_82231110 / 82235918 | espera por espaço no ring buffer (hook existente) |
| sub_8223E7C8 | despejar um bloco de registos da sombra (type-0) |
| sub_8223E438 / 8223EB60 | despejar constantes ALU |
| sub_8223EA08 | despejar fetch constants |
| sub_8223F8F8 | carregar shaders (IM_LOAD) e GPRs |
| sub_8223CDB0 | corpo do Resolve |
| sub_82239380 | corpo do Swap (VdSwap) |
| sub_822F56C0 | memcpy |
| sub_820A2978 | alocador do jogo |

Falso positivo do scanner: `sub_82089F58` (122 chamadores, fora do intervalo) é o alocador de
memória do jogo, não uma espera da GPU.

## Plano de hooks do renderizador nativo (mínimo)
1. **DrawIndexedVerticesUP** e **DrawVerticesUP**: ler sombra + shaders (+13068/+13072) + fetch
   constants + dados UP; desenhar em Vulkan; **não** chamar o original.
2. **Clear**, **Resolve**, **BeginTiling/EndTiling**, **SetRenderTarget/DepthStencil**: alvos
   do host; resolve = a textura passa a apontar para a imagem do alvo.
3. **Present**: apresentar e chamar só o necessário para o vblank do jogo.
4. **CreateVertex/PixelShader**: registar o ucode (hash) para os shaders traduzidos offline.
5. **CreateTexture / LockRect / Unlock** (e as rotinas XG): carregar texturas para o host.
6. Garantir que nenhum lock fica à espera de fences da GPU emulada (o ring deixa de correr).
