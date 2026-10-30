# Renut nos Budokai HD — mapa de hooks

Budokai 3 HD (`yae3_xenon.xex`) e Budokai 1 HD (`yae1_xenon.xex`): XDK 21119, o mesmo Direct3D. O device tem o
layout do MGR (XDK 21173), não o do Burst Limit/N&B: alocação de 24704 bytes (0x6080) e os mesmos offsets privados
(11024 FenceCompletion, 11036 FenceTag, 11068/11069 flags, 12812 IndexBuffer, 12904 StreamStrides, 13600 InKickOff,
13844 VerticesRingEnd, 14000 PresentationInterval, 16968 SwapCount, 24392 BehaviorFlags; DrawIndexed lê as
constantes em 1920/6016/10112). Verificado no código gerado do B3 (ex.: `sub_82246908` escreve 21968, 21972,
21996/22000 e inicializa as secções críticas em 15348/15376; `sub_822360E8` guarda o BehaviorFlags em 24392;
`sub_82238EC0` lê o intervalo em 14000).

Por isso o patch `patches/renut/0200` desfaz o layout do BL do `0100` (volta aos valores do MGR) e troca os
endereços por macros de `gpu/d3d/budokai_hook_map.h` (gerado por `tools/renut/budokai_hook_map.py` a partir dos
dois executáveis; guarda = FNV-32 dos 64 primeiros bytes de cada função, iguais aos do mapa do Optxbox
`maps/dbz-budokai-hd/yae{3,1}_hook_map.h`). O Renut recusa um executável diferente em vez de crashar.

| Papel | MGR | B3 | B1 |
|---|---|---|---|
| Direct3D_CreateDevice | 826B2418 | 822360E8 | 821C5228 |
| InitializePresentationParameters | 826C0C48 | 82246688 | 821D57C8 |
| InitializeApiState | 826C0278 | 82245CB8 | 821D4DF8 |
| D3DDevice_InsertCallback | 826B59B0 | 82232818 | 821C1958 |
| SynchronizeToPresentationInterval | 826B3050 | 82238EC0 | 821C8000 |
| KickOff | 826B5688 | 822324F0 | 821C1630 |
| KickOffSegment | 826B5348 | 822321B0 | 821C12F0 |
| BeginRingBig | 826B58C0 | 82232728 | 821C1868 |
| RingBufferDeviceAllocate | 826B4078 | 82230EE0 | 821C0020 |
| AllocateInlineData | 826B44C0 | 82231328 | 821C0468 |
| NewSegment | 826B45A8 | 82231410 | 821C0550 |
| SetRingBufferParameters | 826B5A08 | 82232870 | 821C19B0 |
| D3DDevice_Swap (partilhado com a cola) | 826B3510 | 82239380 | 821C84C0 |
| BlockOnFence | 826B42A8 | 82231110 | 821C0250 |
| CBlocker::Check (partilhado com android_hooks.cpp) | 826B1C88 | 82235918 | 821C4A58 |
| IsFencePending | 826B57C8 | 82232630 | 821C1770 |
| DrawIndexedVertices (partilhado com a cola) | 826BCD40 | 8223BF08 | 821CB048 |
| BeginVertices | 826BC468 | 8223BA00 | 821CAB40 |
| WindowScissor | 826B5FC8 | 8222D4A8 | 821BC5E8 |
| ClearF | 826BC260 | 8223AD40 | 821C9E80 |
| Resolve | 826BB0D0 | 8223DF08 | 821CD048 |
| XGRegisterVertexShader | 826ACD00 | 82233F98 | 821C30D8 |
| XGRegisterPixelShader | 826AC3D8 | 82233680 | 821C27C0 |
| CreateVertexDeclaration | 826AC938 | 82233BE0 | 821C2D20 |
| D3DResource_Destroy | 826AAF80 | 82234F80 | 821C40C0 |
| XMemFree do jogo (chamado pelo D3DResource_Destroy) | 82715A90 | 820A27A8 | 8209F0C0 |
| Dados: qword global copiada para device+11008 | 834FB7E0 | 82379750 | 8230F890 |
| Dados: slot de importação do VdGlobalDevice | 820005F8 | 82000534 | 82000534 |

27 dos 29 hooks do MGR. Os 2 que faltam:

- **DrawVertices** (MGR 826BC928): não está ligado nestes executáveis. Prova: o cabeçalho de draw do D3D
  (`ori ...,8705` = 0x2201, o pacote DRAW_INDX) só aparece em três funções em cada jogo: BeginVertices
  (8223BA00 / 821CAB40), DrawIndexedVertices (8223BF08 / 821CB048) e o setup de hardware (82235F70 / 821C50B0, o
  buffer de comandos do sistema). O DrawVerticesUP é o invólucro de 18 instruções 8223BEC0 / 821CB000 que chama o
  BeginVertices, copia os vértices e fecha. O hook do Renut para DrawVertices fica compilado e sem endereço.
- **EngineSwapProgress**: é do motor do MGR, não do D3D; os Budokai não têm equivalente.

Diferenças em relação ao BL: os Budokai têm o Direct3D_CreateDevice e o InsertCallback próprios (no BL o LTCG
embutiu-os no código do jogo), por isso ficam os hooks do MGR tal como estão.

## Shaders

Todos no `.rdata` do executável, contíguos (B3 0x82000DF0–0x82063BB0, B1 0x82000DF0–0x82062F70): 120 contentores,
108 diferentes (34 VS + 74 PS) em cada jogo, 69 iguais entre os dois. As `.afs` do disco não têm nenhum (todas as
entradas descomprimidas e lidas). `tools/renut/extract_budokai_shaders.py` tira-os; `tools/renut-shaders.sh`
traduz (XenosRecomp: 108/108 nos dois jogos, sem erros) para `out/renut/<b3|b1>/shader_cache.cpp`.
