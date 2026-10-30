// DBZ3-Android - hooks de desempenho especificos do Android (build US/NA)
//
// sub_82235918 e a espera do Direct3D do Xbox 360 por espaco no ring buffer da
// GPU: chamada em loop (por exemplo por sub_82231110) ate o command processor
// consumir comandos. No 360 o loop usa "cctpl / db16cyc / cctpm" (baixa a
// prioridade da thread de hardware e pausa uns ciclos); recompilado, isso fica
// vazio e a Main XThread gira a 100% de um nucleo. O perfil no Snapdragon 8 Elite
// mostrou 66% do tempo da thread do jogo dentro desta espera.
//
// O hook devolve a pausa ao loop: as primeiras voltas so cedem o CPU (yield) e,
// se a espera se prolongar, dorme 50 us por volta. A logica do jogo nao muda:
// a funcao original corre sempre a seguir e decide se continua a esperar.

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <mutex>
#include <thread>

#include <rex/hook.h>

#if defined(DF_RENUT)
#include "renut_engine/gpu/banjo_renut_select.h"
#include "renut_engine/gpu/banjo_shared_hooks.h"
#endif

// CBlocker::Check de cada jogo (mapa do Optxbox, maps/dbz-budokai-hd/): Budokai 3 82235918,
// Budokai 1 821C4A58.
#if defined(DF_GAME_B3)
#define DF_CBLOCKER_CHECK sub_82235918
#define DF_CBLOCKER_CHECK_IMP __imp__sub_82235918
#else
#define DF_CBLOCKER_CHECK sub_821C4A58
#define DF_CBLOCKER_CHECK_IMP __imp__sub_821C4A58
#endif
REX_EXTERN(DF_CBLOCKER_CHECK_IMP);

// Base da memoria do jogo, para o game_state_jni.cpp (controles por toque) ler
// o estado do combate fora das funcoes do jogo. Guardada aqui porque este hook
// corre logo desde o arranque e muitas vezes por frame.
extern std::atomic<uint8_t*> g_dragonfury_guest_base;

// Pausa dos menus por cima do jogo (game_state_jni.cpp). A thread principal do
// jogo passa por este hook em todos os frames (espera por espaco no ring buffer
// da GPU); com a pausa pedida fica aqui parada ate a pausa acabar, e a logica do
// jogo congela no frame atual. (O GraphicsSystem::Pause do SDK suspende a thread
// da GPU com Thread::Suspend, que no Android nao parava o jogo.)
std::atomic<bool> g_dragonfury_pause{false};
std::mutex g_dragonfury_pause_mu;
std::condition_variable g_dragonfury_pause_cv;

// A base da memoria e o ponto de pausa, em todos os frames: aqui (Xenia) e no Swap do Renut (renut_glue.cpp), onde
// esta espera nao corre (o BlockOnFence do Renut espera pela thread de render, sem CBlocker).
void DragonFuryFramePoint(uint8_t* base) {
  if (g_dragonfury_guest_base.load(std::memory_order_relaxed) != base) {
    g_dragonfury_guest_base.store(base, std::memory_order_release);
  }
  if (g_dragonfury_pause.load(std::memory_order_acquire)) {
    std::unique_lock<std::mutex> lock(g_dragonfury_pause_mu);
    g_dragonfury_pause_cv.wait(lock, [] { return !g_dragonfury_pause.load(std::memory_order_acquire); });
  }
}

REX_HOOK_RAW(DF_CBLOCKER_CHECK) {
  using clock = std::chrono::steady_clock;
  thread_local clock::time_point last_call{};
  thread_local uint32_t spins = 0;

  DragonFuryFramePoint(base);
#if defined(DF_RENUT)
  // Renut: o lado dele do CBlocker::Check (patches/renut/0200, partilhado) responde "feito".
  if (banjo::renut_gpu::Active()) {
    RENUT_NATIVE(RENUT_SUB_CBLOCKER_CHECK)(ctx, base);
    return;
  }
#endif

  const auto now = clock::now();
  // Uma pausa grande desde a ultima chamada = nova espera: recomeca a contagem.
  if (now - last_call > std::chrono::milliseconds(2)) {
    spins = 0;
  }
  last_call = now;

  if (++spins <= 16) {
    std::this_thread::yield();
  } else {
    std::this_thread::sleep_for(std::chrono::microseconds(50));
  }

  DF_CBLOCKER_CHECK_IMP(ctx, base);
}
