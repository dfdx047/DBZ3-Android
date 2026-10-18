package com.dfdx047.dragonfury.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dfdx047.dragonfury.R
import com.dfdx047.dragonfury.data.AppLanguage
import com.dfdx047.dragonfury.data.AppLog
import com.dfdx047.dragonfury.data.SystemCheck
import com.dfdx047.dragonfury.data.TurnipCompat
import com.dfdx047.dragonfury.data.ControlStyle
import com.dfdx047.dragonfury.data.DarkMode
import com.dfdx047.dragonfury.data.FileStatus
import com.dfdx047.dragonfury.data.GameConfig
import com.dfdx047.dragonfury.data.GameFiles
import com.dfdx047.dragonfury.data.GameInstall
import com.dfdx047.dragonfury.data.GameOrientation
import com.dfdx047.dragonfury.data.ImportException
import com.dfdx047.dragonfury.data.ImportProgress
import com.dfdx047.dragonfury.data.LauncherPrefs
import com.dfdx047.dragonfury.data.ThemeMode
import com.dfdx047.dragonfury.drivers.DriverAsset
import com.dfdx047.dragonfury.drivers.DriverException
import com.dfdx047.dragonfury.drivers.DriverRelease
import com.dfdx047.dragonfury.drivers.DriverRepository
import com.dfdx047.dragonfury.drivers.DriverSource
import com.dfdx047.dragonfury.drivers.InstalledDriver
import com.dfdx047.dragonfury.game.GameActivity
import com.dfdx047.dragonfury.mods.ModInfo
import com.dfdx047.dragonfury.mods.ModInstallException
import com.dfdx047.dragonfury.mods.ModRepository
import com.dfdx047.dragonfury.mods.ModType
import com.dfdx047.dragonfury.textures.RuntimeTextureLine
import com.dfdx047.dragonfury.textures.TextureIssue
import com.dfdx047.dragonfury.textures.TexturePackReport
import com.dfdx047.dragonfury.textures.TexturePacks
import com.dfdx047.dragonfury.update.UpdateChecker
import com.dfdx047.dragonfury.update.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Mensagem curta (snackbar), resolvida no ecra com o idioma da atividade. */
data class UiMessage(@StringRes val res: Int, val args: List<Any> = emptyList(), val id: Long = System.nanoTime())

/**
 * Erro mostrado numa janela (titulo, o que aconteceu em linguagem simples e detalhes tecnicos),
 * com "Salvar log em Downloads" e o Discord. Tambem fica no log do app.
 */
data class ErrorReport(
    @StringRes val title: Int,
    @StringRes val message: Int,
    val args: List<Any> = emptyList(),
    val detail: String = "",
    val id: Long = System.nanoTime(),
)

/** Operacao longa em curso (importar, verificar, descarregar...). */
data class Busy(@StringRes val label: Int, val detail: String = "", val progress: Float? = null)

