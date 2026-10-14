package com.dfdx047.dragonfury.textures

import java.io.File
import java.io.RandomAccessFile

/**
 * Regras dos packs de texturas do runtime (dbz3_texture_pack.cpp):
 *
 *  - nome <HASH16>_<L>x<A>_<formato>.png|.dds (L x A = tamanho ORIGINAL);
 *  - PNG (qualquer tipo, vira RGBA8) ou DDS DXT1/DXT3/DXT5 ou 32 bpp sem compressao;
 *  - a imagem tem de ser 1, 2, 3 ou 4 vezes o original, com o mesmo fator nos
 *    dois eixos (mesma proporcao); maximo 16384 por lado.
 */
object TextureNames {
    private val NAME = Regex("""^([0-9A-Fa-f]{16})_(\d+)x(\d+)(?:_([A-Za-z0-9]+))?\.(png|dds)$""", RegexOption.IGNORE_CASE)

    data class Parsed(val hash: String, val width: Int, val height: Int, val format: String?, val ext: String)

    enum class Kind { PNG, DDS_DXT1, DDS_DXT3, DDS_DXT5, DDS_RGBA32, DDS_UNSUPPORTED, UNKNOWN }

    data class ImageInfo(val width: Int, val height: Int, val kind: Kind)

    fun isImage(f: File): Boolean {
        val ext = f.extension.lowercase()
        return ext == "png" || ext == "dds"
    }

    fun parse(fileName: String): Parsed? {
        val m = NAME.matchEntire(fileName) ?: return null
        val w = m.groupValues[2].toIntOrNull() ?: return null
        val h = m.groupValues[3].toIntOrNull() ?: return null
        if (w <= 0 || h <= 0) return null
        return Parsed(m.groupValues[1].uppercase(), w, h, m.groupValues[4].ifEmpty { null }, m.groupValues[5].lowercase())
    }

    /** Le so a cabecalho (PNG IHDR ou DDS) para saber tamanho e formato. */
    fun readImageInfo(f: File): ImageInfo? = try {
        RandomAccessFile(f, "r").use { raf ->
            val head = ByteArray(128)
            val n = raf.read(head)
            if (n >= 24 && head[1] == 'P'.code.toByte() && head[2] == 'N'.code.toByte() && head[3] == 'G'.code.toByte()) {
                ImageInfo(be(head, 16), be(head, 20), Kind.PNG)
            } else if (n >= 128 && head[0] == 'D'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == 'S'.code.toByte()) {
                val h = le(head, 12)
                val w = le(head, 16)
                // DDS_PIXELFORMAT comeca em 76: flags em 80, fourCC em 84, bits em 88.
                val pfFlags = le(head, 80)
                val fourCC = String(head, 84, 4, Charsets.US_ASCII)
                val bits = le(head, 88)
                val kind = when {
                    pfFlags and 0x4 != 0 -> when (fourCC) {
                        "DXT1" -> Kind.DDS_DXT1
                        "DXT3" -> Kind.DDS_DXT3
                        "DXT5" -> Kind.DDS_DXT5
                        else -> Kind.DDS_UNSUPPORTED
                    }
                    pfFlags and 0x40 != 0 && bits == 32 -> Kind.DDS_RGBA32
                    else -> Kind.DDS_UNSUPPORTED
                }
                ImageInfo(w, h, kind)
            } else {
                ImageInfo(0, 0, Kind.UNKNOWN)
            }
        }
    } catch (e: Exception) {
        null
    }

    fun readImageSize(f: File): Pair<Int, Int>? =
        readImageInfo(f)?.takeIf { it.kind != Kind.UNKNOWN }?.let { it.width to it.height }

    /** Fator de escala (1-4) ou null se o tamanho nao e aceite pelo runtime. */
    fun scaleFactor(name: Parsed, size: Pair<Int, Int>): Int? {
        val (w, h) = size
        if (w <= 0 || h <= 0 || w > 16384 || h > 16384) return null
        if (w % name.width != 0 || h % name.height != 0) return null
        val fx = w / name.width
        val fy = h / name.height
        return if (fx == fy && fx in 1..4) fx else null
    }

    private fun be(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

    private fun le(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)
}
