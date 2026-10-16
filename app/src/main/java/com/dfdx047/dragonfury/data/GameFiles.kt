package com.dfdx047.dragonfury.data

import android.content.Context
import android.net.Uri
import android.os.StatFs
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Arquivos esperados do jogo (baserom.md do projeto original, regiao USA). */
object BaseRom {
    const val XEX_SIZE = 4_890_624L
    const val XEX_SHA_US = "B40BBA40CFD6C90CB269EBF5020818924F43109BCC86827A3CC37124C052A26B"
    const val XEX_SHA_EU = "7193803AEE6124C8D0782EF2C37FF2E8D41DB8A11286D198C23352EA7622E924"

    data class Entry(val name: String, val size: Long, val sha256: String, val required: Boolean)

    val US = listOf(
        Entry("data_cmn.afs", 293_423_104, "FC73A1DC9FBF1B13953C0347A121C65FCB2DEECA4A7C823F138177E60FF8C0DF", true),
        Entry("data_eng.afs", 64_284_672, "AC51F1D273EB7A2A50F685282723651AAFDD54159F4BF2237BECD5E484EA8958", false),
        Entry("data_fra.afs", 64_727_040, "0136FFA2C62CE0ED82AD22C07058566A081AED3EF4EC6915D35786752A2C5983", false),
        Entry("data_ger.afs", 66_117_632, "83C51CB4D6DB408868AF6487C0AA0CDDEAC20C32BB28324F08E6215E0B32A328", false),
        Entry("data_ita.afs", 64_315_392, "EFECE6723C2D7859FCB773E446CEA9489394837E632BC68C79A9066B2FFC7E4A", false),
        Entry("data_spn.afs", 65_036_288, "AEB97105E3F3B0A105D41B77F0A4F966184298381519F3B96EFAB210C43EF4D4", false),
        Entry("data_usi.afs", 62_898_176, "7DD517A4EB1462CBD5C104C5CF9748D8CD34719B10B6FACC22F0107181A65851", false),
        Entry("data_yah.afs", 1_632_256, "6E55D160F6C29EE21CC255AE966AB17016143FB52ADE4E84409D0493BAC7EFCF", true),
        Entry("adx_jpn.afs", 728_424_448, "ECF59DB76B2B04720505DDEBC4AC931A45EE2597D21AD65693DA2DF86E27A822", false),
        Entry("adx_usa.afs", 681_572_352, "702168D52C1DD93DFE5E1AB0F625C433EC9B7AFD7E07930C3A9C0FB3E82A4BA5", true),
        Entry("lang_jpn.afs", 19_107_840, "E3421DB3848D93E457EE632BF14D1CD94D3D0165C69A6AA4FBDF48C13D38B4E9", false),
        Entry("lang_usa.afs", 17_618_944, "9F4C947634B368ACBE594F2BC773745312B23BD14B7394E02435AE9432880A0E", true),
        Entry("opening.sfd", 73_623_552, "773D9391BE0CD2EFA4351BE76FB1D4C054A69B2A624D94A882C65CF0EE83DC08", false),
        Entry("Ending00.sfd", 88_363_008, "65180E8C4E9D3C16095447A285018A0E5492D1966DB009E6092585CCB995786B", false),
        Entry("Ending01.sfd", 169_752_576, "C243C28EAE615F161DAC2CC67709507AD60CE0CEF7BCB4F372A413EA837ED4F2", false),
    )

    /**
     * Textos em ingles: o baserom.md lista o data_eng.afs, mas ha copias USA (ex. o
     * dump Redump) que trazem o data_usi.afs no lugar dele e jogam bem. Basta um.
     */
    val TEXT_ANY_OF = listOf("data_eng.afs", "data_usi.afs")
}

/**
 * DIFFERENT = tamanho ou hash diferente da tabela do baserom.md. Ha dumps USA com
 * .afs/.sfd de tamanhos diferentes que funcionam (testado na Fase 1), por isso e
 * so um aviso, nao bloqueia o jogo.
 */
