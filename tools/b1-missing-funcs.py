#!/usr/bin/env python3
"""Ponteiros para codigo que o codegen do Budokai 1 nao separou em funcoes.

Le a imagem PE (out/host/xex-image <xex> <img>) e o codigo gerado (DEFINE_REX_FUNC), procura nas seccoes de dados
palavras alinhadas que apontam para o codigo e nao sao inicio de funcao, e mostra as que parecem inicio de funcao
(a instrucao anterior e blr, b, ou padding). Uso: b1-missing-funcs.py <img> <pasta generated> [--toml]
"""
import re, struct, sys, glob

img = open(sys.argv[1], 'rb').read()
base = 0x82000000
# Cabecalho PE: seccoes
pe = struct.unpack_from('<I', img, 0x3C)[0]
nsec = struct.unpack_from('<H', img, pe + 6)[0]
optsz = struct.unpack_from('<H', img, pe + 20)[0]
secs = []
for i in range(nsec):
    o = pe + 24 + optsz + i * 40
    name = img[o:o + 8].rstrip(b'\0').decode()
    vsz, va, rsz, raw = struct.unpack_from('<IIII', img, o + 8)
    ch = struct.unpack_from('<I', img, o + 36)[0]
    secs.append((name, base + va, vsz, ch))
code = [(s[1], s[1] + s[2]) for s in secs if s[3] & 0x20]
starts = set()
for f in glob.glob(sys.argv[2] + '/*.cpp'):
    t = open(f).read()
    for m in re.finditer(r'DEFINE_REX_FUNC\(sub_([0-9A-F]{8})\)', t):
        starts.add(int(m.group(1), 16))
    # importacoes do kernel (thunks no fim do .text), ja registadas pelo codegen
    for m in re.finditer(r'SetFunction\(0x([0-9A-F]{8}), __imp__', t):
        starts.add(int(m.group(1), 16))
def word(va): return struct.unpack_from('>I', img, va - base)[0]
def incode(v): return any(a <= v < b for a, b in code) and v % 4 == 0
cand = {}
for name, va, vsz, ch in secs:
    if ch & 0x20: continue
    for a in range(va, va + vsz - 3, 4):
        if a - base + 4 > len(img): break
        v = word(a)
        if incode(v) and v not in starts:
            cand.setdefault(v, []).append(a)
out = []
for v, refs in sorted(cand.items()):
    prev = word(v - 4)
    # a anterior termina uma funcao: blr, bctr, b, ou padding
    ok = prev in (0x4E800020, 0x4E800420, 0) or (prev >> 26) == 18 and (prev & 3) == 0
    # ou esta numa tabela (vtable) cujos vizinhos ja sao inicios de funcao
    neigh = any(word(r + d) in starts for r in refs for d in (-4, 4))
    scope = all(0x82067000 <= r < 0x82067E00 for r in refs)  # tabelas de excecao: handlers dentro de funcoes
    if (ok or neigh) and not scope:
        out.append((v, refs, neigh))
if '--toml' in sys.argv:
    for v, refs, n in out:
        print('0x%08X = { name = "rex_sub_%08X" }' % (v, v))
else:
    for v, refs, n in out:
        print('%08X prev=%08X refs=%s viz=%s' % (v, word(v - 4), ' '.join('%08X' % r for r in refs[:4]), n))
    print(len(out), 'candidatos', file=sys.stderr)
