#!/usr/bin/env python3
"""Mapa das funcoes da biblioteca D3D do XDK no executavel recompilado.

Le o codigo gerado pelo codegen do ReXGlue (external/dbz3/generated/*.cpp), que
traz a desmontagem PowerPC de cada instrucao em comentario, e procura:

  * cabecalhos de pacotes PM4 escritos em memoria (lis/ori/li... seguidos de stw):
    type-3 (DRAW_INDX, SET_CONSTANT, IM_LOAD, EVENT_WRITE, ...) e type-0
    (escrita direta de registos da GPU);
  * chamadas a imports do kernel ligados a GPU (VdSwap, VdInitializeRingBuffer,
    XPhysicalAlloc, MmAllocatePhysicalMemoryEx, KeWait*...);
  * o grafo de chamadas, para encontrar o intervalo de enderecos da biblioteca D3D
    (ligada estaticamente, fica contigua) e as funcoes publicas (chamadas de fora
    desse intervalo).

Resultado: um relatorio em Markdown (para ler) e um JSON (para o gerador de hooks
de rastreio, tools/d3d_trace_gen.py) com sugestoes de nomes por funcao.

Uso (a partir da raiz do repositorio):
  python3 tools/d3d_map.py
  python3 tools/d3d_map.py --generated external/dbz3/generated --out perf/d3d
  python3 tools/d3d_map.py --dump sub_82235918      # desmontagem de uma funcao

Nada aqui depende do jogo em si: serve para qualquer jogo de Xbox 360
recompilado com o ReXGlue.
"""

from __future__ import annotations

import argparse
import collections
import json
import os
import re
import sys
from dataclasses import dataclass, field

# ---------------------------------------------------------------------------
# Tabelas da Xenos
# ---------------------------------------------------------------------------

PM4_TYPE3 = {
    0x10: "NOP", 0x21: "REG_RMW", 0x22: "DRAW_INDX", 0x23: "VIZ_QUERY",
    0x25: "SET_STATE", 0x26: "WAIT_FOR_IDLE", 0x27: "IM_LOAD",
    0x2B: "IM_LOAD_IMMEDIATE", 0x2C: "IM_STORE", 0x2D: "SET_CONSTANT",
    0x2E: "LOAD_CONSTANT_CONTEXT", 0x2F: "LOAD_ALU_CONSTANT",
    0x34: "DRAW_INDX_BIN", 0x35: "DRAW_INDX_2_BIN", 0x36: "DRAW_INDX_2",
    0x37: "INDIRECT_BUFFER_PFD", 0x3B: "INVALIDATE_STATE", 0x3C: "WAIT_REG_MEM",
    0x3D: "MEM_WRITE", 0x3E: "REG_TO_MEM", 0x3F: "INDIRECT_BUFFER",
    0x44: "COND_EXEC", 0x45: "COND_WRITE", 0x46: "EVENT_WRITE", 0x48: "ME_INIT",
    0x4A: "SET_SHADER_BASES", 0x4B: "SET_BIN_BASE_OFFSET", 0x4F: "MEM_WRITE_CNTR",
    0x50: "SET_BIN_MASK", 0x51: "SET_BIN_SELECT", 0x52: "WAIT_REG_EQ",
    0x53: "WAIT_REG_GTE", 0x54: "INTERRUPT", 0x55: "SET_CONSTANT2",
    0x56: "SET_SHADER_CONSTANTS", 0x58: "EVENT_WRITE_SHD", 0x59: "EVENT_WRITE_CFL",
    0x5A: "EVENT_WRITE_EXT", 0x5B: "EVENT_WRITE_ZPD", 0x5C: "WAIT_UNTIL_READ",
    0x5D: "WAIT_IB_PFD_COMPLETE", 0x5E: "CONTEXT_UPDATE",
}

# Tipos do SET_CONSTANT (dword seguinte ao cabecalho: tipo << 16 | indice).
SET_CONSTANT_TYPES = {0: "ALU", 1: "FETCH", 2: "BOOL", 3: "LOOP", 4: "REGISTERS"}
SET_CONSTANT_BASE = {0: 0x4000, 1: 0x4800, 2: 0x4900, 3: 0x4908, 4: 0x2000}

