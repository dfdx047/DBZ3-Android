package com.dfdx047.dragonfury.mods

import android.content.Context
import android.net.Uri
import com.dfdx047.dragonfury.data.AppPaths
import com.dfdx047.dragonfury.data.TomlLite
import com.dfdx047.dragonfury.textures.TextureNames
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Mods no formato do projeto original (src/mods.cpp), lidos e escritos em
 * Kotlin. O runtime so precisa das pastas certas:
 *
 *  - um mod = uma pasta mods/<nome>/ ; manifest.txt opcional (chave=valor);
 *  - desativado = ficheiro marcador ".disabled" dentro da pasta (o runtime so
 *    olha para o marcador, por isso nunca se renomeia a pasta);
 *  - ordem: alfabetica pelo caminho, o primeiro mod que tem o ficheiro ganha.
 */
enum class ModType {
    CHARACTER, COSTUME, ROSTER, AUDIO, DATA, TEXTURES, MODEL_SWAP, PORT, MOVESET, OTHER;

    companion object {
        fun fromManifest(s: String?): ModType? = when (s?.lowercase()) {
            "personaje" -> CHARACTER
            "traje" -> COSTUME
            "generado" -> ROSTER
            "audio" -> AUDIO
            "data" -> DATA
            "texturas", "textures", "texture_pack" -> TEXTURES
            "swap_b3" -> MODEL_SWAP
            "port_b3" -> PORT
            "moveset" -> MOVESET
            "other" -> OTHER
            else -> null
        }
    }
}

sealed class ModProblem {
    /** Pasta vazia: nao faz nada. */
    object Empty : ModProblem()
    /** O conteudo esta dentro de uma pasta a mais: o jogo nao o ve. Tem correcao automatica. */
    data class Nested(val inner: String) : ModProblem()
    /** Um caminho referido no personaje.toml/traje.toml nao existe. */
    data class MissingFile(val path: String) : ModProblem()
    /** O .toml do mod nao se le. */
    data class BadToml(val file: String, val message: String) : ModProblem()
    /** Imagens com nome que o jogo nao reconhece. */
    data class BadTextureName(val count: Int) : ModProblem()
    /** Imagem com tamanho que nao e 1-4x o original. */
    data class BadTextureSize(val file: String) : ModProblem()
    /** O mesmo ficheiro do jogo tambem e trocado por outro mod que ganha. */
    data class Overridden(val path: String, val winner: String) : ModProblem()
}

data class ModInfo(
    val dir: File,
    val folderName: String,
    val displayName: String,
    val description: String,
    val author: String,
    val version: String,
    val type: ModType,
    val enabled: Boolean,
    val fileCount: Int,
    val sizeBytes: Long,
    val problems: List<ModProblem>,
) {
    val hasErrors: Boolean
        get() = problems.any { it !is ModProblem.Overridden }
}

class ModInstallException(val reason: Reason, detail: String = "") : Exception(detail) {
    enum class Reason { NOT_ZIP, EMPTY, UNSAFE_PATH, IO }
}

object ModRepository {
    private const val DISABLED_MARKER = ".disabled"
    private const val MAX_ZIP_ENTRY = 4L * 1024 * 1024 * 1024 // 4 GB

    private val modsRoot: File get() = AppPaths.modsDir.also { it.mkdirs() }

    private fun isModFolder(f: File) = f.isDirectory && !f.name.startsWith(".")

    /** Todas as pastas de mods (inclui packs de texturas). */
    fun listAll(): List<ModInfo> {
        val dirs = modsRoot.listFiles()?.filter(::isModFolder).orEmpty().sortedBy { it.name }
        val infos = dirs.map(::readMod)
        return withConflicts(infos)
    }

    fun readMod(dir: File): ModInfo {
        val manifest = readManifest(File(dir, "manifest.txt"))
        val files = dir.walkTopDown().filter { it.isFile && it.name != DISABLED_MARKER }.toList()
        val textureFiles = files.filter { TextureNames.isImage(it) }
        var type = ModType.fromManifest(manifest["type"])
        var display = manifest["name"].orEmpty()
        if (File(dir, "personaje.toml").isFile) {
            if (type == null) type = ModType.CHARACTER
            if (display.isEmpty()) display = characterName(File(dir, "personaje.toml")).orEmpty()
        }
        if (type == null && File(dir, "traje.toml").isFile) type = ModType.COSTUME
        if (type == null && File(dir, "roster.toml").isFile) type = ModType.ROSTER
        if (type == null) {
            val rels = files.map { it.relativeTo(dir).path }
            type = when {
                rels.any { it.contains("adx_") && (it.contains(".afs") || it.contains(".adx")) } -> ModType.AUDIO
                rels.any { it.contains("data_") && it.contains(".afs") } -> ModType.DATA
                textureFiles.any { TextureNames.parse(it.name) != null } -> ModType.TEXTURES
                else -> ModType.OTHER
            }
        }
        return ModInfo(
            dir = dir,
            folderName = dir.name,
            displayName = display.ifEmpty { dir.name },
            description = manifest["description"].orEmpty(),
            author = manifest["author"].orEmpty(),
            version = manifest["version"].orEmpty(),
            type = type,
            enabled = !File(dir, DISABLED_MARKER).exists() && !dir.name.endsWith(".disabled"),
            fileCount = files.size,
            sizeBytes = files.sumOf { it.length() },
            problems = checkProblems(dir, files, type),
        )
    }

