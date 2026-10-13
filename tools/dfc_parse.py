#!/usr/bin/env python3
"""Le uma captura de frames (logs/capture/*.dfc, patch 0027) e imprime o mapa de passes:
draws agrupados por render target, resolves, texturas lidas e shaders usados.

  tools/dfc_parse.py captura.dfc [--regs external/rexglue-sdk-0.10/include/rex/graphics/register_table.inc]
                                  [--dump-mem pasta] [--dump-shaders pasta]
"""
import argparse
import os
import re
import struct
import sys

REG_RANGES = ((0x2000, 0x3000), (0x4000, 0x4A00))

# Indices fixos (ver register_table.inc); --regs substitui/alarga esta lista.
REGS = {
    'RB_SURFACE_INFO': 0x2000, 'RB_COLOR_INFO': 0x2001, 'RB_DEPTH_INFO': 0x2002,
    'RB_COLOR1_INFO': 0x2003, 'RB_COLOR2_INFO': 0x2004, 'RB_COLOR3_INFO': 0x2005,
    'PA_SC_WINDOW_OFFSET': 0x2080, 'PA_SC_WINDOW_SCISSOR_TL': 0x2081, 'PA_SC_WINDOW_SCISSOR_BR': 0x2082,
    'PA_CL_VPORT_XSCALE': 0x210F, 'PA_CL_VPORT_XOFFSET': 0x2110, 'PA_CL_VPORT_YSCALE': 0x2111,
    'PA_CL_VPORT_YOFFSET': 0x2112, 'PA_CL_VPORT_ZSCALE': 0x2113, 'PA_CL_VPORT_ZOFFSET': 0x2114,
    'RB_COLORCONTROL': 0x2202, 'RB_DEPTHCONTROL': 0x2200, 'RB_BLENDCONTROL0': 0x2201,
    'RB_STENCILREFMASK': 0x210D, 'RB_COLOR_MASK': 0x2104, 'RB_BLEND_RED': 0x2105,
    'RB_MODECONTROL': 0x2208, 'RB_COPY_CONTROL': 0x2318, 'RB_COPY_DEST_BASE': 0x2319,
    'RB_COPY_DEST_PITCH': 0x231A, 'RB_COPY_DEST_INFO': 0x231B, 'RB_DEPTH_CLEAR': 0x231D,
    'RB_COLOR_CLEAR': 0x231E, 'PA_SU_SC_MODE_CNTL': 0x2205, 'PA_CL_CLIP_CNTL': 0x2204,
    'PA_CL_VTE_CNTL': 0x2206, 'SQ_PROGRAM_CNTL': 0x2180, 'SQ_CONTEXT_MISC': 0x2181,
    'VGT_DRAW_INITIATOR': 0x21FC, 'RB_SAMPLE_COUNT_CTL': 0x2324, 'RB_ALPHA_REF': 0x210E,
    'SHADER_CONSTANT_000_X': 0x4000, 'SHADER_CONSTANT_FETCH_00_0': 0x4800,
    'SHADER_CONSTANT_BOOL_000_031': 0x4900, 'SHADER_CONSTANT_LOOP_00': 0x4908,
}

PRIMS = {1: 'points', 2: 'lines', 3: 'line_strip', 4: 'tri_list', 5: 'tri_fan', 6: 'tri_strip',
         8: 'tri_w_flags', 9: 'rect_list', 12: 'line_loop', 13: 'quad_list', 14: 'quad_strip',
         15: 'polygon', 16: '2d_copy_rect_0', 17: '2d_copy_rect_1', 18: '2d_copy_rect_2',
         19: '2d_copy_rect_3', 20: '2d_fill_rect', 21: '2d_line', 22: 'line_list_2d',
         23: 'tri_strip_2d', 24: 'tri_list_2d', 25: 'tri_fan_2d', 26: 'quad_list_2d',
         27: 'quad_strip_2d', 28: 'polygon_2d'}
