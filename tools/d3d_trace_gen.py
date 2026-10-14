#!/usr/bin/env python3
"""Gera hooks de rastreio das funcoes D3D candidatas (confirmacao em tempo real).

Entrada: perf/d3d/d3d_map.json (tools/d3d_map.py). Saida: um .cpp com um
REX_HOOK_RAW por funcao escolhida; cada hook conta as chamadas e guarda os
argumentos (r3..r10, f1..f4) das primeiras chamadas, e chama a funcao original.
Uma vez por segundo (ou por N frames, se a funcao do Swap for conhecida) escreve
em logs/d3d_trace.log quantas vezes por frame cada funcao foi chamada e exemplos
de argumentos. Com ~400 draws por frame numa luta, a funcao chamada ~400x/frame
que escreve DRAW_INDX e o DrawIndexedVertices; e assim por diante.

So entra no build com:  ./gradlew assembleRelease -Pdf.d3dTrace=true
(o CMake compila app/src/main/cpp/d3d_trace_hooks.cpp se existir e a opcao
estiver ligada). Nao usar em testes de desempenho.

Uso:
  python3 tools/d3d_map.py
  python3 tools/d3d_trace_gen.py                 # funcoes publicas do intervalo D3D
  python3 tools/d3d_trace_gen.py --all-in-range  # todas as do intervalo (mais lento)
  python3 tools/d3d_trace_gen.py --only sub_82240000,sub_82241000
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys

HOOK_RX = re.compile(r"(?:REX_HOOK_RAW|REX_HOOK|REX_STUB\w*|DBZ3_HOOK|REX_EXPORT\w*)\(\s*(sub_[0-9A-Fa-f]{8})")


def existing_hooks(dirs):
    found = set()
    for d in dirs:
        if not os.path.isdir(d):
            continue
        for dp, _dn, fns in os.walk(d):
            if "generated" in dp.split(os.sep):
                continue
            for fn in fns:
                if not fn.endswith((".cpp", ".h", ".inc")) or fn == "d3d_trace_hooks.cpp":
                    continue
                try:
                    with open(os.path.join(dp, fn), errors="replace") as fh:
                        for m in HOOK_RX.finditer(fh.read()):
                            found.add(m.group(1).upper().replace("SUB_", "sub_"))
                except OSError:
                    pass
    return found


TEMPLATE_HEAD = r'''// GERADO por tools/d3d_trace_gen.py -- nao editar a mao.
// Rastreio das funcoes D3D candidatas (ver tools/d3d_map.py). So para mapear a
// biblioteca D3D do XDK: custa CPU, nao usar em testes de desempenho.

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <string>

#include <rex/hook.h>

namespace {

constexpr int kNumFuncs = @@N@@;
constexpr int kSamples = 4;

struct Sample {
  uint32_t r[8];   // r3..r10
  float f[4];      // f1..f4
  uint32_t lr;
};

struct Slot {
  const char* name;
  const char* hint;
  std::atomic<uint64_t> total{0};
  std::atomic<uint32_t> window{0};
  std::atomic<uint32_t> sampled{0};
  Sample samples[kSamples];
};

Slot g_slots[kNumFuncs];
std::atomic<uint32_t> g_frames{0};
std::atomic<int64_t> g_last_dump_ns{0};
std::mutex g_dump_mu;
FILE* g_log = nullptr;
int g_dumps = 0;

const char* kNames[kNumFuncs] = {@@NAMES@@};
const char* kHints[kNumFuncs] = {@@HINTS@@};

int64_t NowNs() {
  return std::chrono::duration_cast<std::chrono::nanoseconds>(
             std::chrono::steady_clock::now().time_since_epoch())
      .count();
}

FILE* OpenLog() {
  if (g_log) return g_log;
  std::string dir;
  if (const char* lf = std::getenv("REX_LOG_FILE")) {
    dir = lf;
    const auto p = dir.find_last_of('/');
    dir = p == std::string::npos ? std::string(".") : dir.substr(0, p);
  } else if (const char* ad = std::getenv("REX_APP_DIR")) {
    dir = std::string(ad) + "/logs";
  } else {
    dir = ".";
  }
  g_log = std::fopen((dir + "/d3d_trace.log").c_str(), "w");
  if (g_log) {
    std::fprintf(g_log, "# d3d_trace: %d funcoes; janelas de ~1 s; por frame = chamadas/frames da janela\n",
                 kNumFuncs);
    for (int i = 0; i < kNumFuncs; ++i) {
      g_slots[i].name = kNames[i];
      g_slots[i].hint = kHints[i];
    }
  }
  return g_log;
}

void Dump(int64_t now) {
  std::lock_guard<std::mutex> lock(g_dump_mu);
  FILE* f = OpenLog();
  if (!f) return;
  const uint32_t frames = g_frames.exchange(0);
  std::fprintf(f, "\n== janela %d, frames=%u ==\n", g_dumps++, frames);
  for (int i = 0; i < kNumFuncs; ++i) {
    Slot& s = g_slots[i];
    const uint32_t n = s.window.exchange(0);
    if (!n) continue;
    std::fprintf(f, "%-14s %8u  %7.1f/f  total=%llu  [%s]\n", kNames[i], n,
                 frames ? double(n) / frames : 0.0,
                 (unsigned long long)s.total.load(), kHints[i]);
    // amostras so nas 3 primeiras janelas em que a funcao aparece
    const uint32_t got = std::min<uint32_t>(s.sampled.load(), kSamples);
    for (uint32_t k = 0; k < got; ++k) {
      const Sample& m = s.samples[k];
      std::fprintf(f,
                   "    r3=%08X r4=%08X r5=%08X r6=%08X r7=%08X r8=%08X r9=%08X r10=%08X "
                   "f1=%g f2=%g f3=%g f4=%g lr=%08X\n",
                   m.r[0], m.r[1], m.r[2], m.r[3], m.r[4], m.r[5], m.r[6], m.r[7], m.f[0], m.f[1],
                   m.f[2], m.f[3], m.lr);
    }
  }
  std::fflush(f);
  for (int i = 0; i < kNumFuncs; ++i) {
    // reabre a amostragem algumas vezes (argumentos mudam entre menus e luta)
    if (g_dumps % 10 == 0) g_slots[i].sampled.store(0);
  }
}

inline void Trace(int idx, PPCContext& ctx) {
  Slot& s = g_slots[idx];
  s.total.fetch_add(1, std::memory_order_relaxed);
  s.window.fetch_add(1, std::memory_order_relaxed);
  const uint32_t k = s.sampled.fetch_add(1, std::memory_order_relaxed);
  if (k < kSamples) {
    Sample& m = s.samples[k];
    m.r[0] = ctx.r3.u32; m.r[1] = ctx.r4.u32; m.r[2] = ctx.r5.u32; m.r[3] = ctx.r6.u32;
    m.r[4] = ctx.r7.u32; m.r[5] = ctx.r8.u32; m.r[6] = ctx.r9.u32; m.r[7] = ctx.r10.u32;
    m.f[0] = float(ctx.f1.f64); m.f[1] = float(ctx.f2.f64);
    m.f[2] = float(ctx.f3.f64); m.f[3] = float(ctx.f4.f64);
    m.lr = uint32_t(ctx.lr);
  }
  const int64_t now = NowNs();
  int64_t last = g_last_dump_ns.load(std::memory_order_relaxed);
  if (now - last > 1000000000LL) {
    const int64_t seen = last;
    if (g_last_dump_ns.compare_exchange_strong(last, now) && seen != 0) Dump(now);
  }
}

inline void Frame() { g_frames.fetch_add(1, std::memory_order_relaxed); }

}  // namespace

'''

HOOK = '''REX_EXTERN(__imp__{name});
REX_HOOK_RAW({name}) {{
  Trace({idx}, ctx);{frame}
  __imp__{name}(ctx, base);
}}

'''


def cstr(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    root = os.path.normpath(os.path.join(here, ".."))
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--map", default=os.path.join(root, "perf/d3d/d3d_map.json"))
    ap.add_argument("--out", default=os.path.join(root, "app/src/main/cpp/d3d_trace_hooks.cpp"))
    ap.add_argument("--all-in-range", action="store_true")
    ap.add_argument("--only", help="lista de funcoes separadas por virgulas")
    ap.add_argument("--frame", help="funcao que marca o fim do frame (por omissao: a que chama VdSwap)")
    ap.add_argument("--max", type=int, default=400)
    args = ap.parse_args()

    with open(args.map) as fh:
        data = json.load(fh)
    funcs = data["functions"]
    lo, hi = data["range"]

    skip = existing_hooks([os.path.join(root, "app/src/main/cpp"),
                           os.path.join(root, "external/dbz3/src")])

    if args.only:
        wanted = {n.strip() for n in args.only.split(",") if n.strip()}
        chosen = [f for f in funcs if f["name"] in wanted]
    else:
        chosen = []
        for f in funcs:
            inside = lo <= f["addr"] <= hi
            if not inside:
                continue
            if args.all_in_range or f["public"] or f["pm4"] or f["setconst"] or \
                    any(k.startswith("Vd") for k in f["imports"]):
                chosen.append(f)
    frame_fn = args.frame
    if not frame_fn:
        for f in funcs:
            if "VdSwap" in f["imports"]:
                frame_fn = f["name"]
                break

    chosen = [f for f in chosen if f["name"] not in skip][: args.max]
    if frame_fn and frame_fn in skip:
        print(f"aviso: {frame_fn} ja tem hook noutro sitio; contagem de frames desligada", file=sys.stderr)
        frame_fn = None
    if frame_fn and frame_fn not in {f["name"] for f in chosen}:
        extra = next((f for f in funcs if f["name"] == frame_fn), None)
        if extra:
            chosen.append(extra)

    names = ", ".join(cstr(f["name"]) for f in chosen)
    hints = ", ".join(cstr("; ".join(f["suggest"])[:120]) for f in chosen)
    out = [TEMPLATE_HEAD.replace("@@N@@", str(max(1, len(chosen))))
           .replace("@@NAMES@@", names or '""').replace("@@HINTS@@", hints or '""')]
    for i, f in enumerate(chosen):
        out.append(HOOK.format(name=f["name"], idx=i,
                               frame="\n  Frame();" if f["name"] == frame_fn else ""))
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w") as fh:
        fh.write("".join(out))
    print(f"{len(chosen)} hooks -> {args.out} (frame: {frame_fn or 'nenhum'}; "
          f"ignorados por ja terem hook: {len(skip)})")


if __name__ == "__main__":
    main()
