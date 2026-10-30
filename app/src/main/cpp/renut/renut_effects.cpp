// Opções gráficas do MGR no Renut: ver renut_effects.h.

#include "renut_effects.h"

#include <rex/cvar.h>
#include <rex/logging.h>

#include <vector>

REXCVAR_DEFINE_BOOL(swordman_renut_shadows, true, "SwordMan",
                    "Renut: sombras do sol (mapa de sombra e passe de sombra em tela). false = sem sombras");
REXCVAR_DEFINE_BOOL(swordman_renut_bloom, true, "SwordMan",
                    "Renut: brilho (bloom, cadeia de 320x176 somada na imagem final). false = imagem original");

namespace swordman::renut_effects {
namespace {

// Poucos (2 cascatas por cena): procura linear.
constexpr int kMax = 8;
uint32_t g_depth[kMax] = {}, g_atlas[kMax] = {};
int g_depth_count = 0, g_atlas_count = 0;

bool Has(const uint32_t* list, int count, uint32_t va) {
  for (int i = 0; i < count; ++i)
    if (list[i] == va) return true;
  return false;
}

void Add(uint32_t* list, int& count, uint32_t va, const char* what) {
  if (!va || Has(list, count, va) || count == kMax) return;
  list[count++] = va;
  REXLOG_INFO("Renut: {} das sombras {:08X}", what, va);
}

}  // namespace

bool ShadowsOff() { return !REXCVAR_GET(swordman_renut_shadows); }
bool BloomOff() { return !REXCVAR_GET(swordman_renut_bloom); }

void NoteResolve(bool depth, uint32_t width, uint32_t height, uint32_t source_va, uint32_t dest_texture_va) {
  if (!depth || width != height || width < 512) return;
  Add(g_depth, g_depth_count, source_va, "alvo de profundidade");
  Add(g_atlas, g_atlas_count, dest_texture_va, "atlas");
}

bool IsShadowDepth(uint32_t depth_va) { return depth_va && Has(g_depth, g_depth_count, depth_va); }
bool IsShadowAtlas(uint32_t texture_va) { return texture_va && Has(g_atlas, g_atlas_count, texture_va); }

bool IsBloomShader(uint64_t ps_hash) {
  switch (ps_hash) {
    case 0x07A5E4820E9F50C0ull:  // redução (do ecrã para 320x176 e entre níveis)
    case 0x0AD31892DCFED92Dull:  // desfoque horizontal
    case 0x240A635A36C38D19ull:  // desfoque vertical
    case 0xF71DC5AC7E1A72E0ull:  // soma dos níveis
      return true;
    default:
      return false;
  }
}

namespace {
constexpr uint64_t kFxaa = 0xF5F4D761A981B87Dull, kDof = 0x4DFCBC49057E5721ull;
constexpr uint64_t kBloomChain = 0x555EBAAEF8F61839ull, kBloomSource = 0xCB0605B416DF85B8ull,
                   kCopy = 0x6E57A6E305D5D69Bull;

struct FrameResolve {
  const void* surface;
  uint32_t width;
  uint32_t dest;
  bool clears;
  bool dropped;
};
std::vector<FrameResolve> g_frame;
uint64_t g_frame_of = UINT64_MAX;
struct Learned {
  uint32_t dest;
  int kind;  // 0 FXAA, 1 profundidade de campo
  uint64_t seen;
};
std::vector<Learned> g_learned;
uint32_t g_logs = 0;

// As opções (dbz3_fx_*, definidas no SDK, vulkan/command_processor.cpp, noutra biblioteca): lidas pelo nome uma vez
// por frame (mudam ao vivo no menu do jogo).
bool g_fxaa_off = false, g_dof_off = false, g_bloom_off = false;

void Sync(uint64_t frame) {
  if (g_frame_of != frame) {
    g_frame_of = frame;
    g_frame.clear();
    g_fxaa_off = rex::cvar::GetFlagByName("dbz3_fx_fxaa") == "false";
    g_dof_off = rex::cvar::GetFlagByName("dbz3_fx_dof") == "false";
    g_bloom_off = rex::cvar::GetFlagByName("dbz3_fx_bloom") == "false";
  }
}

bool KindOff(int kind) { return kind == 0 ? g_fxaa_off : g_dof_off; }

const FrameResolve* LastResolveTo(uint32_t dest) {
  for (auto it = g_frame.rbegin(); it != g_frame.rend(); ++it)
    if (it->dest == dest) return &*it;
  return nullptr;
}
}  // namespace

bool BudokaiResolveDropsClear(uint64_t frame, const void* surface, uint32_t surface_width, uint32_t dest_va,
                              bool clears) {
  Sync(frame);
  bool drop = false;
  if (clears) {
    for (const Learned& l : g_learned)
      if (l.dest == dest_va && KindOff(l.kind) && frame <= l.seen + 2) {
        drop = true;
        break;
      }
  }
  if (g_frame.size() < 64) g_frame.push_back({surface, surface_width, dest_va, clears, drop});
  return drop;
}

bool BudokaiSkipDraw(uint64_t frame, uint64_t ucode_hash, bool opaque, const void* target, uint32_t target_width,
                     const uint32_t* textures, uint32_t count) {
  Sync(frame);
  if (g_bloom_off) {
    if (ucode_hash == kBloomChain) return true;
    if (ucode_hash == kBloomSource && target_width == 320) return true;
    if (ucode_hash == kCopy && !opaque && count) {
      const FrameResolve* r = LastResolveTo(textures[0]);
      if (r && r->width <= 160) return true;
    }
  }
  const int kind = ucode_hash == kFxaa ? 0 : ucode_hash == kDof ? 1 : -1;
  if (kind < 0 || !opaque || !KindOff(kind)) return false;
  // A textura do efeito que é uma cópia, deste frame, do alvo em que ele desenha (a profundidade de campo também lê
  // o desfoque pequeno).
  uint32_t src = 0;
  const FrameResolve* r = nullptr;
  bool same_target = false;
  for (uint32_t i = 0; i < count && !same_target; ++i) {
    const FrameResolve* found = LastResolveTo(textures[i]);
    const bool same = found && found->surface == target;
    if (!r || same) {
      src = textures[i];
      r = found;
      same_target = same;
    }
  }
  if (!src) return false;
  Learned* learned = nullptr;
  for (Learned& l : g_learned)
    if (l.dest == src && l.kind == kind) learned = &l;
  if (learned) learned->seen = frame;
  if (same_target && (!r->clears || r->dropped)) return true;
  if (same_target && r->clears && !learned) {
    // Este frame já perdeu o ecrã na limpeza: desenha-se; dos próximos em diante a limpeza sai.
    if (g_learned.size() >= 16) g_learned.erase(g_learned.begin());
    g_learned.push_back({src, kind, frame});
    REXLOG_INFO("Renut: {} desligado: entrada {:08X} (resolve com limpeza do mesmo alvo) aprendida",
                kind == 0 ? "FXAA" : "profundidade de campo", src);
    return false;
  }
  if (!same_target && g_logs < 8) {
    ++g_logs;
    REXLOG_INFO("Renut: {} desligado: draw mantido, entrada {:08X} {}", kind == 0 ? "FXAA" : "profundidade de campo",
                src, r ? "de outro alvo" : "sem resolve neste frame");
  }
  return false;
}

}  // namespace swordman::renut_effects