enum class FileStatus { OK, MISSING, DIFFERENT, WRONG_SIZE, WRONG_HASH, EU_REGION }

data class FileCheck(val name: String, val required: Boolean, val status: FileStatus)

/** Estado da instalacao do jogo na pasta da app. */
data class GameInstall(val checks: List<FileCheck>) {
    val xex: FileCheck? get() = checks.firstOrNull { it.name == "yae3_xenon.xex" }
    /** Bloqueia o JOGAR: obrigatorio em falta, executavel EU ou executavel com tamanho errado. */
    val playable: Boolean
        get() = checks.none { c ->
            c.status == FileStatus.EU_REGION ||
                (c.required && c.status == FileStatus.MISSING) ||
                (c.name == "yae3_xenon.xex" && c.status == FileStatus.WRONG_SIZE)
        } && textsPresent
    /** Pelo menos um dos arquivos de texto em ingles (data_eng.afs ou data_usi.afs). */
    val textsPresent: Boolean
        get() = checks.any { it.name in BaseRom.TEXT_ANY_OF && it.status != FileStatus.MISSING }
    val installed: Boolean get() = xex?.status != FileStatus.MISSING
}

sealed class ImportProgress {
    data class Copying(val file: String, val done: Long, val total: Long) : ImportProgress()
    data class Hashing(val file: String, val done: Long, val total: Long) : ImportProgress()
    /** A ler a estrutura do .iso (rapido). */
    data object ReadingIso : ImportProgress()
}

class ImportException(val reason: Reason, val detail: String = "") : Exception(detail) {
    enum class Reason { NO_XEX, NO_US_FOLDER, EU_XEX, BAD_XEX, MISSING_REQUIRED, IO, NOT_ISO, ISO_NO_GAME, NO_SPACE }
}

object GameFiles {

