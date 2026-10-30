// Dragon Fury: widescreen nas telas mais largas que 16:9, com o Renut (como o Dragon Burst, renut 0118). So no B3 por
// agora: no B1 nenhuma das funcoes de frustum faz a vista da luta, e a constante 16/9 em 0x82024B2C (a unica do
// executavel) tambem decide o modo de video (escrita com outro valor, o ecra fica preto).
//
// O jogo continua a desenhar o seu quadro de 1280x720, mas com uma vista mais larga (Hor+): a funcao de frustum
// em perspetiva (l, r, b, t, zn, zf em f1-f6; B3 sub_8224F768, achada pelo padrao fsubs f12,f2,f1 / fsubs f10,f5,f6)
// recebe l e r multiplicados por aspeto / (16/9). O present do Renut mostra o quadro nesse aspeto
// (rex::graphics::SetGuestDisplayAspect, esticado na horizontal) e o 2D do jogo volta a forma certa pela callback
// de overlay: cada desenho 2D fica com a largura multiplicada por (16/9) / aspeto, ao centro. Por cima de uma cena
// 3D, o que cobre a largura toda (fundos, fades) estica ate as pontas e, em luta, o HUD de cima e de baixo vai para
// as pontas. Os ecras so de 2D (titulo, menus, roda de personagens) ficam inteiros ao centro, com faixas, como no PC.
//
// dbz3_widescreen liga; dbz3_widescreen_aspect > 0 fixa o aspeto (testes numa tela 16:9), 0 = o da tela.
#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstring>

#include <rex/cvar.h>
#include <rex/graphics/wide_aspect.h>
#include <rex/hook.h>

#if defined(DF_RENUT)
#include "renut_engine/gpu/banjo_renut_select.h"
#endif

REXCVAR_DEFINE_BOOL(dbz3_widescreen, false, "DBZ3/Video",
                    "Widescreen on screens wider than 16:9 (Renut): wider view, 2D kept in shape")
    .lifecycle(rex::cvar::Lifecycle::kHotReload);
REXCVAR_DEFINE_DOUBLE(dbz3_widescreen_aspect, 0.0, "DBZ3/Video",
                      "Widescreen aspect ratio (0 = the screen's)")
    .lifecycle(rex::cvar::Lifecycle::kHotReload);

#if defined(DF_GAME_B3)
#define DF_FRUSTUM sub_8224F768
#define DF_FRUSTUM_IMP __imp__sub_8224F768
#else
#define DF_FRUSTUM sub_821DE8A8
#define DF_FRUSTUM_IMP __imp__sub_821DE8A8
#endif
REX_EXTERN(DF_FRUSTUM_IMP);

