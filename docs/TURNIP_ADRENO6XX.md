# Turnip em Adreno 6xx — o que foi preciso (para levar a outros ports ReXGlue/Xenia)

Aparelho de teste: Retroid Pocket 5 (SD865, Adreno 650). Drivers: Turnip R6, R8, Rutabaga,
Mesa 26.3. Com o driver do sistema tudo funcionava; com o Turnip o jogo saía sem texturas e a
GPU caía (VK_ERROR_DEVICE_LOST). Turnip em Adreno 7xx/8xx não tinha estes problemas.

## Sintomas e causas

| Sintoma | Causa | Patch (DBZ3) |
|---|---|---|
| GPU cai no ecrã título | timestamps/queries de oclusão e otimizações de passes | 0065 (modo seguro) |
| Geometria certa, texturas pretas, sem HUD | leituras **vetoriais** (uvec4) de SSBO `NonWritable` nos compute shaders de carga de texturas: o ir3 usa `isam` e o resultado sai errado | 0069 (carga no CPU), 0070 (sem isam) |
| HUD/menus em falta | resolves "só para a textura" (sem escrever a memória do guest) | 0072 (resolve escreve sempre a memória) |
| maxStorageBufferRange 128 MB | limite das vistas de 8 bits; com a memória partida em 4 buffers a imagem saía errada | `dbz3_vk_turnip_single_shared_buffer` (um buffer de 512 MB) |

## Correções a portar

1. **Deteção**: `driverID == VK_DRIVER_ID_MESA_TURNIP` e (`deviceID >> 24 == 6` ou nome com
   "Adreno (TM) 6xx") → `Dbz3IsTurnipA6xx()` / `Dbz3TurnipA6xxSafe()` (vulkan_device.cpp).
2. **Memória do guest num só storage buffer de 512 MB** no Turnip quando o driver diz < 512 MB.
3. **Modo seguro** (0065): sem timestamps da GPU, queries de oclusão simuladas, renderArea inteira,
   sem descarte da cor; `EndRenderPass()` antes de `vkCmdEndQuery`.
4. **BC descomprimido** e sem `VK_EXT_shader_stencil_export` (0066).
5. **Caminho original dos sinais de textura / LOD explícito** (0067) se o port tiver essas
   otimizações.
6. **Carga das texturas no CPU** (0069, `VulkanTextureCache::Dbz3CpuLoadTexture`): untile
   (`texture_conversion::Untile` + `GetPackedMipOffset` para a cauda de mips), troca de endian,
   descompressão DXT1/3/5/DXN/DXT5A/565/4444, staging buffer + `vkCmdCopyBufferToImage`. Só
   2D simples; páginas escritas pela GPU (`SharedMemory::Dbz3RangeHasGpuWrittenPages`) ficam
   na GPU.
7. **Resolves escrevem sempre a memória** (0072): sem `dbz3_direct_resolve_skip_memory` nem
   render-into-texture no Turnip A6xx.
8. ~~Sem isam~~ (0070, revertido em 0073: não era a causa e o isam é mais rápido): em `CreateComputePipeline` (versão com SPIR-V), trocar `NonWritable`
   (24) por `Coherent` (23) em OpDecorate/OpMemberDecorate quando `Dbz3TurnipA6xxSafe()`.
9. **Diagnóstico**: registo das operações de GPU antes de um DEVICE_LOST (`dbz3_gpulog.h`), e a
   linha "no caminho da GPU: <motivo>" (0071).

## Do lado do launcher

- Nível de compatibilidade automático por driver (`TurnipCompat`), que sobe quando a GPU cai.
- Em Adreno 6xx o launcher usa o driver do sistema, a não ser que se ligue "Usar o Turnip mesmo
  assim" (aba Drivers). Quando o Turnip estiver confirmado, este interruptor pode passar a ligado.

## Desempenho

- **Não usar `TU_DEBUG=sysmem` por omissão.** Desliga o GMEM (render por tiles do Adreno) e foi o
  maior custo no RP5. Os crashes que levaram a usá-lo tinham outras causas (ver acima).
- Ladder automático (sobe quando a GPU cai): 0 nada, 1 nolrz/noubwc + BC, 2 + sysmem,
  3 + passes simples/sync total, 4 + memória do guest em 4 buffers.
- Efeitos pós (bloom/DOF/FXAA) pesam bastante num Adreno 650; testar desempenho com eles
  desligados e ligados em separado.

## Turnip vs driver do sistema (para os próximos ports)

- O driver da Qualcomm muda de aparelho para aparelho (versão, extensões, bugs); o Turnip é o
  mesmo código em todos os Adreno 6xx/7xx/8xx e o utilizador escolhe a versão.
- Recomendação: nos Snapdragon, desenvolver e testar primeiro com o Turnip; o driver do sistema
  fica como alternativa. Nos Mali só há o driver do fabricante.