COLOR_FMT = {0: '8_8_8_8', 1: '8_8_8_8_GAMMA', 2: '2_10_10_10', 3: '2_10_10_10_FLOAT', 4: '16_16',
             5: '16_16_16_16', 6: '16_16_FLOAT', 7: '16_16_16_16_FLOAT', 10: '2_10_10_10_AS_10_10_10_10',
             12: '2_10_10_10_FLOAT_AS_16_16_16_16', 14: '32_FLOAT', 15: '32_32_FLOAT'}
TEX_FMT = {0: '1_REVERSE', 1: '1', 2: '8', 3: '1_5_5_5', 4: '5_6_5', 5: '6_5_5', 6: '8_8_8_8', 7: '2_10_10_10',
           8: '8_A', 9: '8_B', 10: '8_8', 11: 'Cr_Y1_Cb_Y0', 12: 'Y1_Cr_Y0_Cb', 14: '8_8_8_8_A', 15: '4_4_4_4',
           16: '10_11_11', 17: '11_11_10', 18: 'DXT1', 19: 'DXT2_3', 20: 'DXT4_5', 22: '24_8', 23: '24_8_FLOAT',
           24: '16', 25: '16_16', 26: '16_16_16_16', 27: '16_EXPAND', 28: '16_16_EXPAND', 29: '16_16_16_16_EXPAND',
           30: '16_FLOAT', 31: '16_16_FLOAT', 32: '16_16_16_16_FLOAT', 33: '32', 34: '32_32', 35: '32_32_32_32',
           36: '32_FLOAT', 37: '32_32_FLOAT', 38: '32_32_32_32_FLOAT', 39: '32_AS_8', 40: '32_AS_8_8',
           41: '16_MPEG', 42: '16_16_MPEG', 43: '8_INTERLACED', 44: '32_AS_8_INTERLACED', 45: '32_AS_8_8_INTERLACED',
           46: '16_INTERLACED', 47: '16_MPEG_INTERLACED', 48: '16_16_MPEG_INTERLACED', 49: 'DXN', 50: '8_8_8_8_AS_16_16_16_16',
           51: 'DXT1_AS_16_16_16_16', 52: 'DXT2_3_AS_16_16_16_16', 53: 'DXT4_5_AS_16_16_16_16',
           54: '2_10_10_10_AS_16_16_16_16', 55: '10_11_11_AS_16_16_16_16', 56: '11_11_10_AS_16_16_16_16',
           57: '32_32_32_FLOAT', 58: 'DXT3A', 59: 'DXT5A', 60: 'CTX1', 61: 'DXT3A_AS_1_1_1_1'}


def load_reg_names(path):
    if not path or not os.path.isfile(path):
        return
    for m in re.finditer(r'XE_GPU_REGISTER\((0x[0-9A-Fa-f]+),\s*\w+,\s*(\w+)\)', open(path).read()):
        REGS[m.group(2)] = int(m.group(1), 16)


def read_records(path):
    data = open(path, 'rb').read()
    if data[:4] != b'DFC1':
        sys.exit('nao e uma captura DFC1')
    off = 4
    while off + 8 <= len(data):
        typ, size = struct.unpack_from('<II', data, off)
        off += 8
        yield typ, data[off:off + size]
        off += size


def f32(v):
    return struct.unpack('<f', struct.pack('<I', v))[0]


class Frame:
    def __init__(self, index):
        self.index = index
        self.events = []  # ('draw', dict) | ('copy', dict)


def rt_key(regs):
    si = regs[REGS['RB_SURFACE_INFO']]
    ci = regs[REGS['RB_COLOR_INFO']]
    di = regs[REGS['RB_DEPTH_INFO']]
    pitch = si & 0x3FFF
    msaa = (si >> 16) & 3
    return (pitch, msaa, ci & 0xFFF, (ci >> 16) & 0xF, di & 0xFFF, (di >> 16) & 1)


