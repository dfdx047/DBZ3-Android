package com.dfdx047.dragonfury.data

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Leitor do sistema de ficheiros dos discos de Xbox 360 (XDVDFS / "XISO"), direto do .iso,
 * sem extrair a imagem toda.
 *
 * Imagens aceites:
 *  - ISO completa do disco (XGD2 com a particao do jogo em 0xFD90000, XGD3 em 0x2080000);
 *  - XISO ja "limpa" (extract-xiso -r / iXtractor: particao em 0);
 *  - XGD1 (Xbox original, 0x18300000), so por completude.
 *
 * Formato: setor 32 da particao = "MICROSOFT*XBOX*MEDIA" + setor e tamanho da diretoria raiz.
 * Cada diretoria e uma arvore binaria de entradas (esquerda/direita em dwords), 4 bytes
 * alinhadas: u16 esq, u16 dir, u32 setor, u32 tamanho, u8 atributos (0x10 = pasta),
 * u8 tamanho do nome, nome. Todos os valores em little-endian; setores de 2048 bytes
 * relativos ao inicio da particao.
 */
class XisoReader(private val channel: FileChannel) {

    data class Entry(val path: String, val name: String, val offset: Long, val size: Long) {
        val parent: String get() = path.substringBeforeLast('/', "")
    }

    /** Inicio da particao do jogo dentro do .iso. */
    val partitionOffset: Long

    /** Todos os ficheiros (sem pastas), com o caminho a partir da raiz ("us/data_cmn.afs"). */
    val files: List<Entry>

    init {
        val size = channel.size()
        partitionOffset = PARTITION_OFFSETS.firstOrNull { base ->
            base + MAGIC_SECTOR_OFFSET + SECTOR <= size && hasMagic(base + MAGIC_SECTOR_OFFSET)
        } ?: throw NotXisoException()
        val header = read(partitionOffset + MAGIC_SECTOR_OFFSET, 32)
        val rootSector = header.getInt(20).toLong() and 0xFFFFFFFFL
        val rootSize = header.getInt(24).toLong() and 0xFFFFFFFFL
        val out = ArrayList<Entry>()
        walk(rootSector, rootSize, "", 0, out)
        files = out
    }

    private fun hasMagic(position: Long): Boolean {
        val b = read(position, MAGIC.size)
        for (i in MAGIC.indices) if (b.get(i) != MAGIC[i]) return false
        return true
    }

    private fun read(position: Long, length: Int): ByteBuffer {
        val buf = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN)
        var pos = position
        while (buf.hasRemaining()) {
            val n = channel.read(buf, pos)
            if (n <= 0) throw IOException("fim da imagem em $pos")
            pos += n
        }
        buf.flip()
        return buf
    }

    private fun walk(sector: Long, size: Long, prefix: String, depth: Int, out: MutableList<Entry>) {
        if (size <= 0 || depth > 32 || size > MAX_DIR_SIZE) return
        val table = read(partitionOffset + sector * SECTOR, size.toInt())
        val limit = table.limit()
        val pending = ArrayDeque<Int>()
        val seen = HashSet<Int>()
        pending.addLast(0)
        while (pending.isNotEmpty()) {
            val o = pending.removeFirst()
            if (o < 0 || o + 14 > limit || !seen.add(o)) continue
            val left = table.getShort(o).toInt() and 0xFFFF
            val right = table.getShort(o + 2).toInt() and 0xFFFF
            if (left == 0xFFFF && right == 0xFFFF) continue  // enchimento (0xFF) entre setores
            val start = table.getInt(o + 4).toLong() and 0xFFFFFFFFL
            val length = table.getInt(o + 8).toLong() and 0xFFFFFFFFL
            val attributes = table.get(o + 12).toInt() and 0xFF
            val nameLength = table.get(o + 13).toInt() and 0xFF
            if (left != 0) pending.addLast(left * 4)
            if (right != 0) pending.addLast(right * 4)
            if (nameLength == 0 || o + 14 + nameLength > limit) continue
            val nameBytes = ByteArray(nameLength)
            for (i in 0 until nameLength) nameBytes[i] = table.get(o + 14 + i)
            val name = String(nameBytes, Charsets.ISO_8859_1)
            val path = if (prefix.isEmpty()) name else "$prefix/$name"
            if (attributes and ATTR_DIRECTORY != 0) {
                walk(start, length, path, depth + 1, out)
            } else {
                out += Entry(path, name, partitionOffset + start * SECTOR, length)
            }
        }
    }

    class NotXisoException : IOException("nao e uma imagem de disco de Xbox 360")

    companion object {
        const val SECTOR = 2048L
        private const val MAGIC_SECTOR_OFFSET = 32 * SECTOR
        private const val ATTR_DIRECTORY = 0x10
        private const val MAX_DIR_SIZE = 64L shl 20
        private val MAGIC = "MICROSOFT*XBOX*MEDIA".toByteArray(Charsets.US_ASCII)

        /** XISO limpa, XGD3, XGD2, XGD1. */
        val PARTITION_OFFSETS = longArrayOf(0L, 0x2080000L, 0xFD90000L, 0x18300000L)
    }
}