    private fun readManifest(f: File): Map<String, String> {
        if (!f.isFile) return emptyMap()
        return f.readLines().mapNotNull { line ->
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) return@mapNotNull null
            val eq = t.indexOf('=')
            if (eq <= 0) null else t.substring(0, eq).trim().lowercase() to t.substring(eq + 1).trim()
        }.toMap()
    }

    private fun characterName(toml: File): String? =
        try {
            (TomlLite.read(toml)["nombre"] as? TomlLite.Value.Str)?.v
        } catch (e: TomlLite.ParseError) {
            null
        }

    private fun checkProblems(dir: File, files: List<File>, type: ModType): List<ModProblem> {
        val out = ArrayList<ModProblem>()
        if (files.isEmpty()) return listOf(ModProblem.Empty)
        // Pasta a mais: so uma subpasta com o conteudo real e nada solto na raiz.
        val children = dir.listFiles()?.filter { it.name != DISABLED_MARKER }.orEmpty()
        if (children.size == 1 && children[0].isDirectory && !hasModContent(dir) && hasModContent(children[0])) {
            out += ModProblem.Nested(children[0].name)
        }
        for (name in listOf("personaje.toml", "traje.toml", "roster.toml")) {
            val f = File(dir, name)
            if (!f.isFile) continue
            try {
                val values = TomlLite.read(f)
                if (name != "roster.toml") {
                    for (v in values.values) {
                        val s = (v as? TomlLite.Value.Str)?.v ?: continue
                        if (looksLikeRelativePath(s) && !File(dir, s).exists()) out += ModProblem.MissingFile(s)
                    }
                }
            } catch (e: TomlLite.ParseError) {
                out += ModProblem.BadToml(name, "linha ${e.line}: ${e.message}")
            }
        }
        if (type == ModType.TEXTURES) {
            val images = files.filter { TextureNames.isImage(it) }
            val badNames = images.count { TextureNames.parse(it.name) == null }
            if (badNames > 0) out += ModProblem.BadTextureName(badNames)
            images.firstOrNull { img ->
                val parsed = TextureNames.parse(img.name) ?: return@firstOrNull false
                val size = TextureNames.readImageSize(img) ?: return@firstOrNull false
                TextureNames.scaleFactor(parsed, size) == null
            }?.let { out += ModProblem.BadTextureSize(it.name) }
        }
        return out
    }

    private fun looksLikeRelativePath(s: String): Boolean {
        if (!s.contains('/') && !s.contains('\\')) return false
        val ext = s.substringAfterLast('.', "")
        return ext.isNotEmpty() && ext.length <= 4 && !s.startsWith("http")
    }

    private fun hasModContent(d: File): Boolean {
        if (listOf("us", "eu", "personaje.toml", "traje.toml", "roster.toml", "manifest.txt", "ui_ingame")
                .any { File(d, it).exists() }
        ) return true
        return d.walkTopDown().any { it.isFile && TextureNames.parse(it.name) != null }
    }

    /** Chave do ficheiro do jogo que um ficheiro do mod substitui (para conflitos). */
    private fun overrideKey(rel: String): String? {
        val p = rel.replace('\\', '/')
        val parts = p.split('/')
        if (parts.size >= 3 && (parts[0] == "us" || parts[0] == "eu")) return parts.take(3).joinToString("/")
        if (parts.size == 2 && (parts[0] == "us" || parts[0] == "eu")) return p
        if (parts.size == 1) {
            val ext = p.substringAfterLast('.', "").lowercase()
            if (ext == "afs" || ext == "sfd" || ext == "adx") return "us/$p"
        }
        return null
    }

    private fun withConflicts(mods: List<ModInfo>): List<ModInfo> {
        val owner = HashMap<String, String>()
        val extra = HashMap<String, MutableList<ModProblem>>()
        // Mesma ordem que o runtime: alfabetica pelo caminho completo.
        for (m in mods.filter { it.enabled }.sortedBy { it.dir.absolutePath }) {
            val keys = m.dir.walkTopDown().filter { it.isFile }
                .mapNotNull { overrideKey(it.relativeTo(m.dir).path) }.toSet()
            for (k in keys) {
                val first = owner[k]
                if (first == null) owner[k] = m.folderName
                else extra.getOrPut(m.folderName) { mutableListOf() } += ModProblem.Overridden(k, first)
            }
        }
        return mods.map { m ->
            val more = extra[m.folderName] ?: return@map m
            // Um aviso por mod chega (pode haver centenas de entradas).
            m.copy(problems = m.problems + more.first())
        }
    }

    fun setEnabled(mod: ModInfo, enabled: Boolean) {
        val marker = File(mod.dir, DISABLED_MARKER)
        if (enabled) marker.delete() else marker.writeText("")
    }

    fun delete(mod: ModInfo): Boolean = mod.dir.deleteRecursively()

    /** Correcao do problema "pasta a mais": sobe o conteudo um nivel, sem sobrescrever. */
    fun fixNested(mod: ModInfo): Boolean {
        val inner = mod.problems.filterIsInstance<ModProblem.Nested>().firstOrNull() ?: return false
        val innerDir = File(mod.dir, inner.inner)
        val kids = innerDir.listFiles().orEmpty()
        if (kids.any { File(mod.dir, it.name).exists() && it.name != inner.inner }) return false
        val tmp = File(mod.dir, ".nested_tmp")
        if (!innerDir.renameTo(tmp)) return false
        tmp.listFiles().orEmpty().forEach { it.renameTo(File(mod.dir, it.name)) }
        return tmp.delete()
    }

    /**
     * Instala um .zip escolhido pelo usuario. Igual ao InstallModFromZip do PC:
     * extrai, desembrulha uma pasta unica na raiz e da um nome sem colisao.
     * Um zip com mods/ na raiz (pack de personagens) instala cada subpasta
     * como um mod e substitui as que ja existirem.
     */
    fun installZip(context: Context, uri: Uri, displayName: String, onProgress: (Long) -> Unit = {}): List<String> {
        val stem = sanitize(displayName.substringBeforeLast('.').ifBlank { "mod" })
        val staging = File(modsRoot, ".install_$stem")
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            var written = 0L
            var entries = 0
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw ModInstallException(ModInstallException.Reason.IO, "sem acesso ao ficheiro")
            ZipInputStream(stream.buffered()).use { zip ->
                val canonicalRoot = staging.canonicalPath + File.separator
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    val target = File(staging, entry.name)
                    // Zip-slip: nada pode sair da pasta de destino.
                    if (!target.canonicalPath.startsWith(canonicalRoot) && target.canonicalPath != staging.canonicalPath) {
                        throw ModInstallException(ModInstallException.Reason.UNSAFE_PATH, entry.name)
                    }
                    if (entry.isDirectory) {
                        target.mkdirs()
                        continue
                    }
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out ->
                        val buf = ByteArray(256 * 1024)
                        var size = 0L
                        while (true) {
                            val n = zip.read(buf)
                            if (n < 0) break
                            size += n
                            if (size > MAX_ZIP_ENTRY) {
                                throw ModInstallException(ModInstallException.Reason.IO, "${entry.name}: demasiado grande")
                            }
                            out.write(buf, 0, n)
                            written += n
                        }
                    }
                    onProgress(written)
                }
            }
            if (entries == 0) throw ModInstallException(ModInstallException.Reason.NOT_ZIP)

            var root = staging
            // Desembrulha "Mod/Mod/..." enquanto houver so uma pasta e nada solto.
            while (true) {
                val kids = root.listFiles().orEmpty()
                if (kids.size == 1 && kids[0].isDirectory && !hasModContent(root) && kids[0].name != "mods") root = kids[0] else break
            }
            val kids = root.listFiles().orEmpty()
            if (kids.isEmpty()) throw ModInstallException(ModInstallException.Reason.EMPTY)

            val modsFolder = kids.firstOrNull { it.isDirectory && it.name.equals("mods", true) }
            if (modsFolder != null && kids.size <= 3 && !hasModContent(root)) {
                val installed = ArrayList<String>()
                for (sub in modsFolder.listFiles().orEmpty().filter { it.isDirectory }) {
                    val dest = File(modsRoot, sub.name)
                    dest.deleteRecursively()
                    moveDir(sub, dest)
                    installed += sub.name
                }
                if (installed.isEmpty()) throw ModInstallException(ModInstallException.Reason.EMPTY)
                return installed
            }

            val name = uniqueName(stem)
            moveDir(root, File(modsRoot, name))
            return listOf(name)
        } catch (e: ModInstallException) {
            throw e
        } catch (e: java.util.zip.ZipException) {
            throw ModInstallException(ModInstallException.Reason.NOT_ZIP, e.message ?: "")
        } catch (e: Exception) {
            throw ModInstallException(ModInstallException.Reason.IO, e.message ?: e.toString())
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun moveDir(from: File, to: File) {
        if (!from.renameTo(to)) {
            from.copyRecursively(to, overwrite = true)
            from.deleteRecursively()
        }
    }

    fun uniqueName(base: String): String {
        var name = base
        var i = 2
        while (File(modsRoot, name).exists()) name = "${base}_${i++}"
        return name
    }

    fun sanitize(s: String): String =
        s.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().trimStart('.').ifEmpty { "mod" }
}