def describe_rt(key):
    pitch, msaa, cbase, cfmt, dbase, dfmt = key
    return 'pitch=%d msaa=%dx color@%d %s depth@%d %s' % (
        pitch, 1 << msaa, cbase, COLOR_FMT.get(cfmt, str(cfmt)), dbase, 'D24FS8' if dfmt else 'D24S8')


def textures(regs):
    out = []
    base = REGS['SHADER_CONSTANT_FETCH_00_0']
    for i in range(32):
        d = [regs[base + i * 6 + k] for k in range(6)]
        if d[0] & 3 != 2:
            continue
        fmt = d[1] & 0x3F
        addr = (d[1] >> 12) & 0xFFFFF
        w = (d[2] & 0x1FFF) + 1
        h = ((d[2] >> 13) & 0x1FFF) + 1
        tiled = (d[0] >> 31) & 1
        out.append((i, addr << 12, w, h, TEX_FMT.get(fmt, str(fmt)), tiled))
    return out


def vfetches(regs):
    out = []
    base = REGS['SHADER_CONSTANT_FETCH_00_0']
    for i in range(96):
        d0, d1 = regs[base + i * 2], regs[base + i * 2 + 1]
        if d0 & 3 != 3:
            continue
        out.append((i, (d0 >> 2) << 2, (d1 >> 2) & 0xFFFFFF, d1 & 3))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('path')
    ap.add_argument('--regs')
    ap.add_argument('--dump-mem')
    ap.add_argument('--dump-shaders')
    ap.add_argument('--draws', action='store_true', help='lista todos os draws')
    a = ap.parse_args()
    load_reg_names(a.regs)

    regs = [0] * 0x5003
    frames = [Frame(0)]
    shaders = {}
    mem = []
    for typ, body in read_records(a.path):
        if typ == 7:
            print('#', body.decode('utf-8', 'replace'))
        elif typ == 1:
            n = struct.unpack_from('<I', body)[0]
            for i, v in struct.iter_unpack('<II', body[4:4 + n * 8]):
                regs[i] = v
        elif typ == 2:
            prim, ic, ibf, ibe, ibb, ibl, vsh, psh, di = struct.unpack_from('<IIIIIIQQI', body)
            frames[-1].events.append(('draw', dict(
                prim=PRIMS.get(prim, prim), count=ic, ib=(ibb, ibl, 32 if ibf else 16) if ibb else None,
                vs=vsh, ps=psh, rt=rt_key(regs), tex=textures(regs), vf=vfetches(regs),
                scissor=(regs[REGS['PA_SC_WINDOW_SCISSOR_TL']], regs[REGS['PA_SC_WINDOW_SCISSOR_BR']]),
                vport=tuple(f32(regs[REGS['PA_CL_VPORT_XSCALE'] + k]) for k in range(6)),
                blend=regs[REGS['RB_BLENDCONTROL0']], depth=regs[REGS['RB_DEPTHCONTROL']],
                colorctl=regs[REGS['RB_COLORCONTROL']], mask=regs[REGS['RB_COLOR_MASK']],
                pgm=regs[REGS['SQ_PROGRAM_CNTL']], i=di)))
        elif typ == 3:
            cc = regs[REGS['RB_COPY_CONTROL']]
            frames[-1].events.append(('copy', dict(
                src_select=cc & 7, command=(cc >> 20) & 3, clear=(cc >> 8) & 1,
                dest=regs[REGS['RB_COPY_DEST_BASE']], pitch=regs[REGS['RB_COPY_DEST_PITCH']] & 0x3FFF,
                dest_fmt=TEX_FMT.get((regs[REGS['RB_COPY_DEST_INFO']] >> 7) & 0x3F),
                rt=rt_key(regs), scissor=(regs[REGS['PA_SC_WINDOW_SCISSOR_TL']], regs[REGS['PA_SC_WINDOW_SCISSOR_BR']]))))
        elif typ == 4:
            fb, w, h, fi = struct.unpack_from('<IIII', body)
            frames[-1].events.append(('swap', dict(fb=fb, w=w, h=h)))
            frames.append(Frame(fi + 1))
        elif typ == 5:
            st, h, n = struct.unpack_from('<IQI', body)
            shaders[h] = (st, body[16:16 + n * 4])
        elif typ == 6:
            addr, n = struct.unpack_from('<II', body)
            mem.append((addr, body[8:8 + n]))

    print('shaders: %d  blocos de memoria: %d (%.1f MB)' % (len(shaders), len(mem), sum(len(b) for _, b in mem) / 1e6))
    for fr in frames:
        if not fr.events:
            continue
        print('\n=== frame %d: %d eventos' % (fr.index, len(fr.events)))
        cur = None
        n = 0
        vs_set, ps_set, tex_set = set(), set(), set()
        for kind, e in fr.events:
            if kind == 'draw':
                if e['rt'] != cur:
                    if cur is not None:
                        print('  pass: %d draws, vs=%d ps=%d texturas=%d' % (n, len(vs_set), len(ps_set), len(tex_set)))
                    cur = e['rt']
                    n = 0
                    vs_set, ps_set, tex_set = set(), set(), set()
                    print('-- RT %s scissor=(%d,%d)-(%d,%d)' % (describe_rt(cur), e['scissor'][0] & 0x7FFF, e['scissor'][0] >> 16 & 0x7FFF, e['scissor'][1] & 0x7FFF, e['scissor'][1] >> 16 & 0x7FFF))
                n += 1
                vs_set.add(e['vs'])
                ps_set.add(e['ps'])
                for t in e['tex']:
                    tex_set.add(t[1:])
                if a.draws:
                    print('   draw %4d %-10s n=%-6d vs=%016x ps=%016x blend=%08x depth=%08x tex=%s vf=%s' % (
                        e['i'], e['prim'], e['count'], e['vs'], e['ps'], e['blend'], e['depth'],
                        ['%dx%d %s%s@%08x' % (t[2], t[3], t[4], 'T' if t[5] else 'L', t[1]) for t in e['tex']],
                        ['@%08x+%d' % (v[1], v[2] * 4) for v in e['vf']]))
            elif kind == 'copy':
                if cur is not None:
                    print('  pass: %d draws, vs=%d ps=%d texturas=%d' % (n, len(vs_set), len(ps_set), len(tex_set)))
                    cur, n = None, 0
                print('** RESOLVE %s src=%d cmd=%d clear=%d -> %08x pitch=%d %s scissor=(%d,%d)-(%d,%d)' % (
                    describe_rt(e['rt']), e['src_select'], e['command'], e['clear'], e['dest'], e['pitch'], e['dest_fmt'],
                    e['scissor'][0] & 0x7FFF, e['scissor'][0] >> 16 & 0x7FFF, e['scissor'][1] & 0x7FFF, e['scissor'][1] >> 16 & 0x7FFF))
            else:
                if cur is not None:
                    print('  pass: %d draws, vs=%d ps=%d texturas=%d' % (n, len(vs_set), len(ps_set), len(tex_set)))
                    cur, n = None, 0
                print('## SWAP frontbuffer=%08x %dx%d' % (e['fb'], e['w'], e['h']))

    if a.dump_shaders:
        os.makedirs(a.dump_shaders, exist_ok=True)
        for h, (st, b) in shaders.items():
            open(os.path.join(a.dump_shaders, '%s_%016x.bin' % ('vs' if st == 0 else 'ps', h)), 'wb').write(b)
    if a.dump_mem:
        os.makedirs(a.dump_mem, exist_ok=True)
        for addr, b in mem:
            open(os.path.join(a.dump_mem, '%08x.bin' % addr), 'wb').write(b)


if __name__ == '__main__':
    main()
