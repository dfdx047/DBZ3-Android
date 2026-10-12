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
#include <cstdio>
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

// Luta a decorrer: a bandeira 0x823F39A0 nao serve (ficou a 0 durante lutas inteiras,
// logs de 2026-10-12). O que e fiavel: o ponteiro da configuracao da batalha e as
// posicoes dos lutadores. Nos menus o ponteiro e 0 ou as posicoes sao (0,0); entre
// assaltos voltam a (0,0) e cada assalto recomeca com o P1 a esquerda (x = -15).
struct FightPositions {
  bool active = false;
  float dx = 0.0f, dz = 0.0f;
};

FightPositions ReadFight(const uint8_t* base) {
  FightPositions f;
  const uint32_t cfg = LoadU32(base, kBattleCfgPtr);
  if (cfg == 0 || cfg >= 0xF0000000u) return f;
  const uint32_t p1 = cfg + 2096;
  const uint32_t p2 = cfg + 2096 + 8240;
  const float x1 = LoadF32(base, p1 + 64), z1 = LoadF32(base, p1 + 72);
  const float x2 = LoadF32(base, p2 + 64), z2 = LoadF32(base, p2 + 72);
  if (!std::isfinite(x1) || !std::isfinite(z1) || !std::isfinite(x2) || !std::isfinite(z2)) return f;
  const bool all_zero = std::fabs(x1) < 0.01f && std::fabs(z1) < 0.01f && std::fabs(x2) < 0.01f &&
                        std::fabs(z2) < 0.01f;
  if (all_zero) return f;
  f.active = true;
  f.dx = x2 - x1;
  f.dz = z2 - z1;
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

  const FightPositions fight = ReadFight(base);
  if (!fight.active) {
    // Fora da luta ou entre assaltos: o proximo assalto recomeca com o P1 a esquerda.
    g_tracker.fighting = false;
    g_tracker.has_ref = false;
    return 0;
  }
  const float dx = fight.dx;
  const float dz = fight.dz;

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

// ---------------------------------------------------------------------------
// Pausa do jogo enquanto um menu esta aberto (menus do launcher por cima do jogo
// ou o menu rapido do port, Back+Start / F1).
//
// O jogo para no hook da espera do ring buffer (android_hooks.cpp), por onde a
// thread principal passa em todos os frames; o audio para com AudioSystem::Pause().
// (O GraphicsSystem::Pause do SDK so suspendia a thread da GPU e o jogo continuava.)
//
// O Kotlin chama nativeSyncPause a cada tick do HelperEngine (60 Hz): assim o
// menu rapido do port tambem pausa, sem mexer no codigo dele.
// ---------------------------------------------------------------------------

#include <condition_variable>

#include <rex/audio/audio_system.h>
#include <rex/runtime.h>
#include <rex/ui/overlay/quick_menu.h>

extern std::atomic<bool> g_dragonfury_pause;
extern std::mutex g_dragonfury_pause_mu;
extern std::condition_variable g_dragonfury_pause_cv;

namespace {
std::mutex g_pause_mu;
bool g_paused = false;
}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_dfdx047_dragonfury_game_NativeGameState_nativeSyncPause(JNIEnv*, jclass, jboolean overlay_open,
                                                                jboolean pause_quick_menu) {
  std::lock_guard<std::mutex> lock(g_pause_mu);
  auto* rt = rex::Runtime::instance();
  if (!rt || !g_dragonfury_guest_base.load(std::memory_order_acquire)) return JNI_FALSE;
  const bool want = overlay_open || (pause_quick_menu && rex::ui::QuickMenuDialog::IsOpen());
  if (want == g_paused) return g_paused ? JNI_TRUE : JNI_FALSE;

  // O audio do runtime e sempre rex::audio::AudioSystem (o SDL deriva dela).
  auto* audio = static_cast<rex::audio::AudioSystem*>(rt->audio_system());
  if (want) {
    g_dragonfury_pause.store(true, std::memory_order_release);
    if (audio) audio->Pause();
  } else {
    if (audio) audio->Resume();
    {
      std::lock_guard<std::mutex> l(g_dragonfury_pause_mu);
      g_dragonfury_pause.store(false, std::memory_order_release);
    }
    g_dragonfury_pause_cv.notify_all();
  }
  g_paused = want;
  return g_paused ? JNI_TRUE : JNI_FALSE;
}

/** Ao sair do jogo: nunca deixar a thread do jogo presa na pausa. */
extern "C" JNIEXPORT void JNICALL
Java_com_dfdx047_dragonfury_game_NativeGameState_nativeForceResume(JNIEnv*, jclass) {
  {
    std::lock_guard<std::mutex> l(g_dragonfury_pause_mu);
    g_dragonfury_pause.store(false, std::memory_order_release);
  }
  g_dragonfury_pause_cv.notify_all();
}

// ---------------------------------------------------------------------------
// Luta ou menus: os controles por toque trocam de layout. 0x823F39A0 (US) vale 1
// entre o FIGHT! e o fim do assalto (tagteam.cpp do projeto original). O Kotlin
// junta histerese (fica no layout de luta uns segundos depois de ir a 0, para
// nao trocar entre assaltos). -1 = ainda nao ha memoria do jogo.
// ---------------------------------------------------------------------------
extern "C" JNIEXPORT jint JNICALL
Java_com_dfdx047_dragonfury_game_NativeGameState_nativeFightFlag(JNIEnv*, jclass) {
  const uint8_t* base = g_dragonfury_guest_base.load(std::memory_order_acquire);
  if (!base) return -1;
  return (LoadU32(base, kFightFlag) != 0 || ReadFight(base).active) ? 1 : 0;
}

// Diagnostico da troca luta/menus (logs/controles.log com "Diagnostico de desempenho"):
// a bandeira da luta, o ponteiro da configuracao da batalha e as posicoes dos dois
// lutadores, para descobrir porque a revanche as vezes nao troca o layout.
extern "C" JNIEXPORT jstring JNICALL
Java_com_dfdx047_dragonfury_game_NativeGameState_nativeBattleDebug(JNIEnv* env, jclass) {
  const uint8_t* base = g_dragonfury_guest_base.load(std::memory_order_acquire);
  if (!base) return env->NewStringUTF("sem jogo");
  const uint32_t flag = LoadU32(base, kFightFlag);
  const uint32_t cfg = LoadU32(base, kBattleCfgPtr);
  char text[160];
  if (cfg != 0 && cfg < 0xF0000000u) {
    const uint32_t p1 = cfg + 2096;
    const uint32_t p2 = cfg + 2096 + 8240;
    snprintf(text, sizeof(text), "flag=%08X cfg=%08X p1=(%.1f,%.1f) p2=(%.1f,%.1f)", flag, cfg,
             LoadF32(base, p1 + 64), LoadF32(base, p1 + 72), LoadF32(base, p2 + 64),
             LoadF32(base, p2 + 72));
  } else {
    snprintf(text, sizeof(text), "flag=%08X cfg=%08X", flag, cfg);
  }
  return env->NewStringUTF(text);
}

// ---------------------------------------------------------------------------
// Menu de opcoes do port por toque (Compose, InGameMenu.kt): muda cvars ao vivo
// como o menu rapido do PC (quick_settings.cpp ApplyAndSave): define os cvars,
// passa-os ao SDK e grava o dbz3_user.toml.
// ---------------------------------------------------------------------------
#include <string>

#include <rex/cvar.h>

#include "src/launcher/settings.h"

extern "C" JNIEXPORT jboolean JNICALL
Java_com_dfdx047_dragonfury_game_NativeGameState_nativeSetCvars(JNIEnv* env, jclass, jobjectArray names,
                                                               jobjectArray values) {
  if (!rex::Runtime::instance()) return JNI_FALSE;
  const jsize n = env->GetArrayLength(names);
  bool ok = true;
  for (jsize i = 0; i < n; ++i) {
    auto jn = static_cast<jstring>(env->GetObjectArrayElement(names, i));
    auto jv = static_cast<jstring>(env->GetObjectArrayElement(values, i));
    const char* cn = env->GetStringUTFChars(jn, nullptr);
    const char* cv = env->GetStringUTFChars(jv, nullptr);
    ok = rex::cvar::SetFlagByName(cn, cv) && ok;
    env->ReleaseStringUTFChars(jn, cn);
    env->ReleaseStringUTFChars(jv, cv);
    env->DeleteLocalRef(jn);
    env->DeleteLocalRef(jv);
  }
  dbz3::settings::ApplyRuntimeSettingsToSdk(true);
  dbz3::settings::SaveUserSettings();
  return ok ? JNI_TRUE : JNI_FALSE;
}
