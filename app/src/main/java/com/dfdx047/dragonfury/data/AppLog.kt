package com.dfdx047.dragonfury.data

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.util.Log
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Log do app (launcher e processo do jogo), no idioma escolhido no app.
 *
 * Cada processo escreve o seu ficheiro em logs/: app_launcher.log e app_game.log (o runtime do
 * jogo continua a escrever o dragonfury_last.log). Ao passar de 1 MB o ficheiro vira .old.log e
 * comeca outro. Tudo vai tambem para o logcat (tag "DragonFury") e entra no zip do "Salvar log".
 *
 * As mensagens sao recursos de texto (values, values-pt, values-es): quem le o log ve-o na lingua
 * do app. Detalhes tecnicos (caminhos, excecoes) ficam como vem.
 */
object AppLog {
    private const val TAG = "DragonFury"
    private const val MAX_BYTES = 1L shl 20

    private var app: Context? = null
    private var file: File? = null
    private val lock = Any()
    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)

    /** Chamado no Application.onCreate de cada processo. */
    fun init(application: Application) {
        app = application
        val process = Application.getProcessName() ?: ""
        val name = if (process.endsWith(":game")) "app_game" else "app_launcher"
        val dir = AppPaths.logsDir.also { it.mkdirs() }
        file = File(dir, "$name.log")
        rotateIfBig()
        installCrashHandler()
    }

    /** Contexto com o idioma escolhido no app (as versoes antigas so o aplicam as atividades). */
    private fun localized(): Context? {
        val base = app ?: return null
        val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (tags.isEmpty()) return base
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList.forLanguageTags(tags))
        return base.createConfigurationContext(config)
    }

    /** Texto de um recurso no idioma do app (tambem para a tela de erro). */
    fun text(@StringRes res: Int, vararg args: Any): String {
        val ctx = localized() ?: return "#$res ${args.joinToString()}"
        return try {
            ctx.getString(res, *args)
        } catch (e: Exception) {
            ctx.getString(res) + " " + args.joinToString()
        }
    }

    fun i(@StringRes res: Int, vararg args: Any) = write('I', text(res, *args), null)
    fun w(@StringRes res: Int, vararg args: Any) = write('W', text(res, *args), null)
    fun e(@StringRes res: Int, error: Throwable?, vararg args: Any) = write('E', text(res, *args), error)

    /** Linha tecnica sem traducao (valores, listas, contadores). */
    fun raw(text: String) = write('I', text, null)

    /** Para o Java (GameActivity). */
    @JvmStatic fun info(@StringRes res: Int, arg: String) = i(res, arg)
    @JvmStatic fun error(@StringRes res: Int, error: Throwable?, arg: String) = e(res, error, arg)

    private fun write(level: Char, message: String, error: Throwable?) {
        when (level) {
            'E' -> Log.e(TAG, message, error)
            'W' -> Log.w(TAG, message, error)
            else -> Log.i(TAG, message)
        }
        val f = file ?: return
        val line = buildString {
            append(stamp.format(Date())).append(' ').append(level).append(' ').append(message)
            if (error != null) append('\n').append(stackOf(error))
            append('\n')
        }
        synchronized(lock) {
            try {
                f.appendText(line)
                if (f.length() > MAX_BYTES) rotateIfBig()
            } catch (_: Exception) {
            }
        }
    }

    private fun rotateIfBig() {
        val f = file ?: return
        if (f.length() <= MAX_BYTES) return
        val old = File(f.parentFile, f.nameWithoutExtension + ".old.log")
        old.delete()
        f.renameTo(old)
    }

    fun stackOf(t: Throwable): String = StringWriter().also { t.printStackTrace(PrintWriter(it)) }.toString()

    /** Excecao Java sem tratamento: fica no log e em logs/crash_last.txt antes de o app fechar. */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                write('E', "FATAL (${thread.name}): ${error.javaClass.name}: ${error.message}", error)
                File(AppPaths.logsDir, "crash_last.txt").writeText(
                    "${stamp.format(Date())} ${Application.getProcessName()} ${thread.name}\n${stackOf(error)}",
                )
            } catch (_: Exception) {
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
