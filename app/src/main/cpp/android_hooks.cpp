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
#include <cstdint>
#include <thread>

#include <rex/hook.h>

REX_EXTERN(__imp__sub_82235918);

// Base da memoria do jogo, para o game_state_jni.cpp (controles por toque) ler
// o estado do combate fora das funcoes do jogo. Guardada aqui porque este hook
// corre logo desde o arranque e muitas vezes por frame.
extern std::atomic<uint8_t*> g_dragonfury_guest_base;

REX_HOOK_RAW(sub_82235918) {
  using clock = std::chrono::steady_clock;
  thread_local clock::time_point last_call{};
  thread_local uint32_t spins = 0;

  if (g_dragonfury_guest_base.load(std::memory_order_relaxed) != base) {
    g_dragonfury_guest_base.store(base, std::memory_order_release);
  }

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

  __imp__sub_82235918(ctx, base);
}
