#!/usr/bin/env python3
"""Texturas lidas de volta numa captura (registos TEXTURE, patch 0068).

Para cada textura carregada no 1.o frame da captura ha duas leituras da GPU:
  etapa 0 = saida do shader de carga (buffer, antes da copia para a imagem)
  etapa 1 = nivel 0 da imagem Vulkan depois da copia
Este script compara as duas (se diferem, a copia buffer -> imagem do driver estragou-as) e
grava PNGs das texturas sem compressao (etapa 0 e 1 lado a lado), para ver se a carga ja saiu
errada (comparar com a mesma captura feita com o driver do sistema).

  tools/dfc_textures.py captura.dfc [--png pasta] [--compare outra.dfc]
"""
import argparse
import os
import struct
import sys

VK_NAMES = {37: 'R8G8B8A8_UNORM', 44: 'B8G8R8A8_UNORM', 9: 'R8_UNORM', 16: 'R8G8_UNORM',
            97: 'R16G16B16A16_SFLOAT', 91: 'R16G16B16A16_UNORM', 64: 'A2B10G10R10_UNORM',
            100: 'R32_SFLOAT', 131: 'BC1_RGBA_UNORM', 135: 'BC2_UNORM', 137: 'BC3_UNORM',
            139: 'BC4_UNORM', 141: 'BC5_UNORM', 4: 'R4G4B4A4_UNORM', 7: 'R5G6B5_UNORM',
            83: 'R16G16_UNORM', 77: 'R16_UNORM', 122: 'B10G11R11_UFLOAT'}
BPP = {37: 4, 44: 4, 9: 1, 16: 2, 97: 8, 91: 8, 64: 4, 100: 4, 4: 2, 7: 2, 83: 4, 77: 2, 122: 4}


def records(path):
    data = open(path, 'rb').read()
    if data[:4] != b'DFC1':
        sys.exit('nao e uma captura DFC1')
    off = 4
    while off + 8 <= len(data):
        typ, size = struct.unpack_from('<II', data, off)
        off += 8
        if typ == 9:
            h = struct.unpack_from('<9I', data, off)
            yield h, data[off + 36:off + 36 + h[8]]
        off += size


def load(path):
    tex = {}
    for h, blob in records(path):
        stage, base, fmt, w, hgt, vkf, row_bytes, flags, n = h
        key = (base, fmt, w, hgt)
        tex.setdefault(key, {})[stage] = (vkf, row_bytes, flags, blob)
    return tex


def save_png(path, vkf, w, h, row_texels, blob):
    try:
        from PIL import Image
    except ImportError:
        return False
    bpp = BPP.get(vkf)
    if bpp not in (1, 4):
        return False
    row = row_texels * bpp
    rows = []
    for y in range(h):
        rows.append(blob[y * row:y * row + w * bpp])
    raw = b''.join(rows)
    if len(raw) < w * h * bpp:
        return False
    mode = 'L' if bpp == 1 else 'RGBA'
    img = Image.frombytes(mode, (w, h), raw)
    if vkf == 44:
        r, g, b, a = img.split()
        img = Image.merge('RGBA', (b, g, r, a))
    img.save(path)
    return True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('path')
    ap.add_argument('--png')
    ap.add_argument('--compare', help='a mesma cena capturada com outro driver')
    a = ap.parse_args()
    tex = load(a.path)
    other = load(a.compare) if a.compare else {}
    if a.png:
        os.makedirs(a.png, exist_ok=True)
    bad_copy = 0
    for key in sorted(tex):
        base, fmt, w, h = key
        st = tex[key]
        s0, s1 = st.get(0), st.get(1)
        vkf = (s0 or s1)[0]
        flags = (s0 or s1)[2]
        line = '%08X fmt=%-2d %4dx%-4d %-18s tiled=%d endian=%d mips=%d' % (
            base, fmt, w, h, VK_NAMES.get(vkf, str(vkf)), flags & 0xFF, (flags >> 8) & 0xFF,
            (flags >> 16) & 0xFF)
        if s0 and s1:
            # Compara so as linhas da imagem (a etapa 1 nao escreve o enchimento das linhas).
            bpp = BPP.get(vkf)
            if bpp:
                row = s0[1]
                n = min(len(s0[3]), len(s1[3]))
                used = w * bpp
                same = sum(1 for y in range(h) if y * row + used <= n and
                           s0[3][y * row:y * row + used] == s1[3][y * row:y * row + used])
                pct = 100.0 * same / max(h, 1)
            else:
                pct = 100.0 if s0[3] == s1[3] else 0.0
            line += '  carga==imagem: %5.1f%% das linhas' % pct
            if pct < 99.9:
                bad_copy += 1
                line += '  <-- COPIA PARA A IMAGEM ERRADA'
        nz = sum(1 for b in (s0 or s1)[3][::61] if b) / max(1, len((s0 or s1)[3][::61]))
        line += '  nao-zero=%.0f%%' % (100 * nz)
        if key in other and 0 in other[key] and s0:
            line += '  carga igual ao outro driver: %s' % ('sim' if other[key][0][3] == s0[3] else 'NAO')
        print(line)
        if a.png:
            for stage, s in st.items():
                row_texels = s[1] // BPP[vkf] if vkf in BPP else 0
                if row_texels:
                    save_png(os.path.join(a.png, '%08X_f%d_%dx%d_e%d.png' % (base, fmt, w, h, stage)),
                             vkf, w, h, row_texels, s[3])
    print('\n%d texturas, %d com a copia buffer -> imagem diferente da carga' % (len(tex), bad_copy))


if __name__ == '__main__':
    main()
