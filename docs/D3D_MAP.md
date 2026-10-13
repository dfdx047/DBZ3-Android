# Mapa da biblioteca D3D do XDK no `yae3_xenon.xex` (USA)

Fonte: `tools/d3d_map.py` sobre o `generated/` real (2026-10-13 22:00): 11 389 funções,
77 com sinais de GPU, biblioteca D3D em **0x8222D4A8–0x8224EFF8** (594 funções, 99 chamadas
pelo jogo, 215 sem chamadores diretos — quase todas minúsculas, 8–36 B, provavelmente os
"setters" de render/sampler state chamados por tabela).

Confiança: **A** = confirmado pelos imports/pacotes; **B** = forte pela estrutura; **C** =
hipótese a confirmar com a desmontagem (`--dump-range`) ou com o rastreio (`-Pdf.d3dTrace`).

## Internas (núcleo do command buffer)

| Função | O que é | Conf. | Evidência |
|---|---|---|---|
| sub_822324F0 | reservar espaço no command buffer (chamada por quase tudo o que escreve PM4) | B | chamada por ~40 das funções com PM4 |
| sub_82231110 / sub_82235918 | espera por espaço no ring buffer | A | perfil (66% da Main XThread), hook já existente |
| sub_82231580 | kickoff (INDIRECT_BUFFER) | B | PM4 INDIRECT_BUFFER, 6 chamadas indiretas |
| sub_82232870 | inicialização do ring buffer (parte do CreateDevice) | A | ME_INIT, VdInitializeRingBuffer, VdEnableRingBufferRPtrWriteBack |
| sub_822462D8 / sub_822464B0 | arranque/paragem dos motores de vídeo | A | VdInitializeEngines, VdSetGraphicsInterruptCallback, VdShutdownEngines |
| sub_82246908 | corpo do CreateDevice | A | chama 822462D8, 82232870, VdIsHSIOTrainingSucceeded |
| sub_82239380 | corpo do Swap | A | VdSwap, VdGetSystemCommandBuffer, VdPersistDisplay |
| sub_8224E640 | blit final do Swap (shader embutido, DRAW_INDX_2) | B | só alcançado a partir do Swap |
| sub_8223F8F8 | carregar shaders pendentes (IM_LOAD ×5) + constantes ALU/fetch | B | IM_LOAD, SET_CONSTANT2, KeBugCheck |
| sub_8223E438 | enviar constantes ALU (SET_CONSTANT2) | B | 8 chamadores internos |
| sub_8223F708 | LOAD_ALU_CONSTANT (constantes a partir de memória) | B | |
| sub_8223BA00 / sub_8223BF08 | caminhos de desenho (DRAW_INDX ×2, EVENT_WRITE_EXT) | B | chamam 8223F8F8, 8223E438 |
| sub_8223CDB0 | resolve (4,4 KB: EVENT_WRITE ×4, fetch constants, clip) | C | alcançado por 8223DF08 e 8223B538 |
| sub_82239C00 / sub_8223A120 | clear/cópia com quad (DRAW_INDX_2, RB_SURFACE_INFO) | C | alcançado por 8223B1C8 e 8223AE78 |
| sub_82234898 / sub_82234740 | blits internos com shaders embutidos (IM_LOAD_IMMEDIATE + DRAW_INDX_2) | B | |
| sub_8224D530 / sub_8224D6B8 | alocação física da biblioteca | A | MmAllocatePhysicalMemoryEx |

## API pública (chamada pelo jogo) — candidatos

| Função | Candidato | Conf. | Evidência |
|---|---|---|---|
| sub_822360E8 | Direct3D_CreateDevice | B | único caminho público até 82246908 |
| sub_82239AE8 | D3DDevice_Swap / Present | B | único caminho público até 82239380 (VdSwap) |
| sub_8223BEC0 | D3DDevice_Draw(Indexed)Vertices (72 B, 5 chamadores) | C | chama 8223BA00 (DRAW_INDX) |
| sub_8223BF08 | o outro Draw (1,1 KB, 1 chamador) | C | DRAW_INDX ×2 |
| sub_8223DF08 / sub_8223B538 | D3DDevice_Resolve (wrapper e variante) | C | alcançam 8223CDB0 |
| sub_8223AE78 / sub_8223B1C8 | D3DDevice_Clear / ClearF | C | alcançam 82239C00 |
| sub_82235D20 | InsertFence / InsertCallback | C | EVENT_WRITE, 1 chamador |

Os Set* (SetTexture, SetStreamSource, SetRenderState, Set*Shader, constantes) no XDK só
escrevem no estado do device e marcam bits "dirty"; o PM4 sai no draw seguinte
(8223F8F8, 8223E438). Por isso não têm pacotes próprios: identificam-se pelo que escrevem
no device e pelos argumentos nos sítios de chamada (`--dump-range` mostra-os).

## Próximo passo
`python3 tools/d3d_map.py --dump-range` → `perf/d3d/d3d_dump.txt` (desmontagem do intervalo
inteiro + argumentos nos sítios de chamada do jogo). Com isso fecham-se as linhas C e os Set*.