# Imports do kernel que denunciam funcoes da biblioteca grafica.
GPU_IMPORTS = {
    "VdSwap": "Swap/Present",
    "VdInitializeRingBuffer": "CreateDevice",
    "VdInitializeEngines": "CreateDevice",
    "VdEnableRingBufferRPtrWriteBack": "CreateDevice",
    "VdSetGraphicsInterruptCallback": "CreateDevice",
    "VdSetSystemCommandBufferGpuIdentifierAddress": "CreateDevice",
    "VdGetSystemCommandBuffer": "CreateDevice/Swap",
    "VdInitializeScalerCommandBuffer": "CreateDevice/ResetDevice",
    "VdCallGraphicsNotificationRoutines": "CreateDevice/ResetDevice",
    "VdSetDisplayMode": "SetDisplayMode",
    "VdQueryVideoMode": "QueryVideoMode",
    "VdQueryVideoFlags": "QueryVideoMode",
    "VdGetCurrentDisplayInformation": "display info",
    "VdGetCurrentDisplayGamma": "SetGammaRamp",
    "VdRetrainEDRAM": "EDRAM",
    "VdRetrainEDRAMWorker": "EDRAM",
    "VdIsHSIOTrainingSucceeded": "EDRAM",
    "VdPersistDisplay": "PersistDisplay",
    "VdShutdownEngines": "Release device",
    "VdEnableDisableClockGating": "power",
    "VdGlobalDevice": "device global",
    "VdGlobalXamDevice": "device global",
    "VdGpuClockInMHz": "device global",
    "VdHSIOCalibrationLock": "EDRAM",
    "XPhysicalAlloc": "physical alloc (resource create)",
    "XPhysicalAllocEx": "physical alloc (resource create)",
    "XPhysicalFree": "physical free (resource release)",
    "MmAllocatePhysicalMemoryEx": "physical alloc (resource create)",
    "MmFreePhysicalMemory": "physical free (resource release)",
    "MmGetPhysicalAddress": "physical address",
    "MmQueryAddressProtect": "memory protect",
    "MmSetAddressProtect": "memory protect",
    "KeWaitForSingleObject": "wait",
    "KeWaitForMultipleObjects": "wait",
    "KeSetEvent": "event",
    "KeResetEvent": "event",
    "KeQueryPerformanceCounter": "timing",
    "KeQueryPerformanceFrequency": "timing",
    "KeEnterCriticalRegion": "crit",
    "KeLeaveCriticalRegion": "crit",
    "KeAcquireSpinLockAtRaisedIrql": "lock",
    "KeReleaseSpinLockFromRaisedIrql": "lock",
    "KfAcquireSpinLock": "lock",
    "KfReleaseSpinLock": "lock",
    "KeRaiseIrqlToDpcLevel": "lock",
    "KfLowerIrql": "lock",
}
STRONG_GPU_IMPORTS = {k for k in GPU_IMPORTS if k.startswith("Vd")}

# Registos interessantes escritos por type-0 ou SET_CONSTANT(REGISTERS) -> pista de nome.
REG_HINTS = [
    ("RB_COPY_", "Resolve"),
    ("RB_COLOR_CLEAR", "Clear"),
    ("RB_DEPTH_CLEAR", "Clear"),
    ("RB_SURFACE_INFO", "SetRenderTarget/SetDepthStencil (pending)"),
    ("RB_COLOR_INFO", "SetRenderTarget (pending)"),
    ("RB_COLOR1_INFO", "SetRenderTarget (pending)"),
    ("RB_DEPTH_INFO", "SetDepthStencilSurface (pending)"),
    ("RB_DEPTHCONTROL", "render states (pending)"),
    ("RB_BLENDCONTROL", "render states (pending)"),
    ("RB_COLORCONTROL", "render states (pending)"),
    ("RB_MODECONTROL", "Resolve/Clear mode"),
    ("PA_SC_WINDOW_SCISSOR", "SetScissorRect (pending)"),
    ("PA_SC_SCREEN_SCISSOR", "SetScissorRect (pending)"),
    ("PA_CL_VPORT", "SetViewport (pending)"),
    ("PA_SU_SC_MODE_CNTL", "render states (pending)"),
    ("SQ_PROGRAM_CNTL", "Set*Shader (pending)"),
    ("SQ_CONTEXT_MISC", "Set*Shader (pending)"),
    ("SQ_INTERPOLATOR_CNTL", "Set*Shader (pending)"),
    ("VGT_DRAW_INITIATOR", "Draw"),
    ("VGT_DMA_BASE", "Draw (index buffer)"),
    ("VGT_INDX_OFFSET", "Draw (base vertex)"),
    ("VGT_MAX_VTX_INDX", "Draw"),
    ("CP_RB_WPTR", "Kickoff"),
]

