package com.dfdx047.dragonfury.data

import android.content.Context
import org.json.JSONObject
import java.io.DataInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * O codigo de cada jogo (libgame_b3.so / libgame_b1.so: recompilado e glue Android) vem no APK so encriptado
 * (assets/libgame_<id>.zdat, tools/pack-libgame.py; o metodo e o do Zandatsu e do Dragon Burst). A chave e o SHA-256 da
 * imagem PE do executavel desse jogo (yae3_xenon.xex / yae1_xenon.xex), lida pelo libmain.so
 * (app/src/main/cpp/loader/xex_image.cpp); o .so desencriptado fica em getFilesDir() (privado da app) e o GameActivity
 * carrega-o de la. Feito no import, no JOGAR e no GameActivity quando o APK traz outro (libgame_<id>.json "id").
 */
object GameLib {
    fun file(context: Context, game: Game) = File(context.filesDir, "${game.libName}.so")
    private fun stamp(context: Context, game: Game) = File(context.filesDir, "${game.libName}.id")

    private class Meta(val titleId: String, val xexSha1: String, val keySha256: String, val id: String, val size: Long)

    private fun meta(context: Context, game: Game): Meta = context.assets.open("${game.libName}.json").use { s ->
        val j = JSONObject(s.readBytes().decodeToString())
        Meta(j.getString("title_id"), j.getString("xex_sha1"), j.getString("key_sha256"), j.getString("id"), j.getLong("size"))
    }

    /** O .so desencriptado existe e e o deste APK. */
    fun ready(context: Context, game: Game): Boolean = try {
        val m = meta(context, game)
        file(context, game).length() == m.size && stamp(context, game).readText() == m.id
    } catch (_: Exception) {
        false
    }

    @JvmStatic
    private external fun nativeXexImage(path: String, titleOut: IntArray, errorOut: Array<String?>): ByteArray?

    /**
     * Le o executavel instalado do jogo, deriva a chave e desencripta o libgame dele para getFilesDir(). Executavel de
     * outra versao: ImportException UNSUPPORTED_VERSION com o title ID e o SHA-1 esperados.
     */
    @JvmStatic
    @JvmOverloads
    fun install(context: Context, game: Game, onProgress: (ImportProgress) -> Unit = {}) {
        val m = meta(context, game)
        if (ready(context, game)) return
        val xex = AppPaths.xexFile(game)
        if (!xex.isFile) throw ImportException(ImportException.Reason.NO_XEX, game.xexName)
        System.loadLibrary("main")
        onProgress(ImportProgress.Reading(game.xexName))
        val title = IntArray(1)
        val error = arrayOfNulls<String>(1)
        val pe = nativeXexImage(xex.absolutePath, title, error)
        val sha256 = { b: ByteArray -> MessageDigest.getInstance("SHA-256").digest(b) }
        val key = pe?.let(sha256)
        if (key == null || sha256(key).hex() != m.keySha256) {
            val sha1 = try { GameFiles.sha1File(xex) } catch (_: Exception) { "-" }
            throw ImportException(
                ImportException.Reason.UNSUPPORTED_VERSION,
                "%s: title ID %08X, SHA-1 %s%s; esperado title ID %s, SHA-1 %s".format(
                    game.xexName, title[0], sha1.take(12), error[0]?.let { " ($it)" } ?: "", m.titleId, m.xexSha1.take(12),
                ),
            )
        }

        val out = file(context, game)
        val part = File(out.parentFile, out.name + ".part")
        val started = System.currentTimeMillis()
        stamp(context, game).delete()
        try {
            context.assets.open("${game.libName}.zdat").use { raw ->
                val input = DataInputStream(raw.buffered(1 shl 20))
                val header = ByteArray(24).also { input.readFully(it) }
                val hb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                if (hb.getInt(0) != 0x3154445A) throw ImportException(ImportException.Reason.IO, "${game.libName}.zdat")  // "ZDT1"
                val chunk = hb.getInt(4)
                val size = hb.getLong(8)
                val nonce = ByteArray(12).also { System.arraycopy(header, 16, it, 0, 8) }
                val spec = SecretKeySpec(key, "AES")
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val buf = ByteArray(chunk + 16)
                part.outputStream().buffered(1 shl 20).use { o ->
                    var done = 0L
                    var i = 0
                    while (done < size) {
                        val n = minOf(chunk.toLong(), size - done).toInt()
                        input.readFully(buf, 0, n + 16)
                        ByteBuffer.wrap(nonce, 8, 4).order(ByteOrder.BIG_ENDIAN).putInt(i)
                        cipher.init(Cipher.DECRYPT_MODE, spec, GCMParameterSpec(128, nonce))
                        cipher.updateAAD(header)
                        o.write(cipher.doFinal(buf, 0, n + 16))
                        done += n
                        i++
                        onProgress(ImportProgress.Unpacking(done, size))
                    }
                }
            }
            if (!part.renameTo(out)) throw ImportException(ImportException.Reason.IO, out.name)
            stamp(context, game).writeText(m.id)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw ImportException(ImportException.Reason.UNSUPPORTED_VERSION, "${game.libName}.zdat: ${e.message}")
        } finally {
            part.delete()
        }
        val line = "import: ${game.libName}.so pronto (${out.length() / (1 shl 20)} MB, id ${m.id}, title ${m.titleId}, " +
            "${System.currentTimeMillis() - started} ms)"
        android.util.Log.i("DragonFury", line)
        // Tambem em logs/update.log (vai no Exportar logs): quando o APK trouxe outro jogo e ele foi refeito.
        try {
            File(AppPaths.logsDir.apply { mkdirs() }, "update.log")
                .appendText("${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT).format(java.util.Date())} $line\n")
        } catch (_: Exception) {
        }
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
}
