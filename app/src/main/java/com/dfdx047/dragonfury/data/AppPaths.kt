package com.dfdx047.dragonfury.data

import android.content.Context
import java.io.File

/**
 * Pastas da app. A pasta de dados e a mesma que o GameActivity passa ao runtime
 * em REX_APP_DIR (getExternalFilesDir), por isso tudo o que o launcher escreve
 * aqui e o que o jogo le:
 *
 *   <appDir>/DBZ3/              jogo (yae3_xenon.xex + us/)
 *   <appDir>/dbz3_user.toml     configuracoes do runtime (ver GameConfig)
 *   <appDir>/mods/              mods e packs de texturas (formato do projeto original)
 *   <appDir>/driver/            driver Vulkan ativo (o GameActivity copia-o para a pasta interna)
 *   <appDir>/drivers/           drivers instalados (um por pasta)
 *   <appDir>/logs/              log da ultima sessao de jogo
 */
object AppPaths {
    lateinit var appDir: File
        private set
    lateinit var internalDir: File
        private set
    lateinit var cacheDir: File
        private set

    fun init(context: Context) {
        appDir = (context.getExternalFilesDir(null) ?: context.filesDir).also { it.mkdirs() }
        internalDir = context.filesDir
        cacheDir = context.cacheDir
    }

    val gameDir: File get() = File(appDir, "DBZ3")
    val xexFile: File get() = File(gameDir, "yae3_xenon.xex")
    val usDir: File get() = File(gameDir, "us")
    val modsDir: File get() = File(appDir, "mods")
    val userToml: File get() = File(appDir, "dbz3_user.toml")
    val logsDir: File get() = File(appDir, "logs")
    val lastRunLog: File get() = File(logsDir, "dragonfury_last.log")
    val driversDir: File get() = File(appDir, "drivers")
    val activeDriverDir: File get() = File(appDir, "driver")
    val controlsFile: File get() = File(internalDir, "controls/layout.json")
    val updatesDir: File get() = File(cacheDir, "updates")
}