# ---------------------------------------------------------------------------
# Leitura do codigo gerado
# ---------------------------------------------------------------------------

RE_FUNC_START = re.compile(
    r"^\s*(?:DEFINE_REX_FUNC|PPC_FUNC_IMPL)\(\s*(?:__imp__)?([A-Za-z_]\w*)\s*\)")
RE_HEXADDR = re.compile(r"(?:^|_)([0-9A-Fa-f]{8})$")
RE_LABEL = re.compile(r"^loc_([0-9A-Fa-f]+):")
RE_ASM = re.compile(r"^\t// ([a-z][\w.+-]*)(?:\s+(.*))?$")
RE_LR = re.compile(r"ctx\.lr = 0x([0-9A-Fa-f]+);")
RE_CALL = re.compile(r"^\s*([A-Za-z_]\w*)\(ctx, base\);")
RE_INDIRECT = re.compile(r"REX_CALL_INDIRECT_FUNC")
ASM_SKIP = ("UNIMPLEMENTED", "ERROR", "STUB")


@dataclass
class Insn:
    addr: int
    mn: str
    ops: list


@dataclass
class Func:
    name: str
    addr: int
    file: str
    insns: list = field(default_factory=list)
    calls: list = field(default_factory=list)          # (callsite, nome)
    indirect_calls: int = 0
    tail_calls: list = field(default_factory=list)
    # resultados da analise
    type3: collections.Counter = field(default_factory=collections.Counter)
    type0: collections.Counter = field(default_factory=collections.Counter)
    setconst: collections.Counter = field(default_factory=collections.Counter)
    imports: collections.Counter = field(default_factory=collections.Counter)
    callers: set = field(default_factory=set)

    @property
    def size(self) -> int:
        return len(self.insns) * 4


def name_to_addr(name: str) -> int | None:
    m = RE_HEXADDR.search(name)
    return int(m.group(1), 16) if m else None


def split_ops(s: str | None) -> list:
    if not s:
        return []
    return [o.strip() for o in s.split(",") if o.strip()]


def parse_generated(root: str) -> dict:
    funcs: dict[int, Func] = {}
    files = []
    for dirpath, _dirs, names in os.walk(root):
        for n in names:
            if n.endswith(".cpp"):
                files.append(os.path.join(dirpath, n))
    files.sort()
    if not files:
        sys.exit(f"ERRO: nenhum .cpp em {root}")

    for path in files:
        cur: Func | None = None
        addr = 0
        depth = 0
        with open(path, "r", encoding="utf-8", errors="replace") as fh:
            for line in fh:
                if cur is None:
                    m = RE_FUNC_START.match(line)
                    if m:
                        name = m.group(1)
                        a = name_to_addr(name)
                        if a is None:
                            continue
                        cur = funcs.get(a) or Func(name=name, addr=a, file=os.path.basename(path))
                        if cur.insns:  # definicao repetida (nao devia acontecer)
                            cur.insns.clear()
                            cur.calls.clear()
                        funcs[a] = cur
                        addr = a
                        depth = line.count("{") - line.count("}")
                        if depth <= 0 and "{" in line:
                            cur = None
                    continue

                depth += line.count("{") - line.count("}")
                m = RE_LABEL.match(line)
                if m:
                    addr = int(m.group(1), 16)
                else:
                    m = RE_ASM.match(line)
                    if m and not m.group(1).startswith(ASM_SKIP):
                        rest = m.group(2) or ""
                        if not rest.startswith(ASM_SKIP):
                            cur.insns.append(Insn(addr, m.group(1), split_ops(rest)))
                            addr += 4
                    else:
                        m = RE_LR.search(line)
                        if m and cur.insns:
                            # 'ctx.lr = X' vem depois do comentario do bl: o bl esta em X-4
                            real = int(m.group(1), 16) - 4
                            cur.insns[-1].addr = real
                            addr = real + 4
                        m = RE_CALL.match(line)
                        if m:
                            callee = m.group(1)
                            site = cur.insns[-1].addr if cur.insns else cur.addr
                            if cur.insns and cur.insns[-1].mn.startswith("b") and \
                                    not cur.insns[-1].mn.startswith("bl"):
                                cur.tail_calls.append((site, callee))
                            cur.calls.append((site, callee))
                        elif RE_INDIRECT.search(line):
                            cur.indirect_calls += 1
                if depth <= 0:
                    cur = None
    return funcs


