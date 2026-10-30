"""Contentores de shaders Xenos do Budokai 3 HD / Budokai 1 HD, para o XenosRecomp (Renut).

    python3 -I tools/renut/extract_budokai_shaders.py <b3|b1> <yaeN_xenon.xex> <pasta us do disco | -> <pasta de saida>

Com "-" em vez da pasta us so le o executavel (o tools/renut-shaders.sh faz assim: as .afs nao tem nenhum).

Escreve cada contentor (magia 0x102A1100 pixel / 0x102A1101 vertex) uma vez como vs_/ps_<prefixo sha256>.bin, como o
extract_bl_shaders.py do Dragon Burst: a mesma corrida do XenosRecomp aceita a pasta. A saida sao dados do jogo:
nunca no git.

Onde estao: no .rdata do executavel (os dois jogos: 120 contentores, 108 diferentes, 34 VS + 74 PS; entre 0x82000DF0
e ~0x82063BB0). O yaeN_xenon.xex e XEX2 com encriptacao normal (AES-128-CBC, a chave de sessao desencriptada com a
chave retail) e compressao basic (blocos de dados e zeros); a compressao LZX (normal) tambem e lida, por via das
duvidas. As .afs do disco (AFS: "AFS\\0", numero de ficheiros, tabela de deslocamento/tamanho) nao tem nenhum: quase
todas as entradas sao XMemCompress (magia 0x0FF512EE, LZX com janela de 128 KB, ver xcompress()) com formatos do jogo
por dentro (#AZT, #AMB, #SPX, #BPC, #AWO, ...), e as adx_*.afs sao audio ADX. Sao lidas na mesma (todas as *.afs da
pasta, entradas descomprimidas em paralelo), para apanhar um contentor que um dia la apareca; com LZX em Python puro
demora uns minutos por jogo.

Os shaders que o jogo (ou a biblioteca D3D) monte em tempo de execucao nao estao em lado nenhum destes: so a captura
do Renut no aparelho os ve (XGRegisterVertexShader/XGRegisterPixelShader: B3 0x82233F98/0x82233680, B1
0x821C30D8/0x821C27C0, ver ~/Optxbox/maps/dbz-budokai-hd/yae*_hookmap.md).
"""
import hashlib
import multiprocessing
import os
import struct
import sys

# Jogo -> nome original do PE no XEX (cabecalho 0x183FF), para nao misturar os dois.
GAMES = {'b3': 'yae3_xenon.exe', 'b1': 'yae1_xenon.exe'}

XEX_RETAIL_KEY = bytes.fromhex('20B185A59D28FDC340583FBB0896BF91')
XCOMPRESS_MAGIC = b'\x0f\xf5\x12\xee'


def containers(data):
    """Contentores Xenos (ShaderContainer do shader.h do XenosRecomp), com as mesmas condicoes do main.cpp dele:
    flags 0x102A1100 (pixel) / 0x102A1101 (vertex), tamanho = virtualSize + physicalSize dentro dos dados,
    field1C = field20 = 0."""
    found, p = [], 0
    while True:
        p = data.find(b'\x10\x2a\x11', p)
        if p < 0:
            return found
        if p % 4 == 0 and data[p + 3] in (0, 1) and p + 0x24 <= len(data):
            vsize, psize = struct.unpack('>II', data[p + 4:p + 12])
            f1c, f20 = struct.unpack('>II', data[p + 0x1C:p + 0x24])
            if 0 < vsize + psize <= len(data) - p and f1c == 0 and f20 == 0:
                found.append((p, bytes(data[p:p + vsize + psize])))
                p += vsize + psize
                continue
        p += 1


# --- LZX (como o lzxd.c do libmspack, sem LZX DELTA) ---

