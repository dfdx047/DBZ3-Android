package com.dfdx047.dragonfury.textures

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.dfdx047.dragonfury.data.AppPaths
import com.dfdx047.dragonfury.mods.ModInfo
import com.dfdx047.dragonfury.mods.ModRepository
import com.dfdx047.dragonfury.mods.ModType
import java.io.File

/** Resultado da verificacao de uma imagem de um pack. */
enum class TextureIssue {
    OK,
    BAD_NAME,          // nome sem <hash>_<L>x<A>: o jogo ignora
    UNREADABLE,        // cabecalho ilegivel / ficheiro corrompido
    DDS_FORMAT,        // DDS num formato que o runtime nao descodifica
    BAD_SIZE,          // nao e 1-4x o original (ou proporcao diferente)
    DUPLICATE,         // a mesma textura noutro pack que ganha (ordem alfabetica)
}

data class TextureFileReport(
    val file: File,
    val issue: TextureIssue,
    val scale: Int = 0,
    val detail: String = "",
)

data class TexturePackReport(
    val mod: ModInfo,
    val files: List<TextureFileReport>,
    /** Memoria aproximada depois de descodificado (RGBA8 + mipmaps). */
    val decodedBytes: Long,
) {
    val okCount get() = files.count { it.issue == TextureIssue.OK }
    val warnCount get() = files.count { it.issue == TextureIssue.DUPLICATE }
    val errorCount get() = files.count { it.issue != TextureIssue.OK && it.issue != TextureIssue.DUPLICATE }
    val maxScale get() = files.maxOfOrNull { it.scale } ?: 0
}

/** Linha do log do runtime sobre packs (lida depois de jogar). */
data class RuntimeTextureLine(val level: Level, val text: String) {
    enum class Level { INFO, WARN, ERROR }
}

object TexturePacks {
    const val USER_PACK = "Dragon Fury - texturas"

    /** O runtime guarda em cache ate 2 GB de imagens descodificadas; acima disso descodifica ao usar. */
    const val RUNTIME_CACHE_BUDGET = 2L * 1024 * 1024 * 1024

    fun packs(): List<ModInfo> = ModRepository.listAll().filter { it.type == ModType.TEXTURES }

    /** Valida todos os packs (os ativos e os desativados), com deteccao de duplicados entre ativos. */
    fun validateAll(onProgress: (Int, Int) -> Unit = { _, _ -> }): List<TexturePackReport> {
        val all = packs()
        val owner = HashMap<String, String>() // hash -> pack que ganha
        val reports = ArrayList<TexturePackReport>()
        val total = all.sumOf { m -> m.dir.walkTopDown().count { it.isFile && TextureNames.isImage(it) } }
        var done = 0
        // Mesma ordem do runtime: pastas por ordem alfabetica, o primeiro ganha.
        for (mod in all.sortedBy { it.dir.absolutePath }) {
            val list = ArrayList<TextureFileReport>()
            var bytes = 0L
            for (f in mod.dir.walkTopDown().filter { it.isFile && TextureNames.isImage(it) }.sortedBy { it.path }) {
                done++
                if (done % 50 == 0) onProgress(done, total)
                val parsed = TextureNames.parse(f.name)
                if (parsed == null) {
                    list += TextureFileReport(f, TextureIssue.BAD_NAME)
                    continue
                }
                val info = TextureNames.readImageInfo(f)
                if (info == null || info.kind == TextureNames.Kind.UNKNOWN || info.width <= 0 || info.height <= 0) {
                    list += TextureFileReport(f, TextureIssue.UNREADABLE)
                    continue
                }
                if (info.kind == TextureNames.Kind.DDS_UNSUPPORTED) {
                    list += TextureFileReport(f, TextureIssue.DDS_FORMAT)
                    continue
                }
                val scale = TextureNames.scaleFactor(parsed, info.width to info.height)
                if (scale == null) {
                    list += TextureFileReport(
                        f, TextureIssue.BAD_SIZE,
                        detail = "${info.width}x${info.height} / ${parsed.width}x${parsed.height}",
                    )
                    continue
                }
                if (mod.enabled) {
                    val first = owner[parsed.hash]
                    if (first != null && first != mod.folderName) {
                        list += TextureFileReport(f, TextureIssue.DUPLICATE, scale, first)
                        continue
                    }
                    owner[parsed.hash] = mod.folderName
                }
                bytes += info.width.toLong() * info.height * 4 * 4 / 3
                list += TextureFileReport(f, TextureIssue.OK, scale, "${info.width}x${info.height}")
            }
            reports += TexturePackReport(mod, list, bytes)
        }
        onProgress(total, total)
        return reports.sortedBy { it.mod.folderName }
    }

    private fun queryName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    /**
     * Copia imagens soltas (PNG/DDS com nome de pack) para o pack do usuario.
     * Devolve (copiadas, recusadas com o motivo).
     */
    fun importImages(context: Context, uris: List<Uri>): Pair<Int, List<Pair<String, TextureIssue>>> {
        val dest = File(AppPaths.modsDir, USER_PACK).also { it.mkdirs() }
        val manifest = File(dest, "manifest.txt")
        if (!manifest.exists()) {
            manifest.writeText("name=$USER_PACK\ntype=texturas\nauthor=Dragon Fury\n")
        }
        var ok = 0
        val refused = ArrayList<Pair<String, TextureIssue>>()
        for (uri in uris) {
            val name = queryName(context, uri) ?: continue
            if (TextureNames.parse(name) == null) {
                refused += name to TextureIssue.BAD_NAME
                continue
            }
            val target = File(dest, name)
            val tmp = File(dest, "$name.part")
            val input = context.contentResolver.openInputStream(uri)
            if (input == null) {
                refused += name to TextureIssue.UNREADABLE
                continue
            }
            input.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            val info = TextureNames.readImageInfo(tmp)
            val parsed = TextureNames.parse(name)!!
            val issue = when {
                info == null || info.kind == TextureNames.Kind.UNKNOWN -> TextureIssue.UNREADABLE
                info.kind == TextureNames.Kind.DDS_UNSUPPORTED -> TextureIssue.DDS_FORMAT
                TextureNames.scaleFactor(parsed, info.width to info.height) == null -> TextureIssue.BAD_SIZE
                else -> TextureIssue.OK
            }
            if (issue != TextureIssue.OK) {
                tmp.delete()
                refused += name to issue
                continue
            }
            target.delete()
            tmp.renameTo(target)
            ok++
        }
        return ok to refused
    }

    /**
     * Linhas do log da ultima sessao sobre packs de texturas (mensagens do
     * dbz3_texture_pack.cpp e do launcher do runtime), para mostrar o estado real.
     */
    fun runtimeLog(): List<RuntimeTextureLine> {
        val log = AppPaths.lastRunLog
        if (!log.isFile) return emptyList()
        return log.useLines { lines ->
            lines.filter { l ->
                l.contains("dbz3:") && (l.contains("pack") || l.contains("textura") || l.contains("texture"))
            }.map { l ->
                val text = l.substringAfter("dbz3:").trim()
                val level = when {
                    l.contains("[error]", true) || text.contains("no se pudo") || text.contains("no existe") ->
                        RuntimeTextureLine.Level.ERROR
                    l.contains("[warn", true) || text.contains("ignorado") || text.contains("invalido") ||
                        text.contains("gana el primero") || text.contains("skipped") ->
                        RuntimeTextureLine.Level.WARN
                    else -> RuntimeTextureLine.Level.INFO
                }
                RuntimeTextureLine(level, text)
            }.take(200).toList()
        }
    }
}