# ---------------------------------------------------------------------------
# Constantes em registos (por bloco linear) e cabecalhos PM4
# ---------------------------------------------------------------------------

def parse_imm(s: str) -> int | None:
    s = s.strip()
    try:
        if s.lower().startswith(("0x", "-0x")):
            return int(s, 16)
        return int(s, 10)
    except ValueError:
        return None


def reg_num(s: str) -> int | None:
    s = s.strip()
    if s == "0":
        return 0
    if s.startswith("r") and s[1:].isdigit():
        return int(s[1:])
    return None


def u32(v: int) -> int:
    return v & 0xFFFFFFFF


STORE_MN = {"stw", "stwu", "stwx", "stwux", "stwbrx", "stwcx."}
KILL_ALL_MN_PREFIX = ("bl", "bctrl", "blrl")


def scan_constants(fn: Func, regnames: dict):
    """Segue constantes de 32 bits em registos e regista os valores guardados."""
    known: dict[int, int] = {}
    labels = {i.addr for i in fn.insns}  # nao temos os labels a parte; reset em ramos
    last_header = None  # (opcode, insn index) para ler o dword do SET_CONSTANT
    stored_seq = []
    for idx, ins in enumerate(fn.insns):
        mn, ops = ins.mn, ins.ops
        try:
            if mn == "li" and len(ops) == 2:
                d, v = reg_num(ops[0]), parse_imm(ops[1])
                if d is not None and v is not None:
                    known[d] = u32(v)
                continue
            if mn == "lis" and len(ops) == 2:
                d, v = reg_num(ops[0]), parse_imm(ops[1])
                if d is not None and v is not None:
                    known[d] = u32(v << 16)
                continue
            if mn in ("addi", "addis", "subi") and len(ops) == 3:
                d, s, v = reg_num(ops[0]), reg_num(ops[1]), parse_imm(ops[2])
                if d is None:
                    continue
                if v is None or s is None:
                    known.pop(d, None)
                    continue
                base = 0 if s == 0 else known.get(s)
                if base is None:
                    known.pop(d, None)
                    continue
                if mn == "addis":
                    v <<= 16
                if mn == "subi":
                    v = -v
                known[d] = u32(base + v)
                continue
            if mn in ("ori", "oris", "xori") and len(ops) == 3:
                d, s, v = reg_num(ops[0]), reg_num(ops[1]), parse_imm(ops[2])
                if d is None:
                    continue
                if s is None or v is None or s not in known:
                    known.pop(d, None)
                    continue
                v &= 0xFFFF
                if mn == "oris":
                    v <<= 16
                known[d] = u32(known[s] ^ v) if mn == "xori" else u32(known[s] | v)
                continue
            if mn == "mr" and len(ops) == 2:
                d, s = reg_num(ops[0]), reg_num(ops[1])
                if d is not None:
                    if s is not None and s in known:
                        known[d] = known[s]
                    else:
                        known.pop(d, None)
                continue
            if mn in STORE_MN and ops:
                s = reg_num(ops[0])
                if s is not None and s in known:
                    v = known[s]
                    stored_seq.append(v)
                    classify_store(fn, v, regnames, last_header)
                    if (v >> 30) == 3 and ((v >> 8) & 0x7F) in PM4_TYPE3:
                        last_header = ((v >> 8) & 0x7F, idx)
                    elif last_header and idx - last_header[1] > 12:
                        last_header = None
                continue
            if mn.startswith(KILL_ALL_MN_PREFIX):
                # chamada: r0, r3-r12 sao volateis
                for r in [0] + list(range(3, 13)):
                    known.pop(r, None)
                continue
            # qualquer outra instrucao com destino registo: esquece-o
            if ops and not mn.startswith(("st", "cmp", "b", "tw", "dcb", "sync", "lwsync",
                                          "eieio", "isync", "mt", "nop")):
                d = reg_num(ops[0])
                if d is not None:
                    known.pop(d, None)
            if mn.startswith("b") and not mn.startswith("bl"):
                # fim de bloco linear: mantem (lis/ori quase sempre adjacentes)
                pass
        except Exception:  # noqa: BLE001 - desmontagem inesperada, ignora
            continue