EXTRA_BITS = [0, 0, 0, 0] + [min(i // 2 - 1, 17) for i in range(4, 52)]
POSITION_BASE = [0]
for _e in EXTRA_BITS[:-1]:
    POSITION_BASE.append(POSITION_BASE[-1] + (1 << _e))
POSITION_SLOTS = {15: 30, 16: 32, 17: 34, 18: 36, 19: 38, 20: 42, 21: 50}


class LzxError(Exception):
    pass


def huffman_table(lens, bits=16):
    """Tabela de descodificacao de 2^bits entradas (codigo canonico, MSB primeiro): simbolo << 5 | comprimento.
    None se nao houver codigos; erro se os comprimentos nao formarem uma arvore completa."""
    table, pos = [0] * (1 << bits), 0
    for length in range(1, bits + 1):
        span = 1 << (bits - length)
        for sym, n in enumerate(lens):
            if n == length:
                table[pos:pos + span] = [sym << 5 | length] * span
                pos += span
    if pos == 0:
        return None
    if pos != 1 << bits:
        raise LzxError('arvore de Huffman incompleta')
    return table


def lzx(src, out_size, window_bits=17):
    """Descomprime um fluxo LZX (pedacos ja concatenados, sem os prefixos de tamanho) para out_size bytes. O
    fluxo le palavras de 16 bits little-endian, bits do mais significativo para o menos; a cada 32 KB de saida
    (um frame) volta a alinhar a 16 bits."""
    src = bytes(src) + bytes(8)
    num_main = 256 + (POSITION_SLOTS[window_bits] << 3)
    main_lens, length_lens = [0] * num_main, [0] * 249
    main_t = length_t = aligned_t = None
    out = bytearray()
    buf = cnt = bp = 0  # buf guarda cnt bits por ler; bp = byte da proxima palavra
    r0 = r1 = r2 = 1

    def bits(n):
        nonlocal buf, cnt, bp
        while cnt < n:
            buf, cnt, bp = buf << 16 | src[bp + 1] << 8 | src[bp], cnt + 16, bp + 2
        cnt -= n
        v = buf >> cnt
        buf &= (1 << cnt) - 1
        return v

    def symbol(table):
        nonlocal buf, cnt, bp
        while cnt < 16:
            buf, cnt, bp = buf << 16 | src[bp + 1] << 8 | src[bp], cnt + 16, bp + 2
        v = table[buf >> (cnt - 16)]
        cnt -= v & 31
        buf &= (1 << cnt) - 1
        return v >> 5

    def read_lens(lens, first, last):
        pre = huffman_table([bits(4) for _ in range(20)])
        x = first
        while x < last:
            z = symbol(pre)
            if z == 17:
                run, v = bits(4) + 4, 0
            elif z == 18:
                run, v = bits(5) + 20, 0
            elif z == 19:  # a corrida repete o valor calculado sobre o primeiro comprimento
                run = bits(1) + 4
                v = (lens[x] - symbol(pre)) % 17
            else:
                run, v = 1, (lens[x] - z) % 17
            lens[x:x + run] = [v] * run
            x += run

    if bits(1):  # cabecalho Intel E8 (codigo x86): os jogos da Xbox 360 nao o usam
        raise LzxError('fluxo com traducao E8')
    block_type = block_left = block_len = 0
    while len(out) < out_size:
        frame_end = min(len(out) + 0x8000, out_size)
        while len(out) < frame_end:
            if block_left == 0:
                if block_type == 3 and block_len & 1:
                    bp += 1  # bloco nao comprimido de tamanho impar: um byte de enchimento
                block_type = bits(3)
                block_left = block_len = bits(16) << 8 | bits(8)
                if block_type == 2:
                    aligned_t = huffman_table([bits(3) for _ in range(8)])
                if block_type in (1, 2):
                    read_lens(main_lens, 0, 256)
                    read_lens(main_lens, 256, num_main)
                    main_t = huffman_table(main_lens)
                    read_lens(length_lens, 0, 249)
                    length_t = huffman_table(length_lens)
                    if main_t is None:
                        raise LzxError('arvore principal vazia')
                elif block_type == 3:
                    # 1 a 16 bits de enchimento ate a palavra seguinte, depois R0..R2 e os bytes em bruto
                    if cnt == 0:
                        bp += 2
                    buf = cnt = 0
                    r0, r1, r2 = struct.unpack('<III', src[bp:bp + 12])
                    bp += 12
                else:
                    raise LzxError(f'tipo de bloco {block_type}')
            run = min(block_left, frame_end - len(out))
            block_left -= run
            if block_type == 3:
                out += src[bp:bp + run]
                bp += run
                continue
            target = len(out) + run
            while len(out) < target:
                sym = symbol(main_t)
                if sym < 256:
                    out.append(sym)
                    continue
                sym -= 256
                length = sym & 7
                if length == 7:
                    if length_t is None:
                        raise LzxError('arvore de comprimentos vazia')
                    length += symbol(length_t)
                length += 2
                slot = sym >> 3
                if slot == 0:
                    off = r0
                elif slot == 1:
                    off, r1, r0 = r1, r0, r1
                elif slot == 2:
                    off, r2, r0 = r2, r0, r2
                else:
                    extra = EXTRA_BITS[slot]
                    off = POSITION_BASE[slot] - 2
                    if extra >= 3 and block_type == 2:
                        if extra > 3:
                            off += bits(extra - 3) << 3
                        off += symbol(aligned_t)
                    elif extra:
                        off += bits(extra)
                    r2, r1, r0 = r1, r0, off
                start = len(out) - off
                if start < 0:
                    raise LzxError('referencia antes do inicio')
                if off >= length:
                    out += out[start:start + length]
                else:
                    for i in range(length):
                        out.append(out[start + i])
            if len(out) > target:  # a ultima copia passou o pedido: conta no bloco
                block_left -= len(out) - target
                if block_left < 0:
                    raise LzxError('copia passou o fim do bloco')
        if len(out) != frame_end:
            raise LzxError('copia passou o fim do frame')
        cnt -= cnt & 15  # realinha a 16 bits
        buf &= (1 << cnt) - 1
    return bytes(out)


def xcompress(b):
    """XMemCompress, formato nativo (o das entradas das .afs). Cabecalho big-endian de 0x30 bytes: magia
    0x0FF512EE, versao 0x01030000, 8 bytes a zero, janela (0x20000), particao (0x80000), tamanho descomprimido
    (u64), tamanho comprimido a seguir ao cabecalho + 4 (u64), tamanho do bloco descomprimido, maior bloco
    comprimido. Depois blocos: u32 com o tamanho do bloco e pedacos (frames LZX de 32 KB) com u16 de tamanho, ou
    0xFF + u16 tamanho descomprimido + u16 comprimido no ultimo frame. Os pedacos de todos os blocos formam um so
    fluxo LZX (nos dois jogos ha sempre um bloco por entrada)."""
    window = struct.unpack('>I', b[0x10:0x14])[0]
    usize, csize = struct.unpack('>QQ', b[0x18:0x28])
    end, p, stream = min(len(b), 0x2C + csize), 0x30, bytearray()
    while p + 4 <= end:
        bsize = struct.unpack('>I', b[p:p + 4])[0]
        p += 4
        q, bend = p, min(p + bsize, end)
        while q + 2 <= bend:
            if b[q] == 0xFF:
                n = struct.unpack('>H', b[q + 3:q + 5])[0]
                q += 5
            else:
                n = struct.unpack('>H', b[q:q + 2])[0]
                q += 2
            if n == 0:
                break
            stream += b[q:q + n]
            q += n
        p = bend
    return lzx(bytes(stream), usize, window.bit_length() - 1)


def afs_entries(path):
    """(deslocamento, tamanho) de cada ficheiro de uma AFS: "AFS\\0", u32 LE numero, tabela de pares u32 LE (o par
    a seguir aponta para a tabela de nomes, que aqui nao interessa)."""
    with open(path, 'rb') as f:
        h = f.read(8)
        if h[:4] != b'AFS\0':
            return []
        n = struct.unpack('<I', h[4:8])[0]
        table = f.read(n * 8)
    return [struct.unpack('<II', table[i * 8:i * 8 + 8]) for i in range(n)]


def scan_entry(job):
    """Trabalho de um processo: uma entrada de uma AFS -> (tipo, 4 primeiros bytes, contentores, erro)."""
    path, off, size = job
    with open(path, 'rb') as f:
        f.seek(off)
        b = f.read(size)
    kind, error = 'bruto', None
    if b[:4] == XCOMPRESS_MAGIC:
        kind = 'xcompress'
        try:
            b = xcompress(b)
        except (LzxError, IndexError, struct.error) as e:
            return kind, b'', [], str(e) or type(e).__name__
    return kind, b[:4], [c for _, c in containers(b)], error


def xex_image(path):
    """A imagem PE carregada (base 0x82000000) de um XEX2 retail, e o nome original do PE (cabecalho 0x183FF)."""
    from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
    x = open(path, 'rb').read()
    if x[:4] != b'XEX2':
        sys.exit(f'extract_budokai_shaders: {path} nao e um XEX2')
    data_at, security = struct.unpack('>I', x[8:12])[0], struct.unpack('>I', x[16:20])[0]
    fmt, pe_name = None, ''
    for i in range(struct.unpack('>I', x[20:24])[0]):
        key, value = struct.unpack('>II', x[24 + i * 8:32 + i * 8])
        if key == 0x3FF:  # informacao do formato
            fmt = value
        elif key == 0x183FF:  # nome original do PE
            pe_name = x[value + 4:value + struct.unpack('>I', x[value:value + 4])[0]].split(b'\0')[0].decode()
    info_size, encryption, compression = struct.unpack('>IHH', x[fmt:fmt + 8])
    if encryption > 1 or compression not in (1, 2):
        sys.exit(f'extract_budokai_shaders: {path}: encriptacao {encryption} compressao {compression} nao tratadas')
    body = x[data_at:]
    if encryption == 1:
        ecb = Cipher(algorithms.AES(XEX_RETAIL_KEY), modes.ECB()).decryptor()
        session = ecb.update(x[security + 0x150:security + 0x160]) + ecb.finalize()
        cbc = Cipher(algorithms.AES(session), modes.CBC(bytes(16))).decryptor()
        body = cbc.update(body[:len(body) // 16 * 16]) + cbc.finalize()
    image = bytearray()
    if compression == 1:  # basic: blocos de dados seguidos de zeros
        p = 0
        for i in range((info_size - 8) // 8):
            size, zeros = struct.unpack('>II', x[fmt + 8 + i * 8:fmt + 16 + i * 8])
            image += body[p:p + size] + bytes(zeros)
            p += size
    else:  # normal: blocos LZX encadeados (tamanho e SHA-1 do seguinte no inicio de cada um), pedacos de u16
        window, bsize = struct.unpack('>II', x[fmt + 8:fmt + 16])
        stream, p = bytearray(), 0
        while bsize:
            blk = body[p:p + bsize]
            q = 24
            while q + 2 <= bsize:
                n = struct.unpack('>H', blk[q:q + 2])[0]
                q += 2
                if not n:
                    break
                stream += blk[q:q + n]
                q += n
            p, bsize = p + bsize, struct.unpack('>I', blk[:4])[0]
        image = lzx(bytes(stream), struct.unpack('>I', x[security + 4:security + 8])[0], window.bit_length() - 1)
    if image[:2] != b'MZ':
        sys.exit(f'extract_budokai_shaders: {path} nao desencriptou para uma imagem PE')
    return bytes(image), pe_name


def main():
    if len(sys.argv) != 5 or sys.argv[1] not in GAMES:
        sys.exit(__doc__)
    game, xex, disc, out = sys.argv[1:5]

    # 1. O executavel.
    image, pe_name = xex_image(xex)
    if pe_name != GAMES[game]:
        sys.exit(f'extract_budokai_shaders: {xex} e {pe_name!r}, nao o {GAMES[game]} do {game}')
    found = [('xex', 0x82000000 + p, c) for p, c in containers(image)]
    print(f'{os.path.basename(xex)}: {len(found)} contentores entre 0x{found[0][1]:08X} e '
          f'0x{found[-1][1] + len(found[-1][2]):08X}' if found else f'{os.path.basename(xex)}: nenhum contentor')

    # 2. As AFS do disco (todas as entradas, descomprimidas em paralelo); "-" = so o executavel.
    archives = [] if disc == '-' else sorted(n for n in os.listdir(disc) if n.lower().endswith('.afs'))
    if disc == '-':
        disc = None
    if disc and not archives:
        sys.exit(f'extract_budokai_shaders: nenhuma .afs em {disc}')
    jobs = [(os.path.join(disc, a), off, size) for a in archives
            for off, size in afs_entries(os.path.join(disc, a)) if size]
    stats = {a: {} for a in archives}
    errors = []
    ctx = multiprocessing.get_context('fork')
    with ctx.Pool(os.cpu_count() or 1) as pool:
        for (path, off, _), (kind, magic, found_here, error) in zip(
                jobs, pool.imap(scan_entry, jobs, chunksize=4)):
            a = os.path.basename(path)
            key = (kind, magic.decode('latin1') if magic[:1] in (b'#', b'M') else magic.hex())
            stats[a][key] = stats[a].get(key, 0) + 1
            if error:
                errors.append(f'{a} @0x{off:X}: {error}')
            found += [(f'{a} @0x{off:X}', None, c) for c in found_here]
    for a in archives:
        top = sorted(stats[a].items(), key=lambda kv: -kv[1])
        print(f'  {a}: {sum(stats[a].values())} entradas, ' + ', '.join(f'{n} {k}/{m}' for (k, m), n in top[:8]))
    for e in errors:
        print(f'  ERRO {e}')

    # 3. Um ficheiro por contentor diferente.
    os.makedirs(out, exist_ok=True)
    seen, kinds, origin = set(), {'vs': 0, 'ps': 0}, {}
    for where, _, c in found:
        origin[where.split(' @')[0]] = origin.get(where.split(' @')[0], 0) + 1
        digest = hashlib.sha256(c).hexdigest()
        if digest in seen:
            continue
        seen.add(digest)
        stage = 'vs' if c[3] & 1 else 'ps'
        kinds[stage] += 1
        with open(os.path.join(out, f'{stage}_{digest[:16]}.bin'), 'wb') as w:
            w.write(c)
    print(f"extract_budokai_shaders: {game}: {len(found)} contentores "
          f"({', '.join(f'{n} de {k}' for k, n in origin.items())}), {kinds['vs']} VS + {kinds['ps']} PS "
          f"diferentes -> {out}")
    if errors:
        sys.exit(f'extract_budokai_shaders: {len(errors)} entradas nao descomprimiram')


if __name__ == '__main__':
    main()
