// Opções gráficas do MGR no Renut (menu Desempenho): sombras e brilho, ligados ou desligados ao vivo. Chamado pelo
// Renut (patch renut/0016) na thread de render.
//
// Mapa do frame (dumps de 24/10, swordman_renut_frame_dump; título, menus, pausa, luta, MG RAY):
// - Sombras: o sol desenha 2 cascatas num alvo de profundidade 1024x1024, cada uma resolvida para um atlas
//   1024x2048 (profundidade). Um quad de ecrã inteiro (PS 15796848…, B59EE8F6… no título) lê o atlas e a
//   profundidade linear e escreve a máscara de sombra 1280x720, limpa a branco antes; os materiais leem a máscara.
//   Só esse quad lê o atlas. Desligadas: os draws das cascatas não são desenhados (os clears e os resolves ficam) e
//   o quad passa a ser um clear a branco (1,1,1,1) do seu alvo: a máscara fica escrita e "sem sombra".
// - Brilho: cadeia em 320x176 (redução 07A5E482…, desfoque 0AD31892… e 240A635A…, soma F71DC5AC…) lida pela
//   composição final (2173EC47…) como textura 1. Desligado: cada draw da cadeia passa a ser um clear a preto do
//   seu alvo, as texturas da cadeia ficam escritas a 0 e a composição soma 0 (a imagem original).
// - SSAO, profundidade de campo e motion blur: nenhum passe nos frames do jogo (a abertura do R-00 é vídeo).
#pragma once

#include <cstdint>

namespace swordman::renut_effects {

// swordman_renut_shadows / swordman_renut_bloom (lidos a cada chamada: mudam ao vivo).
bool ShadowsOff();
bool BloomOff();

// Cada resolve: um de profundidade de um alvo quadrado >= 512 é uma cascata (alvo e atlas ficam conhecidos).
void NoteResolve(bool depth, uint32_t width, uint32_t height, uint32_t source_va, uint32_t dest_texture_va);
// O alvo de profundidade das cascatas.
bool IsShadowDepth(uint32_t depth_va);
// O atlas das cascatas.
bool IsShadowAtlas(uint32_t texture_va);
// Um pixel shader da cadeia do brilho.
bool IsBloomShader(uint64_t ps_hash);

// Dragon Fury (2026-10-30): os efeitos dos Budokai no Renut, pelas opções do launcher (dbz3_fx_fxaa, dbz3_fx_dof,
// dbz3_fx_bloom; as mesmas do caminho Xenia, command_processor.cpp, e pelos mesmos hashes de ucode).
// - FXAA e profundidade de campo: o jogo resolve o ecrã para uma textura, limpa-o (o resolve com limpeza) e volta a
//   desenhá-lo pelo efeito. Desligados: o par resolve -> efeito é aprendido no 1.º frame (o efeito desenha-se); dali
//   em diante a limpeza desse resolve sai e o draw do efeito é saltado (o ecrã fica com a cena).
// - Brilho: a cadeia (555EBAAE…), as fontes de brilho no alvo de 320 (CB0605B4… com 320 de largura) e a composição
//   (6E57A6E3… com mistura, a ler a resolução de um alvo de 160 ou menos) saltadas.
// Cada resolve (antes da limpeza): devolve se a limpeza da cor sai.
bool BudokaiResolveDropsClear(uint64_t frame, const void* surface, uint32_t surface_width, uint32_t dest_va,
                              bool clears);
// Um draw: devolve se é saltado. textures: as texturas que o PS lê (VA do fetch), count delas.
bool BudokaiSkipDraw(uint64_t frame, uint64_t ucode_hash, bool opaque, const void* target, uint32_t target_width,
                     const uint32_t* textures, uint32_t count);

}  // namespace swordman::renut_effects