def classify_store(fn: Func, v: int, regnames: dict, last_header):
    typ = v >> 30
    if typ == 3:
        op = (v >> 8) & 0x7F
        if op in PM4_TYPE3:
            count = ((v >> 16) & 0x3FFF) + 1
            fn.type3[PM4_TYPE3[op]] += 1
            return
    if last_header and last_header[0] in (0x2D, 0x55):
        ctype = (v >> 16) & 0xFF
        index = v & 0x7FF
        if ctype in SET_CONSTANT_TYPES and v < 0x00100000:
            label = SET_CONSTANT_TYPES[ctype]
            if ctype == 4:
                reg = SET_CONSTANT_BASE[4] + index
                label += ":" + regnames.get(reg, f"0x{reg:04X}")
            fn.setconst[label] += 1
            return
    if typ == 0 and v >= 0x10000:
        reg = v & 0x7FFF
        cnt = ((v >> 16) & 0x3FFF) + 1
        if reg in regnames and cnt <= 64 and reg >= 0x0400:
            fn.type0[regnames[reg]] += 1


def load_register_names(sdk_root: str | None) -> dict:
    cands = []
    if sdk_root:
        cands.append(os.path.join(sdk_root, "include/rex/graphics/register_table.inc"))
    here = os.path.dirname(os.path.abspath(__file__))
    cands.append(os.path.join(here, "..", "external/rexglue-sdk-0.10/include/rex/graphics/register_table.inc"))
    for p in cands:
        if os.path.isfile(p):
            names = {}
            rx = re.compile(r"XE_GPU_REGISTER\(0x([0-9A-Fa-f]+),\s*\w+,\s*(\w+)\)")
            with open(p) as fh:
                for line in fh:
                    m = rx.search(line)
                    if m:
                        names[int(m.group(1), 16)] = m.group(2)
            return names
    print("aviso: register_table.inc nao encontrado; escritas de registos ficam por nome hex",
          file=sys.stderr)
    return {}


# ---------------------------------------------------------------------------
# Analise global
# ---------------------------------------------------------------------------

def import_name(callee: str) -> str | None:
    if callee.startswith("__imp__") and name_to_addr(callee) is None:
        return callee[len("__imp__"):]
    return None


def analyse(funcs: dict, regnames: dict, anchors: list[int]):
    by_name = {f.name: f for f in funcs.values()}
    # imports e callers
    for f in funcs.values():
        for _site, callee in f.calls:
            imp = import_name(callee)
            if imp:
                f.imports[imp] += 1
                continue
            a = name_to_addr(callee)
            if a is not None and a in funcs:
                funcs[a].callers.add(f.addr)
        scan_constants(f, regnames)

    # semente: funcoes com PM4, imports Vd* ou chamadas as ancoras (espera do ring)
    seeds = set()
    anchor_set = set(anchors)
    for f in funcs.values():
        callees = {name_to_addr(c) for _s, c in f.calls}
        if f.type3 or f.setconst or (set(f.imports) & STRONG_GPU_IMPORTS) or (callees & anchor_set):
            seeds.add(f.addr)
    seeds |= {a for a in anchors if a in funcs}
    return seeds


def find_cluster(funcs: dict, seeds: set, max_gap: int):
    """Intervalo contiguo de enderecos mais denso em sementes (a biblioteca D3D)."""
    if not seeds:
        return None
    s = sorted(seeds)
    groups = [[s[0]]]
    for a in s[1:]:
        if a - groups[-1][-1] <= max_gap:
            groups[-1].append(a)
        else:
            groups.append([a])
    best = max(groups, key=len)
    lo, hi = best[0], best[-1]
    # alarga ate a ultima funcao antes da proxima semente fora do grupo
    return lo, hi, groups


