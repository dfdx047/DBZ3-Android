package com.dfdx047.dragonfury.data

import android.content.Context
import java.io.File

/**
 * Pastas da app. A pasta de dados e a mesma que o GameActivity passa ao runtime
 * em REX_APP_DIR (getExternalFilesDir), por isso tudo o que o launcher escreve
 * aqui e o que o jogo le:
 *
 *   <appDir>/DBZ3/              Budokai 3 (yae3_xenon.xex + us/)
 *   <appDir>/DBZ1/              Budokai 1 (yae1_xenon.xex + us/)
 *   <appDir>/dbz3_user.toml     configuracoes do runtime do Budokai 3 (ver GameConfig; dbz1_user.toml no Budokai 1)
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

    /**
     * O jogo escolhido no launcher (aba Inicio) ou o que o GameActivity recebeu no Intent. As pastas, o toml e os
     * controles abaixo sao os dele.
     */
    @Volatile
    var game: Game = Game.B3

    fun gameDir(g: Game): File = File(appDir, g.dirName)
    fun xexFile(g: Game): File = File(gameDir(g), g.xexName)
    fun usDir(g: Game): File = File(gameDir(g), "us")

    val gameDir: File get() = gameDir(game)
    val xexFile: File get() = xexFile(game)
    val usDir: File get() = usDir(game)
    val modsDir: File get() = File(appDir, "mods")
    val userToml: File get() = File(appDir, game.tomlName)
    val logsDir: File get() = File(appDir, "logs")
    val lastRunLog: File get() = File(logsDir, "dragonfury_last.log")
    val driversDir: File get() = File(appDir, "drivers")
    val activeDriverDir: File get() = File(appDir, "driver")
    val controlsFile: File get() = File(internalDir, "controls/layout_v2.json")
    val controlsMenuFile: File get() = File(internalDir, "controls/layout_menu_v2.json")
    /** Voo livre pelo mapa do Dragon Universe. */
    val controlsMapFile: File get() = File(internalDir, "controls/layout_map_v1.json")
    val updatesDir: File get() = File(cacheDir, "updates")
    /** ISOs de PS2 do Gateway do B3 (Budokai 2, Infinite World, Lenda do Satan): o jogo procura-as em ps2_games/ ao lado do toml. */
    val ps2GamesDir: File get() = File(appDir, "ps2_games")
}
