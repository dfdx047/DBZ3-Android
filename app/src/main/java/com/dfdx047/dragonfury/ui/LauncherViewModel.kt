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
            else -> AppLanguage.SYSTEM
        }
    }

    // ------------------------------------------------------- jogo e launcher
    var gameOrientation by mutableStateOf(prefs.gameOrientation)
        private set
    fun setOrientation(v: GameOrientation) { gameOrientation = v; prefs.gameOrientation = v }

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

    var install by mutableStateOf<GameInstall?>(null)
        private set

    /** Ao voltar ao launcher: o menu rapido do jogo pode ter mudado o toml. */
    fun reloadAfterGame() {
        viewModelScope.launch(Dispatchers.IO) {
            val c = GameConfig.load()
            withContext(Dispatchers.Main) { config = c }
            runtimeTextureLog = TexturePacks.runtimeLog()
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
            if (fullHash) {
                if (result.playable) say(R.string.msg_verify_ok)
                else say(R.string.msg_verify_problems)
            }
        }
    }

    fun importGame(uri: Uri) {
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            busy = Busy(R.string.busy_importing)
            try {
                GameFiles.import(ctx, uri) { p ->
                    busy = when (p) {
                        is ImportProgress.Copying -> Busy(R.string.busy_importing, p.file, p.done.toFloat() / p.total.coerceAtLeast(1))
                        is ImportProgress.Hashing -> Busy(R.string.busy_checking_xex, p.file, p.done.toFloat() / p.total.coerceAtLeast(1))
                    }
                }
                say(R.string.msg_import_ok)
            } catch (e: ImportException) {
                say(
                    when (e.reason) {
                        ImportException.Reason.NO_XEX -> R.string.err_import_no_xex
                        ImportException.Reason.NO_US_FOLDER -> R.string.err_import_no_us
                        ImportException.Reason.EU_XEX -> R.string.err_import_eu
                        ImportException.Reason.BAD_XEX -> R.string.err_import_bad_xex
                        ImportException.Reason.MISSING_REQUIRED -> R.string.err_import_missing
                        ImportException.Reason.IO -> R.string.err_import_io
                    },
                    e.detail.ifEmpty { "-" },
                )
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
            say(if (inst?.xex?.status == FileStatus.EU_REGION) R.string.err_import_eu else R.string.msg_not_installed, "-")
            return
        }
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
            ModRepository.setEnabled(mod, enabled)
            mods = ModRepository.listAll()
        }
    }

    fun deleteMod(mod: ModInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            if (ModRepository.delete(mod)) say(R.string.msg_mod_deleted, mod.displayName)
            mods = ModRepository.listAll()
            textureReports = null
        }
    }

    fun fixMod(mod: ModInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            say(if (ModRepository.fixNested(mod)) R.string.msg_mod_fixed else R.string.msg_mod_fix_failed, mod.displayName)
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
        try {
            val installed = ModRepository.installZip(ctx, uri, name) { bytes ->
                busy = Busy(R.string.busy_installing_mod, "$name — ${bytes / (1024 * 1024)} MB")
            }
            say(R.string.msg_mod_installed, installed.joinToString())
        } catch (e: ModInstallException) {
            say(
                when (e.reason) {
                    ModInstallException.Reason.NOT_ZIP -> R.string.err_mod_not_zip
                    ModInstallException.Reason.EMPTY -> R.string.err_mod_empty
                    ModInstallException.Reason.UNSAFE_PATH -> R.string.err_mod_unsafe
                    ModInstallException.Reason.IO -> R.string.err_mod_io
                },
                e.message ?: "-",
            )
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
            try {
                textureReports = TexturePacks.validateAll { done, total ->
                    busy = Busy(R.string.busy_validating_textures, "$done / $total", done.toFloat() / total.coerceAtLeast(1))
                }
                runtimeTextureLog = TexturePacks.runtimeLog()
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
            try {
                val (ok, refused) = TexturePacks.importImages(ctx, uris)
                if (refused.isEmpty()) {
                    say(R.string.msg_textures_imported, ok)
                } else {
                    val first = refused.first()
                    say(R.string.msg_textures_refused, ok, refused.size, first.first, textureIssueName(first.second))
                }
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
    val socName = DriverRepository.socName()
    var installedDrivers by mutableStateOf<List<InstalledDriver>>(emptyList())
        private set
    var activeDriver by mutableStateOf<String?>(null)
        private set
    var driverSource by mutableStateOf(DriverRepository.SOURCES.first())
        private set
    var driverReleases by mutableStateOf<List<DriverRelease>>(emptyList())
        private set
    var driverReleasesLoading by mutableStateOf(false)
        private set
    var driverReleasesError by mutableStateOf(false)
        private set
    var showTurnipNotice by mutableStateOf(isAdreno && !prefs.turnipNoticeShown)
        private set

    fun dismissTurnipNotice() {
        showTurnipNotice = false
        prefs.turnipNoticeShown = true
    }

    fun refreshDrivers() {
        viewModelScope.launch(Dispatchers.IO) {
            installedDrivers = DriverRepository.installed()
            activeDriver = DriverRepository.activeDir()
        }
    }

    fun loadDriverReleases(source: DriverSource = driverSource) {
        driverSource = source
        viewModelScope.launch(Dispatchers.IO) {
            driverReleasesLoading = true
            driverReleasesError = false
            driverReleases = try {
                DriverRepository.fetchReleases(source)
            } catch (e: DriverException) {
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
            try {
                val d = DriverRepository.download(asset) { done, total ->
                    busy = Busy(R.string.busy_downloading_driver, asset.name, if (total > 0) done.toFloat() / total else null)
                }
                say(R.string.msg_driver_installed, d.name)
            } catch (e: DriverException) {
                say(driverError(e), e.message ?: "-")
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
            try {
                val d = DriverRepository.importUri(ctx, uri, name)
                say(R.string.msg_driver_installed, d.name)
            } catch (e: DriverException) {
                say(driverError(e), e.message ?: "-")
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
            refreshDrivers()
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
            try {
                val info = UpdateChecker.check()
                updateInfo = info
                updateState = if (info == null) UpdateState.UP_TO_DATE else UpdateState.AVAILABLE
                if (info != null && (manual || prefs.skippedUpdate != info.version)) showUpdateDialog = true
                if (info == null && manual) say(R.string.msg_up_to_date)
            } catch (e: Exception) {
                updateState = UpdateState.FAILED
                if (manual) say(R.string.err_update_check)
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
            try {
                val apk = UpdateChecker.downloadApk(info) { done, total ->
                    busy = Busy(R.string.busy_downloading_update, info.version, if (total > 0) done.toFloat() / total else null)
                }
                withContext(Dispatchers.Main) { UpdateChecker.install(context, apk) }
            } catch (e: Exception) {
                say(R.string.err_network, e.message ?: "-")
            } finally {
                busy = null
            }
        }
    }

    // Fica no fim da classe: as propriedades acima ja tem de estar inicializadas.
    init {
        viewModelScope.launch(Dispatchers.IO) {
            val c = GameConfig.load()
            withContext(Dispatchers.Main) { config = c }
            // Garante que o toml existe com as opcoes do launcher desde o primeiro arranque.
            GameConfig.save(c)
        }
        refreshInstall(false)
        refreshMods()
        refreshDrivers()
    }

    companion object {
        const val PLAY_ACTION = "com.dfdx047.dragonfury.PLAY"
    }
}