namespace {
constexpr float kNative = 16.0f / 9.0f;

// O aspeto em que o quadro e mostrado: 0 com o widescreen desligado, no Xenia ou numa tela de 16:9 ou mais estreita.
float WideAspect() {
#if defined(DF_RENUT) && defined(DF_GAME_B3)
  if (!REXCVAR_GET(dbz3_widescreen) || !banjo::renut_gpu::Active()) return 0.0f;
  float a = float(REXCVAR_GET(dbz3_widescreen_aspect));
  if (!(a > 0.0f)) {
    uint32_t w = 0, h = 0;
    rex::graphics::GetOutputSurfaceSize(w, h);
    a = (w && h) ? float(w) / float(h) : 0.0f;
  }
  return a > kNative + 0.01f ? std::min(a, 4.0f) : 0.0f;
#else
  return 0.0f;
#endif
}

// Em luta (DragonFuryWideUpdate, a cada quadro): o HUD da luta vai para as pontas.
std::atomic<bool> g_fight{false};
// Quadros desde a ultima vista 3D (o frustum): os ecras so de 2D (titulo, menus, cartas) ficam inteiros ao centro,
// com faixas aos lados, como no PC; os fundos e fades so esticam ate as pontas por cima de uma cena 3D.
std::atomic<uint32_t> g_frames_2d{0};

float LoadBeF(const uint8_t* p) {
  uint32_t u;
  std::memcpy(&u, p, 4);
  u = __builtin_bswap32(u);
  float f;
  std::memcpy(&f, &u, 4);
  return f;
}

// O 2D do jogo: posicao em pixeis (screen space) nos primeiros 8 bytes de cada vertice. Sem vertices legiveis, o
// desenho fica centrado e na forma certa.
rex::graphics::OverlayPlacement Place(const rex::graphics::OverlayDrawInfo& info) {
  rex::graphics::OverlayPlacement out;
  const float aspect = rex::graphics::GetGuestDisplayAspect();
  if (!(aspect > kNative + 0.01f)) return out;
  const float s = kNative / aspect;
  // So o 2D do jogo: o vertex shader dele leva x * c0.x - 1 e y * c1.y + 1 a clip space (os dois jogos desenham o
  // 2D em 800x450, c0.x = 1/400, c1.y = -1/225). O resto que chega aqui (contornos e brilhos das personagens, sem
  // teste de profundidade) e 3D e ja tem a vista larga pelo frustum.
  const float c0x = info.vertex_constants[0], c1y = info.vertex_constants[5];
  if (!(c0x > 1e-4f && c0x < 0.1f) || !(c1y < -1e-4f && c1y > -0.1f)) return out;
  // Texturas com a altura do quadro (720, 360, 180...; o B3 tem uma copia da cena de 960x720): passes de ecra
  // inteiro, ficam esticados.
  const uint32_t tw = info.texture0_width, th = info.texture0_height;
  if (tw && th && tw >= th && th >= 90 && 720 % th == 0) return out;
  // Os limites do desenho (posicao em x, y nos primeiros 8 bytes de cada vertice).
  float x0 = 1e9f, x1 = -1e9f, y0 = 1e9f, y1 = -1e9f;
  const uint32_t n = info.vertex_count;
  if (info.vertex_data && n && info.vertex_data_size >= n * 8) {
    const uint32_t stride = info.vertex_data_size / n;
    for (uint32_t i = 0; i < n; ++i) {
      const float x = LoadBeF(info.vertex_data + i * stride), y = LoadBeF(info.vertex_data + i * stride + 4);
      if (!std::isfinite(x) || !std::isfinite(y)) continue;
      x0 = std::min(x0, x), x1 = std::max(x1, x), y0 = std::min(y0, y), y1 = std::max(y1, y);
    }
  }
  out.scale_x = s;
  // Ecra so de 2D: tudo inteiro ao centro.
  if (!(x1 > x0) || g_frames_2d.load(std::memory_order_relaxed) >= 3) return out;
  const float fw = 2.0f / c0x, fh = -2.0f / c1y;
  const float l = x0 / fw * 2.0f - 1.0f, r = x1 / fw * 2.0f - 1.0f;
  // Largura toda (fundos, fades) por cima de uma cena 3D: esticado ate as pontas.
  if (l <= -0.98f && r >= 0.98f) return {};
  // HUD da luta: pecas pequenas no terco de fora e na faixa de cima ou de baixo (barras de vida, retratos),
  // encostadas a ponta do ecra. So em luta: nos menus as pecas de cima e de baixo fazem parte do quadro ao centro.
  // As do meio (tabelas de texto e numeros) e as altas (molduras, imagens de 4:3) ficam no sitio.
  const float c = 0.5f * (l + r);
  const bool tall = (y1 - y0) >= 0.6f * fh;
  const bool band = y1 <= 0.33f * fh || y0 >= 0.82f * fh;
  if (g_fight.load(std::memory_order_relaxed) && !tall && band && (r - l) < 0.8f) {
    if (c < -1.0f / 3.0f) out.offset_x = -(1.0f - s);
    else if (c > 1.0f / 3.0f) out.offset_x = 1.0f - s;
  }
  return out;
}

std::atomic<bool> g_callback_set{false};
}  // namespace

bool DragonFuryFightNow();  // game_state_jni.cpp
#if defined(DF_GAME_B3)
bool dbz3_select_live();  // select_ext.cpp: a roda de personagens (tem o modelo 3D, mas e um menu)
#endif

// Chamado em todos os quadros (o frustum corre a cada vista 3D) e no Swap do Renut: o aspeto do present e a callback.
void DragonFuryWideUpdate() {
  const float a = WideAspect();
  g_fight.store(a > 0.0f && DragonFuryFightNow(), std::memory_order_relaxed);
  g_frames_2d.fetch_add(1, std::memory_order_relaxed);
#if defined(DF_GAME_B3)
  if (dbz3_select_live()) g_frames_2d.store(1000, std::memory_order_relaxed);
  else if (g_frames_2d.load(std::memory_order_relaxed) >= 1000) g_frames_2d.store(3, std::memory_order_relaxed);
#endif
  if (rex::graphics::GetGuestDisplayAspect() != a) rex::graphics::SetGuestDisplayAspect(a);
  if (!g_callback_set.exchange(true)) rex::graphics::SetOverlayPlacementCallback(Place);
}

REX_HOOK_RAW(DF_FRUSTUM) {
  const float a = WideAspect();
  if (g_frames_2d.load(std::memory_order_relaxed) < 1000) g_frames_2d.store(0, std::memory_order_relaxed);
  // So a vista principal (l/t de 16:9, simetrica); sombras e outras vistas ficam como estao.
  if (a > 0.0f && ctx.f4.f64 > 0.0 && std::fabs(ctx.f1.f64 + ctx.f2.f64) < 1e-4 &&
      std::fabs(ctx.f2.f64 / ctx.f4.f64 - kNative) < 0.02) {
    const double k = a / kNative;
    ctx.f1.f64 *= k;
    ctx.f2.f64 *= k;
  }
  DF_FRUSTUM_IMP(ctx, base);
}