    private fun sha256(input: InputStream, total: Long, onProgress: (Long, Long) -> Unit): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(1 shl 20)
        var done = 0L
        var lastReport = 0L
        input.use {
            while (true) {
                val n = it.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
                done += n
                if (done - lastReport >= (8L shl 20)) {
                    lastReport = done
                    onProgress(done, total)
                }
            }
        }
        onProgress(total, total)
        return md.digest().joinToString("") { b -> "%02X".format(b) }
    }

    /**
     * Verifica o que esta na pasta da app. Sem [fullHash] so compara tamanhos dos
     * .afs/.sfd (rapido); o .xex e sempre verificado por SHA-256 (4,6 MB).
     */
    fun check(fullHash: Boolean, onProgress: (ImportProgress) -> Unit = {}): GameInstall {
        val list = ArrayList<FileCheck>()
        val xex = AppPaths.xexFile
        list += FileCheck(
            "yae3_xenon.xex", true,
            when {
                !xex.isFile -> FileStatus.MISSING
                xex.length() != BaseRom.XEX_SIZE -> FileStatus.WRONG_SIZE
                else -> {
                    val hash = sha256(xex.inputStream(), xex.length()) { d, t ->
                        onProgress(ImportProgress.Hashing(xex.name, d, t))
                    }
                    when (hash) {
                        BaseRom.XEX_SHA_US -> FileStatus.OK
                        BaseRom.XEX_SHA_EU -> FileStatus.EU_REGION
                        // Outro dump do executavel USA: o APK foi gerado a partir do
                        // executavel de quem o compilou, por isso so avisa.
                        else -> FileStatus.DIFFERENT
                    }
                }
            },
        )
        for (e in BaseRom.US) {
            val f = File(AppPaths.usDir, e.name)
            val status = when {
                !f.isFile -> FileStatus.MISSING
                f.length() != e.size -> FileStatus.DIFFERENT
                !fullHash -> FileStatus.OK
                else -> {
                    val hash = sha256(f.inputStream(), f.length()) { d, t ->
                        onProgress(ImportProgress.Hashing(e.name, d, t))
                    }
                    if (hash == e.sha256) FileStatus.OK else FileStatus.DIFFERENT
                }
            }
            list += FileCheck(e.name, e.required, status)
        }
        return GameInstall(list)
    }

    private fun DocumentFile.child(name: String): DocumentFile? =
        listFiles().firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Ficheiro do jogo a copiar: nome no disco/pasta, tamanho e como abrir a origem. */
    private class Item(val name: String, val size: Long, val offset: Long, val open: () -> FastCopy.Source)

    private fun openUri(context: Context, uri: Uri): FastCopy.Source {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("nao foi possivel abrir $uri")
        return FastCopy.Source(FileInputStream(pfd.fileDescriptor).channel, pfd)
    }

    private fun readAll(item: Item): ByteArray {
        if (item.size > 64L shl 20) throw IOException("${item.name}: grande demais")
        item.open().use { src ->
            val buf = ByteBuffer.allocate(item.size.toInt())
            var pos = item.offset
            while (buf.hasRemaining()) {
                val n = src.channel.read(buf, pos)
                if (n <= 0) throw IOException("${item.name}: fim inesperado")
                pos += n
            }
            return buf.array()
        }
    }

    /**
     * Parte comum das duas importacoes: confere o executavel (tamanho, SHA-256, regiao), os
     * obrigatorios de us/ e o espaco livre, e copia tudo com o [FastCopy]. Ficheiros que ja
     * estao na app com o mesmo tamanho (reimportacao) sao saltados.
     */
    private fun install(xex: Item, usFiles: List<Item>, onProgress: (ImportProgress) -> Unit) {
        if (xex.size != BaseRom.XEX_SIZE) {
            throw ImportException(ImportException.Reason.BAD_XEX, xex.name)
        }
        val xexBytes = readAll(xex)
        val hash = sha256(ByteArrayInputStream(xexBytes), xex.size) { d, t ->
            onProgress(ImportProgress.Hashing(xex.name, d, t))
        }
        // Hash desconhecido com o tamanho certo: outro dump USA, aceita (a verificacao avisa).
        if (hash == BaseRom.XEX_SHA_EU) throw ImportException(ImportException.Reason.EU_XEX)

        val missing = BaseRom.US.filter { e -> e.required && usFiles.none { it.name.equals(e.name, true) } }
            .map { it.name }.toMutableList()
        if (BaseRom.TEXT_ANY_OF.none { t -> usFiles.any { it.name.equals(t, true) } }) {
            missing += BaseRom.TEXT_ANY_OF.joinToString(" / ")
        }
        if (missing.isNotEmpty()) {
            throw ImportException(ImportException.Reason.MISSING_REQUIRED, missing.joinToString())
        }

        AppPaths.usDir.mkdirs()
        val jobs = ArrayList<FastCopy.Job>()
        for (item in usFiles) {
            // Nome canonico (o runtime procura em minusculas, exceto os Ending0N.sfd).
            val canonical = BaseRom.US.firstOrNull { it.name.equals(item.name, true) }?.name ?: item.name
            val target = File(AppPaths.usDir, canonical)
            if (target.isFile && target.length() == item.size) continue
            jobs += FastCopy.Job(canonical, target, item.offset, item.size, item.open)
        }
        val needed = jobs.sumOf { it.size } + (64L shl 20)
        val free = try { StatFs(AppPaths.appDir.path).availableBytes } catch (_: Exception) { Long.MAX_VALUE }
        if (free < needed) {
            throw ImportException(
                ImportException.Reason.NO_SPACE,
                "%.1f GB / %.1f GB".format(needed / 1e9, free / 1e9),
            )
        }
        try {
            AppPaths.xexFile.parentFile?.mkdirs()
            val tmp = File(AppPaths.gameDir, AppPaths.xexFile.name + ".part")
            tmp.writeBytes(xexBytes)
            if (AppPaths.xexFile.exists()) AppPaths.xexFile.delete()
            if (!tmp.renameTo(AppPaths.xexFile)) throw IOException(AppPaths.xexFile.name)
            FastCopy.run(jobs, threads = 2) { file, done, total ->
                onProgress(ImportProgress.Copying(file, done, total))
            }
        } catch (e: ImportException) {
            throw e
        } catch (e: Exception) {
            throw ImportException(ImportException.Reason.IO, e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Importa o jogo de uma pasta escolhida pelo usuario (SAF). Aceita a pasta
     * com o .xex (yae3_xenon.xex ou default.xex) e a pasta us/, ou uma pasta
     * acima que tenha DBZ3/ dentro. Copia para a pasta da app.
     */
    fun import(context: Context, tree: Uri, onProgress: (ImportProgress) -> Unit) {
        val root = DocumentFile.fromTreeUri(context, tree)
            ?: throw ImportException(ImportException.Reason.IO, "pasta invalida")
        val base = root.child("DBZ3")?.takeIf { it.isDirectory } ?: root
        val xexDoc = base.child("yae3_xenon.xex") ?: base.child("default.xex")
            ?: throw ImportException(ImportException.Reason.NO_XEX)
        val usDoc = base.child("us")?.takeIf { it.isDirectory }
            ?: throw ImportException(ImportException.Reason.NO_US_FOLDER)
        fun item(doc: DocumentFile) = Item(doc.name ?: "", doc.length(), 0L) { openUri(context, doc.uri) }
        val usFiles = usDoc.listFiles().filter { it.isFile && it.name != null }.map(::item)
        install(item(xexDoc), usFiles, onProgress)
    }

    /**
     * Importa direto da imagem do disco de Xbox 360 (.iso, XGD2/XGD3 ou XISO). So le a
     * estrutura do disco e copia o executavel do Budokai 3 e a pasta de dados USA (os
     * ficheiros do Budokai 1 e das outras regioes ficam de fora). O .iso nao e alterado.
     */
    fun importIso(context: Context, uri: Uri, onProgress: (ImportProgress) -> Unit) {
        onProgress(ImportProgress.ReadingIso)
        val files = try {
            openUri(context, uri).use { XisoReader(it.channel).files }
        } catch (e: XisoReader.NotXisoException) {
            throw ImportException(ImportException.Reason.NOT_ISO)
        } catch (e: IOException) {
            throw ImportException(ImportException.Reason.IO, e.message ?: "")
        }
        // Pasta de dados: a que tem mais ficheiros da tabela do baserom.md com o tamanho certo
        // (desempate: chamar-se "us"). Assim nao depende do caminho exato no disco.
        val usDir = files.groupBy { it.parent }.maxByOrNull { (dir, list) ->
            val sizes = list.count { f -> BaseRom.US.any { it.name.equals(f.name, true) && it.size == f.size } }
            val names = list.count { f -> BaseRom.US.any { it.name.equals(f.name, true) } }
            sizes * 100 + names * 10 + (if (dir.substringAfterLast('/').equals("us", true)) 5 else 0)
        }?.takeIf { (_, list) -> list.any { it.name.equals("data_cmn.afs", true) } }
            ?: throw ImportException(ImportException.Reason.ISO_NO_GAME, "us/")
        val dataParent = usDir.key.substringBeforeLast('/', "")
        // Executavel: yae3_xenon.xex (de preferencia ao lado da pasta de dados); senao um
        // default.xex com o tamanho do Budokai 3 (copias digitais).
        val xex = files.filter { it.name.equals("yae3_xenon.xex", true) }
            .sortedByDescending { it.parent.equals(dataParent, true) }.firstOrNull()
            ?: files.firstOrNull { it.name.equals("default.xex", true) && it.size == BaseRom.XEX_SIZE }
            ?: throw ImportException(ImportException.Reason.ISO_NO_GAME, "yae3_xenon.xex")
        fun item(e: XisoReader.Entry) = Item(e.name, e.size, e.offset) { openUri(context, uri) }
        install(item(xex), usDir.value.map(::item), onProgress)
    }

    fun installedSizeBytes(): Long =
        AppPaths.gameDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
