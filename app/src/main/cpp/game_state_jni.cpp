// DBZ3-Android / Dragon Fury - estado do combate para os controles por toque.
//
// O botao "carregar ki" precisa de saber o que e "para tras" para o P1. O jogo
// nao tem um campo de orientacao conhecido, por isso segue-se o lado de cada
// lutador pela posicao (mesmos enderecos que o tagteam.cpp do projeto original,
// build US):
//
//   *0x823D6CF4 + 2096 + 8240*p   bloco do lutador p (0 = P1, 1 = P2)
//   X+64 / X+72                   posicao x / z (float big-endian)
//   0x823F39A0                    1 entre o FIGHT! e o fim do assalto
//
// O P1 comeca cada assalto a esquerda. Depois, a cada leitura, compara-se o
// vetor P1->P2 (plano xz) com o da leitura anterior: a camara do Budokai 3
// acompanha os lutadores, por isso o vetor roda devagar, e so troca de sentido
// (produto escalar negativo) quando um passa para o outro lado (salto por cima,
// teletransporte). Nao escreve nada na memoria do jogo.

#include <jni.h>

#include <atomic>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <mutex>

std::atomic<uint8_t*> g_dragonfury_guest_base{nullptr};

namespace {

constexpr uint32_t kBattleCfgPtr = 0x823D6CF4;
constexpr uint32_t kFightFlag = 0x823F39A0;

uint32_t LoadU32(const uint8_t* base, uint32_t addr) {
  uint32_t v;
  std::memcpy(&v, base + addr, 4);
  return __builtin_bswap32(v);
}

float LoadF32(const uint8_t* base, uint32_t addr) {
  const uint32_t u = LoadU32(base, addr);
  float f;
  std::memcpy(&f, &u, 4);
  return f;
}

struct Tracker {
  std::mutex mu;
  bool fighting = false;
  bool p1_left = true;
  float ref_x = 0.0f, ref_z = 0.0f;
  bool has_ref = false;
};
Tracker g_tracker;

}  // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_dfdx047_dragonfury_game_NativeGameState_nativeP1Side(JNIEnv*, jclass) {
  const uint8_t* base = g_dragonfury_guest_base.load(std::memory_order_acquire);
  if (!base) return 0;
  std::lock_guard<std::mutex> lock(g_tracker.mu);

  const bool fighting = LoadU32(base, kFightFlag) != 0;
  if (!fighting) {
    g_tracker.fighting = false;
    g_tracker.has_ref = false;
    return 0;
  }
  const uint32_t cfg = LoadU32(base, kBattleCfgPtr);
  if (cfg == 0) return 0;
  const uint32_t p1 = cfg + 2096;
  const uint32_t p2 = cfg + 2096 + 8240;
  const float dx = LoadF32(base, p2 + 64) - LoadF32(base, p1 + 64);
  const float dz = LoadF32(base, p2 + 72) - LoadF32(base, p1 + 72);
  if (!std::isfinite(dx) || !std::isfinite(dz)) return 0;

  if (!g_tracker.fighting) {
    // Inicio do assalto: o P1 esta a esquerda.
    g_tracker.fighting = true;
    g_tracker.p1_left = true;
    g_tracker.has_ref = false;
  }
  const float len2 = dx * dx + dz * dz;
  if (len2 > 1e-6f) {
    if (g_tracker.has_ref) {
      const float dot = dx * g_tracker.ref_x + dz * g_tracker.ref_z;
      if (dot < 0.0f) g_tracker.p1_left = !g_tracker.p1_left;
    }
    g_tracker.ref_x = dx;
    g_tracker.ref_z = dz;
    g_tracker.has_ref = true;
  }
  return g_tracker.p1_left ? 1 : 2;
}