def suggest(f: Func, in_range, funcs) -> list:
    tips = []
    imps = set(f.imports)
    for imp in sorted(imps):
        if imp in GPU_IMPORTS and imp.startswith(("Vd", "XPhysical", "MmAllocate", "MmFree")):
            tips.append(GPU_IMPORTS[imp])
    t3 = f.type3
    if t3.get("DRAW_INDX"):
        tips.append("Draw(Indexed)Vertices")
    if t3.get("DRAW_INDX_2"):
        tips.append("DrawVerticesUP / quad (Clear, Resolve)")
    if t3.get("IM_LOAD") or t3.get("IM_LOAD_IMMEDIATE"):
        tips.append("carga de shader (Set*Shader pending)")
    if t3.get("LOAD_ALU_CONSTANT") or f.setconst.get("ALU"):
        tips.append("constantes ALU (SetVertex/PixelShaderConstantF pending)")
    if f.setconst.get("FETCH"):
        tips.append("fetch constants (SetTexture/SetStreamSource pending)")
    if f.setconst.get("BOOL") or f.setconst.get("LOOP"):
        tips.append("constantes bool/loop")
    if t3.get("VIZ_QUERY") or t3.get("EVENT_WRITE_ZPD"):
        tips.append("query de oclusao")
    if t3.get("EVENT_WRITE") or t3.get("EVENT_WRITE_SHD") or t3.get("MEM_WRITE"):
        tips.append("fence / evento")
    if t3.get("WAIT_REG_MEM") or t3.get("WAIT_REG_EQ") or t3.get("WAIT_REG_GTE"):
        tips.append("espera da GPU (BlockOnFence?)")
    if t3.get("INDIRECT_BUFFER") or t3.get("INDIRECT_BUFFER_PFD"):
        tips.append("command buffer / kickoff")
    if t3.get("INVALIDATE_STATE"):
        tips.append("invalidate state")
    if t3.get("ME_INIT") or t3.get("SET_SHADER_BASES"):
        tips.append("CreateDevice / init")
    regs = set(f.type0) | {k.split(":", 1)[1] for k in f.setconst if ":" in k}
    for prefix, hint in REG_HINTS:
        if any(r.startswith(prefix) for r in regs):
            tips.append(hint)
    if in_range and in_range(f.addr) and f.callers and not any(in_range(c) for c in f.callers):
        tips.insert(0, "API PUBLICA (so chamada pelo jogo)")
    # dedup mantendo a ordem
    out, seen = [], set()
    for t in tips:
        if t not in seen:
            seen.add(t)
            out.append(t)
    return out


def fmt_counter(c: collections.Counter, n=8) -> str:
    return ", ".join(f"{k}x{v}" if v > 1 else k for k, v in c.most_common(n))