enum class UpdateState { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, FAILED }

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = LauncherPrefs(app)

    // ------------------------------------------------------------- aparencia
    var themeMode by mutableStateOf(prefs.themeMode)
        private set
    var darkMode by mutableStateOf(prefs.darkMode)
        private set
    var appLanguage by mutableStateOf(currentAppLanguage())
        private set

    fun setTheme(v: ThemeMode) { themeMode = v; prefs.themeMode = v }
    fun setDark(v: DarkMode) { darkMode = v; prefs.darkMode = v }

    fun setLanguage(v: AppLanguage) {
        appLanguage = v
        AppCompatDelegate.setApplicationLocales(
            if (v == AppLanguage.SYSTEM) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(v.tag),
        )
    }

    private fun currentAppLanguage(): AppLanguage {
        val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        return when {
            tag.startsWith("pt") -> AppLanguage.PORTUGUESE
            tag.startsWith("en") -> AppLanguage.ENGLISH
            tag.startsWith("es") -> AppLanguage.SPANISH
            else -> AppLanguage.SYSTEM
        }
    }

    // ------------------------------------------------------- jogo e launcher
    var gameOrientation by mutableStateOf(prefs.gameOrientation)
        private set
    fun setOrientation(v: GameOrientation) { gameOrientation = v; prefs.gameOrientation = v }

    var turnipCompatFlags by mutableStateOf(prefs.turnipCompatFlags)
        private set
    fun setTurnipCompatFlag(bit: Int, on: Boolean) {
        turnipCompatFlags = if (on) turnipCompatFlags or bit else turnipCompatFlags and bit.inv()
        prefs.turnipCompatFlags = turnipCompatFlags
    }

    var controlStyle by mutableStateOf(prefs.controlStyle)
        private set
    // JvmName: evita o conflito com o setter privado da propriedade (mesma assinatura JVM).
    @JvmName("changeControlStyle")
    fun setControlStyle(v: ControlStyle) { controlStyle = v; prefs.controlStyle = v }

    var directional by mutableStateOf(prefs.directional)
        private set
    @JvmName("changeDirectional")
    fun setDirectional(v: com.dfdx047.dragonfury.data.Directional) { directional = v; prefs.directional = v }

    var buttonBadge by mutableStateOf(prefs.buttonBadge)
        private set
    @JvmName("changeButtonBadge")
    fun setButtonBadge(v: com.dfdx047.dragonfury.data.ButtonBadge) { buttonBadge = v; prefs.buttonBadge = v }

    var pauseOnMenus by mutableStateOf(prefs.pauseOnMenus)
        private set
    @JvmName("changePauseOnMenus")
    fun setPauseOnMenus(v: Boolean) { pauseOnMenus = v; prefs.pauseOnMenus = v }

    var touchMode by mutableStateOf(prefs.touchMode)
        private set
    @JvmName("changeTouchMode")
    fun setTouchMode(v: com.dfdx047.dragonfury.data.TouchMode) { touchMode = v; prefs.touchMode = v }

    var touchEnabled by mutableStateOf(prefs.touchEnabled)
        private set
    // JvmName: evita o conflito com o setter privado da propriedade (mesma assinatura JVM).
    @JvmName("changeTouchEnabled")
    fun setTouchEnabled(v: Boolean) { touchEnabled = v; prefs.touchEnabled = v }

    var touchHideWithPad by mutableStateOf(prefs.touchHideWithPad)
        private set
    // JvmName: evita o conflito com o setter privado da propriedade (mesma assinatura JVM).
    @JvmName("changeTouchHideWithPad")
    fun setTouchHideWithPad(v: Boolean) { touchHideWithPad = v; prefs.touchHideWithPad = v }

    var touchHaptics by mutableStateOf(prefs.touchHaptics)
        private set
    // JvmName: evita o conflito com o setter privado da propriedade (mesma assinatura JVM).
    @JvmName("changeTouchHaptics")
    fun setTouchHaptics(v: Boolean) { touchHaptics = v; prefs.touchHaptics = v }

    var touchOpacity by mutableStateOf(prefs.touchOpacity)
        private set
    // JvmName: evita o conflito com o setter privado da propriedade (mesma assinatura JVM).
    @JvmName("changeTouchOpacity")
    fun setTouchOpacity(v: Float) { touchOpacity = v; prefs.touchOpacity = v }

    var checkUpdatesOnStart by mutableStateOf(prefs.checkUpdatesOnStart)
        private set
    // JvmName: evita o conflito com o setter privado da propriedade (mesma assinatura JVM).
    @JvmName("changeCheckUpdatesOnStart")
    fun setCheckUpdatesOnStart(v: Boolean) { checkUpdatesOnStart = v; prefs.checkUpdatesOnStart = v }

    var config by mutableStateOf(GameConfig())
        private set

    fun updateConfig(transform: (GameConfig) -> GameConfig) {
        val next = transform(config)
        if (next == config) return
        config = next
        viewModelScope.launch(Dispatchers.IO) { GameConfig.save(next) }
    }

    var busy by mutableStateOf<Busy?>(null)
        private set
    var message by mutableStateOf<UiMessage?>(null)
    private fun say(@StringRes res: Int, vararg args: Any) {
        message = UiMessage(res, args.toList())
    }

    /** Erro na tela (janela com explicacao, detalhes e o botao de salvar o log). */
    var error by mutableStateOf<ErrorReport?>(null)

    private fun fail(
        @StringRes title: Int,
        @StringRes text: Int,
        vararg args: Any,
        detail: String = "",
        cause: Throwable? = null,
    ) {
        AppLog.e(R.string.log_error_shown, cause, AppLog.text(title), AppLog.text(text, *args) +
            if (detail.isNotEmpty()) " | $detail" else "")
        error = ErrorReport(title, text, args.toList(), detail.ifEmpty { cause?.let { "${it.javaClass.simpleName}: ${it.message}" } ?: "" })
    }

    fun dismissError() {
        error = null
    }

    /** Erro inesperado numa operacao: aparece na tela em vez de fechar o app. */
    private fun unexpected(e: Throwable) {
        fail(R.string.err_title_unexpected, R.string.err_unexpected, e.message ?: e.javaClass.simpleName, cause = e)
    }

    var install by mutableStateOf<GameInstall?>(null)
        private set

    /** Ao voltar ao launcher: o menu rapido do jogo pode ter mudado o toml. */
    fun reloadAfterGame() {
        viewModelScope.launch(Dispatchers.IO) {
            val c = GameConfig.load()
            withContext(Dispatchers.Main) { config = c }
            runtimeTextureLog = TexturePacks.runtimeLog()
            checkGameExit()
        }
    }

    /** Se o processo do jogo terminou com erro desde a ultima vez, diz porque. */
    private fun checkGameExit() {
        val ctx = getApplication<Application>()
        val since = prefs.lastGameExitSeen.takeIf { it > 0 } ?: run {
            prefs.lastGameExitSeen = System.currentTimeMillis()
            return
        }
        val found = SystemCheck.lastGameExit(ctx, since)
        val gpuLost = SystemCheck.gpuLost(since)
        if (found == null && !gpuLost) return
        prefs.lastGameExitSeen = found?.timestamp ?: System.currentTimeMillis()
        val runtime = SystemCheck.lastRuntimeErrors()
        val detail = buildString {
            if (found != null) {
                append(found.reasonName())
                if (found.description.isNotBlank()) append(" · ").append(found.description)
            } else {
                append("VK_ERROR_DEVICE_LOST")
            }
            runtime.forEach { append('\n').append(it) }
        }
        if (gpuLost) {
            // A GPU caiu: o problema e o driver (ou a GPU), nao o port em si. Com um Turnip, o
            // proximo arranque junta mais correcoes sozinho (TurnipCompat / GameActivity).
            val driver = DriverRepository.activeDir()
            AppLog.e(R.string.log_game_gpu_lost, null, driver ?: "system")
            val name = driver ?: ctx.getString(R.string.driver_system_name)
            if (driver != null && TurnipCompat.isTurnip(driver)) {
                val level = TurnipCompat.raise(ctx, driver)
                if (level > 0) {
                    AppLog.w(R.string.log_turnip_raise, driver, level)
                    fail(R.string.err_title_game, R.string.err_game_gpu_lost_turnip, name, level, detail = detail)
                } else {
                    fail(R.string.err_title_game, R.string.err_game_gpu_lost_turnip_last, name, detail = detail)
                }
                return
            }
            fail(R.string.err_title_game, R.string.err_game_gpu_lost, name, detail = detail)
            return
        }
        val exit = found ?: return
        AppLog.e(R.string.log_game_exit, null, exit.reasonName(), exit.description)
        val cpu = SystemCheck.cpu()
        when {
            exit.isIllegalInstruction || (exit.isNativeCrash && !cpu.supported) ->
                fail(R.string.err_title_game, R.string.err_game_sigill, socName, detail = detail)
            exit.isNativeCrash -> fail(R.string.err_title_game, R.string.err_game_native, exit.signal, detail = detail)
            exit.isJavaCrash -> {
                val crash = java.io.File(com.dfdx047.dragonfury.data.AppPaths.logsDir, "crash_last.txt")
                    .takeIf { it.isFile }?.readLines()?.take(8)?.joinToString("\n").orEmpty()
                fail(R.string.err_title_game, R.string.err_game_java, exit.description.ifBlank { "-" },
                    detail = if (crash.isNotEmpty()) crash else detail)
            }
            exit.isLowMemory -> fail(R.string.err_title_game, R.string.err_game_low_memory, detail = detail)
            exit.isFrozen -> fail(R.string.err_title_game, R.string.err_game_frozen, detail = detail)
            else -> fail(R.string.err_title_game, R.string.err_game_other, exit.reasonName(), detail = detail)
        }
    }

    fun refreshInstall(fullHash: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (fullHash) busy = Busy(R.string.busy_verifying)
            val result = try {
                GameFiles.check(fullHash) { p ->
                    if (p is ImportProgress.Hashing && fullHash) {
                        busy = Busy(R.string.busy_verifying, p.file, p.done.toFloat() / p.total.coerceAtLeast(1))
                    }
                }
            } finally {
                if (fullHash) busy = null
            }
            install = result
            if (fullHash) AppLog.i(R.string.log_verify, "true")
            AppLog.i(R.string.log_verify_result, result.checks.joinToString { "${it.name}=${it.status}" })
            if (fullHash) {
                if (result.playable) say(R.string.msg_verify_ok)
                else say(R.string.msg_verify_problems)
            }
        }
    }

    fun importGame(uri: Uri) = runImport(uri) { ctx, progress -> GameFiles.import(ctx, uri, progress) }

    /** Importa direto do .iso do disco de Xbox 360. */
    fun importIso(uri: Uri) = runImport(uri) { ctx, progress -> GameFiles.importIso(ctx, uri, progress) }

    private fun runImport(source: Uri, work: (Context, (ImportProgress) -> Unit) -> Unit) {
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            busy = Busy(R.string.busy_importing)
            val started = System.nanoTime()
            AppLog.i(R.string.log_import_start, source.toString())
            var lastFile = ""
            try {
                work(ctx) { p ->
                    val file = when (p) {
                        is ImportProgress.Copying -> p.file
                        is ImportProgress.Hashing -> p.file
                        ImportProgress.ReadingIso -> "ISO"
                    }
                    if (file != lastFile) {
                        lastFile = file
                        AppLog.i(R.string.log_import_file, file)
                    }
                    busy = when (p) {
                        is ImportProgress.Copying -> Busy(R.string.busy_importing, p.file, p.done.toFloat() / p.total.coerceAtLeast(1))
                        is ImportProgress.Hashing -> Busy(R.string.busy_checking_xex, p.file, p.done.toFloat() / p.total.coerceAtLeast(1))
                        ImportProgress.ReadingIso -> Busy(R.string.busy_reading_iso)
                    }
                }
                val seconds = ((System.nanoTime() - started) / 1_000_000_000L).toInt()
                AppLog.i(R.string.log_import_ok, seconds)
                say(R.string.msg_import_ok_time, seconds)
            } catch (e: ImportException) {
                AppLog.e(R.string.log_import_failed, e, e.reason.name, e.detail)
                fail(
                    R.string.err_title_import,
                    when (e.reason) {
                        ImportException.Reason.NO_XEX -> R.string.err_import_no_xex
                        ImportException.Reason.NO_US_FOLDER -> R.string.err_import_no_us
                        ImportException.Reason.EU_XEX -> R.string.err_import_eu
                        ImportException.Reason.BAD_XEX -> R.string.err_import_bad_xex
                        ImportException.Reason.MISSING_REQUIRED -> R.string.err_import_missing
                        ImportException.Reason.IO -> R.string.err_import_io
                        ImportException.Reason.NOT_ISO -> R.string.err_import_not_iso
                        ImportException.Reason.ISO_NO_GAME -> R.string.err_import_iso_no_game
                        ImportException.Reason.NO_SPACE -> R.string.err_import_no_space
                    },
                    e.detail.ifEmpty { "-" },
                    detail = "${e.reason} · $lastFile",
                )
            } catch (e: Exception) {
                AppLog.e(R.string.log_import_failed, e, e.javaClass.simpleName, e.message ?: "-")
                fail(R.string.err_title_import, R.string.err_import_io, e.message ?: "-", detail = lastFile, cause = e)
            } finally {
                busy = null
            }
            install = GameFiles.check(false)
        }
    }

    /** Grava as opcoes e abre o jogo. */
    fun play(context: Context) {
        val inst = install
        if (inst == null || !inst.playable) {
            val missing = inst?.checks?.filter { it.required && it.status != FileStatus.OK }
                ?.joinToString { "${it.name}: ${it.status}" }.orEmpty()
            fail(
                R.string.err_title_files,
                if (inst?.xex?.status == FileStatus.EU_REGION) R.string.err_import_eu else R.string.msg_not_installed,
                "-",
                detail = missing,
            )
            return
        }
        val cpu = SystemCheck.cpu()
        if (!cpu.supported) {
            AppLog.w(R.string.log_cpu_blocked, cpu.features)
            fail(
                R.string.err_title_cpu,
                R.string.err_cpu_unsupported,
                socName,
                cpu.coresWithoutAtomics,
                cpu.cores,
                detail = "Features: ${cpu.features}",
            )
            return
        }
        AppLog.i(
            R.string.log_play,
            "${config.resolutionChoice} fps=${config.frameCap} fx=${config.fxBloom}/${config.fxDof}/${config.fxFxaa} " +
                "driver=${activeDriver ?: "system"}",
        )
        if (prefs.lastGameExitSeen == 0L) prefs.lastGameExitSeen = System.currentTimeMillis()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { GameConfig.save(config) }
            context.startActivity(Intent(context, GameActivity::class.java).setAction(PLAY_ACTION))
        }
    }

    fun createShortcut(context: Context) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            say(R.string.msg_shortcut_unsupported)
            return
        }
        val info = ShortcutInfoCompat.Builder(context, "play_dbz3")
            .setShortLabel(context.getString(R.string.shortcut_short))
            .setLongLabel(context.getString(R.string.shortcut_long))
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(Intent(context, GameActivity::class.java).setAction(PLAY_ACTION))
            .build()
        if (ShortcutManagerCompat.requestPinShortcut(context, info, null)) say(R.string.msg_shortcut_requested)
        else say(R.string.msg_shortcut_unsupported)
    }

    // ------------------------------------------------------------------ mods
    var mods by mutableStateOf<List<ModInfo>>(emptyList())
        private set

    fun refreshMods() {
        viewModelScope.launch(Dispatchers.IO) { mods = ModRepository.listAll() }
    }

    fun setModEnabled(mod: ModInfo, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            AppLog.i(R.string.log_mod_toggle, mod.displayName, if (enabled) "on" else "off")
            ModRepository.setEnabled(mod, enabled)
            mods = ModRepository.listAll()
        }
    }

    fun deleteMod(mod: ModInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            if (ModRepository.delete(mod)) {
                AppLog.i(R.string.log_mod_deleted, mod.displayName)
                say(R.string.msg_mod_deleted, mod.displayName)
            }
            mods = ModRepository.listAll()
            textureReports = null
        }
    }

    fun fixMod(mod: ModInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            val fixed = ModRepository.fixNested(mod)
            AppLog.i(R.string.log_mod_fix, mod.displayName, fixed.toString())
            say(if (fixed) R.string.msg_mod_fixed else R.string.msg_mod_fix_failed, mod.displayName)
            mods = ModRepository.listAll()
        }
    }

    private fun displayName(uri: Uri): String {
        val ctx = getApplication<Application>()
        return ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: "mod.zip"
    }

    fun installModZip(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) { doInstallZip(uri) }
    }

    private fun doInstallZip(uri: Uri) {
        val ctx = getApplication<Application>()
        val name = displayName(uri)
        busy = Busy(R.string.busy_installing_mod, name)
        AppLog.i(R.string.log_mod_install, name)
        try {
            val installed = ModRepository.installZip(ctx, uri, name) { bytes ->
                busy = Busy(R.string.busy_installing_mod, "$name — ${bytes / (1024 * 1024)} MB")
            }
            AppLog.i(R.string.log_mod_installed, installed.joinToString())
            say(R.string.msg_mod_installed, installed.joinToString())
        } catch (e: ModInstallException) {
            AppLog.e(R.string.log_mod_failed, e, e.reason.name, e.message ?: "-")
            fail(
                R.string.err_title_mod,
                when (e.reason) {
                    ModInstallException.Reason.NOT_ZIP -> R.string.err_mod_not_zip
                    ModInstallException.Reason.EMPTY -> R.string.err_mod_empty
                    ModInstallException.Reason.UNSAFE_PATH -> R.string.err_mod_unsafe
                    ModInstallException.Reason.IO -> R.string.err_mod_io
                },
                e.message ?: "-",
                detail = "${e.reason} · $name",
            )
        } catch (e: Exception) {
            AppLog.e(R.string.log_mod_failed, e, e.javaClass.simpleName, e.message ?: "-")
            fail(R.string.err_title_mod, R.string.err_mod_io, e.message ?: "-", detail = name, cause = e)
        } finally {
            busy = null
        }
        mods = ModRepository.listAll()
        textureReports = null
    }

    // -------------------------------------------------------------- texturas
    var textureReports by mutableStateOf<List<TexturePackReport>?>(null)
        private set
    var runtimeTextureLog by mutableStateOf<List<RuntimeTextureLine>>(emptyList())
        private set

    val texturePacks: List<ModInfo> get() = mods.filter { it.type == ModType.TEXTURES }

    fun validateTextures() {
        viewModelScope.launch(Dispatchers.IO) {
            busy = Busy(R.string.busy_validating_textures)
            AppLog.i(R.string.log_tex_validate)
            try {
                val reports = TexturePacks.validateAll { done, total ->
                    busy = Busy(R.string.busy_validating_textures, "$done / $total", done.toFloat() / total.coerceAtLeast(1))
                }
                textureReports = reports
                reports.forEach { r -> AppLog.i(R.string.log_tex_result, r.mod.displayName, r.okCount, r.errorCount) }
                runtimeTextureLog = TexturePacks.runtimeLog()
            } catch (e: Exception) {
                unexpected(e)
            } finally {
                busy = null
            }
        }
    }

    fun setTexturePackEnabled(mod: ModInfo, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            ModRepository.setEnabled(mod, enabled)
            mods = ModRepository.listAll()
            validateTextures()
        }
    }

    fun refreshRuntimeLog() {
        viewModelScope.launch(Dispatchers.IO) { runtimeTextureLog = TexturePacks.runtimeLog() }
    }

    fun importTextureImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            busy = Busy(R.string.busy_importing_textures)
            AppLog.i(R.string.log_tex_import, uris.size)
            try {
                val (ok, refused) = TexturePacks.importImages(ctx, uris)
                AppLog.i(R.string.log_tex_import_result, ok, refused.size,
                    refused.take(20).joinToString { "${it.first}=${it.second}" })
                if (refused.isEmpty()) {
                    say(R.string.msg_textures_imported, ok)
                } else {
                    val first = refused.first()
                    say(R.string.msg_textures_refused, ok, refused.size, first.first, textureIssueName(first.second))
                }
            } catch (e: Exception) {
                fail(R.string.err_title_textures, R.string.err_unexpected, e.message ?: "-", cause = e)
            } finally {
                busy = null
            }
            mods = ModRepository.listAll()
            textureReports = null
            validateTextures()
        }
    }

    private fun textureIssueName(i: TextureIssue): String {
        val ctx = getApplication<Application>()
        return ctx.getString(
            when (i) {
                TextureIssue.OK -> R.string.tex_issue_ok
                TextureIssue.BAD_NAME -> R.string.tex_issue_bad_name
                TextureIssue.UNREADABLE -> R.string.tex_issue_unreadable
                TextureIssue.DDS_FORMAT -> R.string.tex_issue_dds
                TextureIssue.BAD_SIZE -> R.string.tex_issue_bad_size
                TextureIssue.DUPLICATE -> R.string.tex_issue_duplicate
            },
        )
    }

    fun installTexturePackZip(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            doInstallZip(uri)
            validateTextures()
        }
    }

    // --------------------------------------------------------------- drivers
    val isAdreno = DriverRepository.isAdreno()
    val isMali = DriverRepository.isMali()
    val socName = DriverRepository.socName()
    var installedDrivers by mutableStateOf<List<InstalledDriver>>(emptyList())
        private set
    var activeDriver by mutableStateOf<String?>(null)
        private set
    /** Resultado do teste do driver alternativo no ultimo arranque (null = sem informacao). */
    var activeDriverStatus by mutableStateOf<com.dfdx047.dragonfury.drivers.DriverStatus?>(null)
        private set
    var driverSource by mutableStateOf(DriverRepository.SOURCES.first())
        private set
    var driverReleases by mutableStateOf<List<DriverRelease>>(emptyList())
        private set
    var driverReleasesLoading by mutableStateOf(false)
        private set
    var driverReleasesError by mutableStateOf(false)
        private set
    /**
     * Aviso de primeiro arranque (GPUs e drivers). Mostrado uma vez a todos; tambem se
     * abre pelo Sobre. Substitui o antigo aviso do Turnip (o texto ja fala do Turnip).
     */
    var showGpuNotice by mutableStateOf(!prefs.gpuNoticeShown)
        private set

    fun openGpuNotice() {
        showGpuNotice = true
    }

    fun dismissGpuNotice() {
        showGpuNotice = false
        prefs.gpuNoticeShown = true
        prefs.turnipNoticeShown = true
    }

    fun refreshDrivers() {
        viewModelScope.launch(Dispatchers.IO) {
            installedDrivers = DriverRepository.installed()
            activeDriver = DriverRepository.activeDir()
            val lib = DriverRepository.activeLibraryName()
            activeDriverStatus = DriverRepository.lastStatus()?.takeIf { lib != null && it.driver == lib }
            AppLog.i(R.string.log_driver_active, activeDriver ?: AppLog.text(R.string.log_system_driver))
            activeDriverStatus?.let { st ->
                AppLog.i(R.string.log_driver_status, "${if (st.ok) "ok" else "FAIL"} ${st.driver} ${st.detail}")
            }
        }
    }

    fun loadDriverReleases(source: DriverSource = driverSource) {
        driverSource = source
        viewModelScope.launch(Dispatchers.IO) {
            driverReleasesLoading = true
            driverReleasesError = false
            AppLog.i(R.string.log_driver_list, source.apiUrl)
            driverReleases = try {
                DriverRepository.fetchReleases(source).also {
                    AppLog.i(R.string.log_driver_list_result, source.apiUrl, it.size)
                }
            } catch (e: DriverException) {
                AppLog.e(R.string.log_driver_list_failed, e, e.message ?: e.reason.name)
                driverReleasesError = true
                emptyList()
            } finally {
                driverReleasesLoading = false
            }
        }
    }

    fun downloadDriver(asset: DriverAsset) {
        viewModelScope.launch(Dispatchers.IO) {
            busy = Busy(R.string.busy_downloading_driver, asset.name)
            AppLog.i(R.string.log_driver_download, "${asset.name} ${asset.url}")
            try {
                val d = DriverRepository.download(asset) { done, total ->
                    busy = Busy(R.string.busy_downloading_driver, asset.name, if (total > 0) done.toFloat() / total else null)
                }
                AppLog.i(R.string.log_driver_installed, d.name)
                say(R.string.msg_driver_installed, d.name)
            } catch (e: DriverException) {
                AppLog.e(R.string.log_driver_failed, e, e.reason.name, e.message ?: "-")
                fail(R.string.err_title_driver, driverError(e), e.message ?: "-", detail = "${e.reason} · ${asset.name}")
            } catch (e: Exception) {
                AppLog.e(R.string.log_driver_failed, e, e.javaClass.simpleName, e.message ?: "-")
                fail(R.string.err_title_driver, R.string.err_driver_io, e.message ?: "-", detail = asset.name, cause = e)
            } finally {
                busy = null
            }
            refreshDrivers()
        }
    }

    fun importDriver(uri: Uri) {
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val name = displayName(uri)
            busy = Busy(R.string.busy_installing_driver, name)
            AppLog.i(R.string.log_driver_import, name)
            try {
                val d = DriverRepository.importUri(ctx, uri, name)
                AppLog.i(R.string.log_driver_installed, d.name)
                say(R.string.msg_driver_installed, d.name)
            } catch (e: DriverException) {
                AppLog.e(R.string.log_driver_failed, e, e.reason.name, e.message ?: "-")
                fail(R.string.err_title_driver, driverError(e), e.message ?: "-", detail = "${e.reason} · $name")
            } catch (e: Exception) {
                AppLog.e(R.string.log_driver_failed, e, e.javaClass.simpleName, e.message ?: "-")
                fail(R.string.err_title_driver, R.string.err_driver_io, e.message ?: "-", detail = name, cause = e)
            } finally {
                busy = null
            }
            refreshDrivers()
        }
    }

    private fun driverError(e: DriverException) = when (e.reason) {
        DriverException.Reason.NO_LIBRARY -> R.string.err_driver_no_lib
        DriverException.Reason.NOT_ZIP -> R.string.err_driver_not_zip
        DriverException.Reason.NETWORK -> R.string.err_network
        DriverException.Reason.IO -> R.string.err_driver_io
    }

    fun activateDriver(d: InstalledDriver) {
        viewModelScope.launch(Dispatchers.IO) {
            DriverRepository.activate(d)
            say(R.string.msg_driver_active, d.name)
            refreshDrivers()
        }
    }

    fun useSystemDriver() {
        viewModelScope.launch(Dispatchers.IO) {
            DriverRepository.deactivate()
            say(R.string.msg_driver_system)
            refreshDrivers()
        }
    }

    fun deleteDriver(d: InstalledDriver) {
        viewModelScope.launch(Dispatchers.IO) {
            DriverRepository.delete(d)
            AppLog.i(R.string.log_driver_deleted, d.name)
            refreshDrivers()
        }
    }

    // ------------------------------------------------------------------ logs
    fun exportLogs(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            busy = Busy(R.string.busy_exporting_logs)
            try {
                val name = com.dfdx047.dragonfury.data.LogExporter.export(context.applicationContext)
                AppLog.i(R.string.log_logs_saved, name)
                say(R.string.msg_logs_exported, name)
            } catch (e: Exception) {
                AppLog.e(R.string.log_logs_saved, e, e.message ?: "-")
                fail(R.string.err_title_logs, R.string.err_logs_export, e.message ?: "-", cause = e)
            } finally {
                busy = null
            }
        }
    }

    // ------------------------------------------------------------ atualizacao
    var updateState by mutableStateOf(UpdateState.IDLE)
        private set
    var updateInfo by mutableStateOf<UpdateInfo?>(null)
        private set
    var showUpdateDialog by mutableStateOf(false)
    private var startupCheckDone = false

    fun checkUpdatesOnLaunch() {
        if (startupCheckDone || !prefs.checkUpdatesOnStart) return
        startupCheckDone = true
        checkUpdates(manual = false)
    }

    fun checkUpdates(manual: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            updateState = UpdateState.CHECKING
            AppLog.i(R.string.log_update_check)
            try {
                val info = UpdateChecker.check()
                AppLog.i(R.string.log_update_result, info?.version ?: "-")
                updateInfo = info
                updateState = if (info == null) UpdateState.UP_TO_DATE else UpdateState.AVAILABLE
                if (info != null && (manual || prefs.skippedUpdate != info.version)) showUpdateDialog = true
                if (info == null && manual) say(R.string.msg_up_to_date)
            } catch (e: Exception) {
                updateState = UpdateState.FAILED
                AppLog.e(R.string.log_update_failed, e, e.message ?: "-")
                if (manual) fail(R.string.err_title_update, R.string.err_update_check, cause = e)
            }
        }
    }

    fun skipUpdate() {
        prefs.skippedUpdate = updateInfo?.version
        showUpdateDialog = false
    }

    fun downloadUpdate(context: Context) {
        val info = updateInfo ?: return
        if (info.apkUrl == null) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        showUpdateDialog = false
        viewModelScope.launch(Dispatchers.IO) {
            busy = Busy(R.string.busy_downloading_update, info.version)
            AppLog.i(R.string.log_update_download, "${info.version} ${info.apkUrl}")
            try {
                val apk = UpdateChecker.downloadApk(info) { done, total ->
                    busy = Busy(R.string.busy_downloading_update, info.version, if (total > 0) done.toFloat() / total else null)
                }
                withContext(Dispatchers.Main) { UpdateChecker.install(context, apk) }
            } catch (e: Exception) {
                AppLog.e(R.string.log_update_failed, e, e.message ?: "-")
                fail(R.string.err_title_update, R.string.err_network, e.message ?: "-", cause = e)
            } finally {
                busy = null
            }
        }
    }

    // Fica no fim da classe: as propriedades acima ja tem de estar inicializadas.
    init {
        viewModelScope.launch(Dispatchers.IO) {
            val cpu = SystemCheck.cpu()
            AppLog.i(
                R.string.log_device,
                "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                android.os.Build.VERSION.RELEASE,
                socName,
                if (cpu.supported) "ok" else "NO (${cpu.coresWithoutAtomics}/${cpu.cores})",
                SystemCheck.ram().let { "${it.totalMb} MB (${it.availableMb} MB / avail)" },
            )
            var c = GameConfig.load()
            // Render targets diretos: caminho principal de desempenho (e os interruptores
            // dos efeitos so funcionam com ele). Religa-se uma vez para quem o desligou.
            if (!prefs.rtDirectMigrated) {
                c = c.copy(rtDirect = true)
                prefs.rtDirectMigrated = true
            }
            // Mali: uma vez (primeiro arranque ou ao atualizar), 540p e efeitos desligados.
            // Depois o usuario pode mudar a vontade.
            if (!prefs.maliPresetApplied) {
                if (DriverRepository.isMali()) c = c.withMaliPreset().copy(rtDirect = true)
                prefs.maliPresetApplied = true
            }
            // 0.6.1: bilinear passa a ser o filtro padrao (o AMD FSR custa muito desempenho).
            if (!prefs.bilinearMigrated) {
                c = c.copy(presentEffect = "bilinear")
                prefs.bilinearMigrated = true
            }
            val loaded = c
            AppLog.i(R.string.log_settings, "${loaded.resolutionChoice} fps=${loaded.frameCap} " +
                "fx=${loaded.fxBloom}/${loaded.fxDof}/${loaded.fxFxaa} rt_direct=${loaded.rtDirect}")
            withContext(Dispatchers.Main) { config = loaded }
            // Garante que o toml existe com as opcoes do launcher desde o primeiro arranque.
            GameConfig.save(loaded)
        }
        refreshInstall(false)
        refreshMods()
        refreshDrivers()
    }

    companion object {
        const val PLAY_ACTION = "com.dfdx047.dragonfury.PLAY"
    }
}