def write_report(funcs, seeds, cluster, out_dir, anchors):
    os.makedirs(out_dir, exist_ok=True)
    lo, hi, groups = cluster if cluster else (0, 0, [])

    def in_range(a):
        return lo <= a <= hi

    rows = []
    for a in sorted(funcs):
        f = funcs[a]
        inside = in_range(a)
        if not (inside or a in seeds):
            continue
        rows.append(f)

    js = {
        "range": [lo, hi],
        "anchors": anchors,
        "functions": [],
    }
    lines = []
    lines.append("# Mapa D3D (gerado por tools/d3d_map.py)\n")
    lines.append(f"Funcoes analisadas: {len(funcs)}; sementes (PM4/Vd*/ancoras): {len(seeds)}.\n")
    if cluster:
        lines.append(f"Intervalo mais denso (biblioteca D3D provavel): **0x{lo:08X}–0x{hi:08X}** "
                     f"({sum(1 for a in seeds if lo <= a <= hi)} sementes; usar --max-gap para o alargar).\n")
        lines.append("Outros grupos de sementes (fora do intervalo), do maior para o menor:\n")
        for g in sorted(groups, key=len, reverse=True):
            if g[0] == lo or len(g) < 2:
                continue
            lines.append(f"- 0x{g[0]:08X}–0x{g[-1]:08X}: {len(g)} sementes")
        lines.append("")
    lines.append("Legenda: *pub* = chamada de fora do intervalo (API publica); "
                 "PM4 = pacotes type-3 escritos; SETC = dword de SET_CONSTANT; "
                 "REG = escritas type-0.\n")
    lines.append("| funcao | tam | chamadores (fora/dentro) | pub | PM4 | SETC / REG | imports | chama | sugestao |")
    lines.append("|---|---|---|---|---|---|---|---|---|")
    for f in rows:
        outside = sum(1 for c in f.callers if not in_range(c))
        inside = len(f.callers) - outside
        callees = []
        for _s, c in f.calls:
            a = name_to_addr(c)
            if a is not None and in_range(a) and c not in callees:
                callees.append(c)
        tips = suggest(f, in_range, funcs)
        regs = fmt_counter(f.setconst, 4)
        if f.type0:
            regs = (regs + "; " if regs else "") + fmt_counter(f.type0, 4)
        imps = fmt_counter(collections.Counter({k: v for k, v in f.imports.items()
                                                if k in GPU_IMPORTS}), 4)
        pub = "pub" if (outside and in_range(f.addr)) else ""
        lines.append(
            f"| {f.name} | {f.size} | {outside}/{inside} | {pub} | {fmt_counter(f.type3, 5)} | "
            f"{regs} | {imps} | {', '.join(callees[:5])}{'…' if len(callees) > 5 else ''} | "
            f"{'; '.join(tips)} |")
        js["functions"].append({
            "name": f.name, "addr": f.addr, "size": f.size,
            "callers_outside": outside, "callers_inside": inside,
            "public": bool(pub), "pm4": dict(f.type3), "setconst": dict(f.setconst),
            "type0": dict(f.type0), "imports": dict(f.imports),
            "callees_in_range": callees, "indirect_calls": f.indirect_calls,
            "suggest": tips, "file": f.file,
        })

    md = os.path.join(out_dir, "d3d_map.md")
    with open(md, "w") as fh:
        fh.write("\n".join(lines) + "\n")
    with open(os.path.join(out_dir, "d3d_map.json"), "w") as fh:
        json.dump(js, fh, indent=1)
    return md, len(rows)


def dump_function(funcs, name):
    a = name_to_addr(name) if not name.startswith("0x") else int(name, 16)
    f = funcs.get(a)
    if not f:
        sys.exit(f"funcao {name} nao encontrada")
    print(f"; {f.name} ({f.file}) {f.size} bytes, chamadores: "
          + ", ".join(f"sub_{c:08X}" for c in sorted(f.callers)))
    calls = dict(f.calls)
    for ins in f.insns:
        extra = f"   -> {calls[ins.addr]}" if ins.addr in calls else ""
        print(f"{ins.addr:08X}  {ins.mn:8s} {','.join(ins.ops)}{extra}")


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    root = os.path.normpath(os.path.join(here, ".."))
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--generated", default=os.path.join(root, "external/dbz3/generated"))
    ap.add_argument("--sdk", default=os.path.join(root, "external/rexglue-sdk-0.10"))
    ap.add_argument("--out", default=os.path.join(root, "perf/d3d"))
    ap.add_argument("--anchor", action="append", default=[],
                    help="funcao conhecida da biblioteca D3D (ex. sub_82235918, a espera do ring buffer)")
    ap.add_argument("--max-gap", type=lambda s: int(s, 0), default=0x4000,
                    help="distancia maxima entre sementes do mesmo grupo (bytes)")
    ap.add_argument("--dump", help="imprime a desmontagem de uma funcao (sub_XXXXXXXX ou 0x...)")
    args = ap.parse_args()

    anchors = [name_to_addr(a) for a in (args.anchor or ["sub_82235918", "sub_82231110"])]
    anchors = [a for a in anchors if a]

    funcs = parse_generated(args.generated)
    if args.dump:
        for f in funcs.values():
            for _s, callee in f.calls:
                a = name_to_addr(callee)
                if a in funcs:
                    funcs[a].callers.add(f.addr)
        dump_function(funcs, args.dump)
        return
    regnames = load_register_names(args.sdk)
    seeds = analyse(funcs, regnames, anchors)
    cluster = find_cluster(funcs, seeds, args.max_gap)
    md, n = write_report(funcs, seeds, cluster, args.out, anchors)
    print(f"{len(funcs)} funcoes lidas, {len(seeds)} sementes, {n} no relatorio -> {md}")
    if cluster:
        print(f"intervalo D3D provavel: 0x{cluster[0]:08X}-0x{cluster[1]:08X}")


if __name__ == "__main__":
    main()
