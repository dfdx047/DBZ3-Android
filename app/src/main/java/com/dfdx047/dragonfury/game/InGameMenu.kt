@file:OptIn(ExperimentalMaterial3Api::class)

package com.dfdx047.dragonfury.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.dfdx047.dragonfury.R
import com.dfdx047.dragonfury.controls.ControlsEditor
import com.dfdx047.dragonfury.controls.LayoutKind
import com.dfdx047.dragonfury.data.AppLog
import com.dfdx047.dragonfury.data.ButtonBadge
import com.dfdx047.dragonfury.data.ControlStyle
import com.dfdx047.dragonfury.data.Directional
import com.dfdx047.dragonfury.data.GameConfig
import com.dfdx047.dragonfury.data.LauncherPrefs
import com.dfdx047.dragonfury.data.LogExporter
import com.dfdx047.dragonfury.data.TouchMode
import com.dfdx047.dragonfury.ui.theme.DfColors
import com.dfdx047.dragonfury.ui.theme.LogoStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * O GameActivity e um SDLActivity (Activity simples, sem Lifecycle do AndroidX).
 * Para pôr um ComposeView por cima do jogo basta um "dono" de lifecycle e de
 * saved state sempre ativo.
 */
class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    init {
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}

enum class InGameScreen { CLOSED, MENU, EDITOR, PORT }

/** Ações do menu, implementadas pelo GameOverlay. */
interface InGameActions {
    fun resume()
    fun openEditor()
    fun closeEditor()
    fun openPortMenu()
    fun openPortOptions()
    fun isInBattle(): Boolean
    /** Layout dos controles por toque em uso agora (luta, menus ou mapa do Dragon Universe). */
    fun currentLayoutKind(): LayoutKind = if (isInBattle()) LayoutKind.GAMEPLAY else LayoutKind.MENU
    fun applyAppearance()
    fun exitToLauncher()
    /** Pede ao runtime uma captura de frames (logs/capture, ficheiros .dfc) para o renderizador nativo. */
    fun captureFrames()
    /** Autoteste do render (RenderSelfTest): so numa luta. Devolve false se nao deu para comecar. */
    fun startSelfTest(): Boolean = false
}

/**
 * Menu do jogo (Voltar, Select + Start ou os botoes na tela), com o tema do launcher. Substitui o
 * menu rapido do PC (ImGui). Abas (LB/RB ou toque): Jogo, Video, Efeitos, Audio, Controles e
 * Depuracao; cada linha muda com as setas do comando ou com toque. So entram as opcoes que mudam
 * ao vivo; o resto (resolucao, FPS, MSAA, idioma) fica no launcher.
 */
@Composable
fun InGameOverlay(screen: InGameScreen, prefs: LauncherPrefs, actions: InGameActions, nav: MenuNav) {
    when (screen) {
        InGameScreen.CLOSED -> Unit
        InGameScreen.EDITOR -> ControlsEditor(
            prefs,
            overGame = true,
            initialKind = actions.currentLayoutKind(),
        ) { actions.closeEditor() }
        InGameScreen.MENU, InGameScreen.PORT -> Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center,
        ) {
            GameMenuPanel(prefs, actions, nav)
        }
    }
}

private enum class MenuTab(val title: Int, val icon: ImageVector) {
    GAME(R.string.ingame_tab_game, Icons.Filled.SportsEsports),
    VIDEO(R.string.ingame_tab_video, Icons.Filled.Tv),
    EFFECTS(R.string.ingame_tab_effects, Icons.Filled.AutoAwesome),
    AUDIO(R.string.ingame_tab_audio, Icons.Filled.VolumeUp),
    CONTROLS(R.string.ingame_tab_controls, Icons.Filled.Gamepad),
    DEBUG(R.string.ingame_tab_debug, Icons.Filled.BugReport),
}

@Composable
private fun GameMenuPanel(prefs: LauncherPrefs, actions: InGameActions, nav: MenuNav) {
    val context = LocalContext.current
    var c by remember { mutableStateOf(GameConfig.load()) }
    fun set(next: GameConfig, cvars: Map<String, String>) {
        c = next
        NativeGameState.setCvars(cvars)
        AppLog.i(R.string.log_menu, cvars.entries.joinToString { "${it.key}=${it.value}" })
    }
    fun pct(x: Float) = "${(x * 100).roundToInt()}%"

    var touchMode by remember { mutableStateOf(prefs.touchMode) }
    var directional by remember { mutableStateOf(prefs.directional) }
    var style by remember { mutableStateOf(prefs.controlStyle) }
    var badge by remember { mutableStateOf(prefs.buttonBadge) }
    var opacity by remember { mutableFloatStateOf(prefs.touchOpacity) }
    var haptics by remember { mutableStateOf(prefs.touchHaptics) }
    var confirmExit by remember { mutableStateOf(false) }
    var selfTestRefused by remember { mutableStateOf(false) }
    var logStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val tabs = MenuTab.entries
    nav.tabCount = tabs.size
    val tab = tabs[nav.tab.intValue.coerceIn(0, tabs.size - 1)]
    val off = stringResource(R.string.off)
    val on = stringResource(R.string.on)

    // ------------------------------------------------------------ linhas de cada aba
    val items: List<MItem> = when (tab) {
        MenuTab.GAME -> listOf(
            MItem.Action(stringResource(R.string.ingame_resume), accent = true) { actions.resume() },
            MItem.Action(stringResource(R.string.ingame_edit_touch)) { actions.openEditor() },
            MItem.Action(
                stringResource(if (confirmExit) R.string.ingame_exit_confirm else R.string.ingame_exit),
            ) { if (confirmExit) actions.exitToLauncher() else confirmExit = true },
            MItem.Note(stringResource(R.string.ingame_restart_note)),
        )
        MenuTab.VIDEO -> buildList {
            val effects = listOf("bilinear", "cas", "fsr")
            add(
                MItem.Choice(
                    stringResource(R.string.settings_filter),
                    listOf("Bilinear", "AMD CAS", "AMD FSR 1"),
                    effects.indexOf(c.presentEffect).coerceAtLeast(0),
                ) { set(c.copy(presentEffect = effects[it]), mapOf("dbz3_present_effect" to effects[it])) },
            )
            if (c.presentEffect == "fsr") {
                add(
                    MItem.Slider(
                        stringResource(R.string.settings_fsr_sharpness),
                        (2.0 - c.fsrSharpness).toFloat(), 0f..2f, 0.1f, { "${(it / 2f * 100).roundToInt()}%" },
                    ) {
                        val red = (2.0 - it).coerceIn(0.0, 2.0)
                        set(c.copy(fsrSharpness = red), mapOf("dbz3_fsr_sharpness" to TomlNum.d(red)))
                    },
                )
            }
            if (c.presentEffect == "cas") {
                add(
                    MItem.Slider(stringResource(R.string.settings_cas_sharpness), c.casSharpness.toFloat(), 0f..1f, 0.05f, ::pct) {
                        set(c.copy(casSharpness = it.toDouble()), mapOf("dbz3_cas_sharpness" to TomlNum.d(it.toDouble())))
                    },
                )
            }
            val aa = listOf("none", "fxaa", "fxaa_extreme")
            add(
                MItem.Choice(stringResource(R.string.settings_aa), listOf(off, "FXAA", "FXAA+"), aa.indexOf(c.fxaa).coerceAtLeast(0)) {
                    set(c.copy(fxaa = aa[it]), mapOf("dbz3_fxaa" to aa[it]))
                },
            )
            add(
                MItem.Choice(
                    stringResource(R.string.settings_aspect_mode),
                    listOf(stringResource(R.string.aspect_original), stringResource(R.string.aspect_stretch)),
                    if (c.letterbox) 0 else 1,
                ) { set(c.copy(letterbox = it == 0), mapOf("present_letterbox" to (it == 0).toString())) },
            )
            add(
                MItem.Slider(stringResource(R.string.settings_rim_light), c.rimLight.toFloat(), 0f..1f, 0.05f, ::pct) {
                    set(c.copy(rimLight = it.toDouble()), mapOf("dbz3_hd_rim_light" to TomlNum.d(it.toDouble())))
                },
            )
            add(MItem.Toggle(stringResource(R.string.settings_dither), c.dither) {
                set(c.copy(dither = it), mapOf("dbz3_present_dither" to it.toString()))
            })
            add(MItem.Toggle(stringResource(R.string.textures_enabled), c.texturePacksOn) {
                set(c.copy(texturePacksOn = it), mapOf("dbz3_texture_packs_on" to it.toString()))
            })
            add(MItem.Toggle(stringResource(R.string.settings_show_fps), c.showFps) {
                set(c.copy(showFps = it), mapOf("dbz3_show_fps" to it.toString()))
            })
        }
        MenuTab.EFFECTS -> listOf(
            MItem.Toggle(stringResource(R.string.fx_bloom), c.fxBloom, enabled = c.rtDirect) {
                set(c.copy(fxBloom = it), mapOf("dbz3_fx_bloom" to it.toString()))
            },
            MItem.Toggle(stringResource(R.string.fx_dof), c.fxDof, enabled = c.rtDirect) {
                set(c.copy(fxDof = it), mapOf("dbz3_fx_dof" to it.toString()))
            },
            MItem.Toggle(stringResource(R.string.fx_fxaa), c.fxFxaa, enabled = c.rtDirect) {
                set(c.copy(fxFxaa = it), mapOf("dbz3_fx_fxaa" to it.toString()))
            },
            MItem.Note(stringResource(if (c.rtDirect) R.string.ingame_effects_note else R.string.fx_needs_rt_direct)),
        )
        MenuTab.AUDIO -> listOf(
            MItem.Slider(stringResource(R.string.settings_volume), c.masterVolume.toFloat(), 0f..1f, 0.05f, ::pct) {
                set(c.copy(masterVolume = it.toDouble()), mapOf("dbz3_master_volume" to TomlNum.d(it.toDouble())))
            },
            MItem.Toggle(stringResource(R.string.settings_mute), c.mute) {
                set(c.copy(mute = it), mapOf("dbz3_mute" to it.toString()))
            },
        )
        MenuTab.CONTROLS -> buildList {
            add(MItem.Toggle(stringResource(R.string.settings_rumble), c.rumble) {
                set(c.copy(rumble = it), mapOf("dbz3_rumble" to it.toString()))
            })
            val modes = listOf(TouchMode.AUTO, TouchMode.ON, TouchMode.OFF)
            add(
                MItem.Choice(
                    stringResource(R.string.ingame_touch_mode),
                    listOf(stringResource(R.string.touch_mode_auto), stringResource(R.string.touch_mode_on), stringResource(R.string.touch_mode_off)),
                    modes.indexOf(touchMode).coerceAtLeast(0),
                ) {
                    touchMode = modes[it]
                    prefs.touchMode = modes[it]
                    actions.applyAppearance()
                },
            )
            val dirs = listOf(Directional.DPAD, Directional.STICK)
            add(
                MItem.Choice(
                    stringResource(R.string.ingame_directional),
                    listOf(stringResource(R.string.directional_dpad), stringResource(R.string.directional_stick)),
                    dirs.indexOf(directional).coerceAtLeast(0),
                ) {
                    directional = dirs[it]
                    prefs.directional = dirs[it]
                    actions.applyAppearance()
                },
            )
            val styles = listOf(ControlStyle.DRAGON, ControlStyle.XBOX, ControlStyle.PLAYSTATION)
            add(
                MItem.Choice(stringResource(R.string.settings_touch_style), listOf("Dragon Fury", "Xbox", "PlayStation"), styles.indexOf(style).coerceAtLeast(0)) {
                    style = styles[it]
                    prefs.controlStyle = styles[it]
                    actions.applyAppearance()
                },
            )
            if (style == ControlStyle.DRAGON) {
                val badges = listOf(ButtonBadge.XBOX, ButtonBadge.PLAYSTATION, ButtonBadge.NONE)
                add(
                    MItem.Choice(
                        stringResource(R.string.ingame_badge),
                        listOf("Xbox", "PlayStation", stringResource(R.string.badge_none)),
                        badges.indexOf(badge).coerceAtLeast(0),
                    ) {
                        badge = badges[it]
                        prefs.buttonBadge = badges[it]
                        actions.applyAppearance()
                    },
                )
            }
            add(
                MItem.Slider(stringResource(R.string.settings_touch_opacity), opacity, 0.1f..1f, 0.05f, ::pct) {
                    opacity = it
                    prefs.touchOpacity = it
                    actions.applyAppearance()
                },
            )
            add(MItem.Toggle(stringResource(R.string.settings_touch_haptics), haptics) {
                haptics = it
                prefs.touchHaptics = it
                actions.applyAppearance()
            })
            add(MItem.Action(stringResource(R.string.ingame_edit_touch)) { actions.openEditor() })
        }
        MenuTab.DEBUG -> buildList {
            add(MItem.Toggle(stringResource(R.string.settings_show_fps), c.showFps) {
                set(c.copy(showFps = it), mapOf("dbz3_show_fps" to it.toString()))
            })
            // Captura de frames para o renderizador nativo: o jogo continua ao fechar o menu
            // e os 2 frames seguintes ficam em logs/capture (entram no "Salvar log").
            add(MItem.Action(stringResource(R.string.ingame_capture)) { actions.captureFrames(); actions.resume() })
            // Autoteste do render: congela a luta, troca cada otimizacao e compara a imagem.
            add(
                MItem.Action(
                    stringResource(R.string.ingame_selftest),
                    enabled = actions.isInBattle(),
                    detail = if (selfTestRefused || !actions.isInBattle()) stringResource(R.string.ingame_selftest_only_fight) else null,
                ) { selfTestRefused = !actions.startSelfTest() },
            )
            val saving = stringResource(R.string.ingame_saving)
            add(
                MItem.Action(stringResource(R.string.error_save_log), detail = logStatus) {
                    logStatus = saving
                    scope.launch {
                        logStatus = try {
                            val name = withContext(Dispatchers.IO) { LogExporter.export(context.applicationContext) }
                            context.getString(R.string.ingame_log_saved, name)
                        } catch (e: Exception) {
                            context.getString(R.string.ingame_log_failed)
                        }
                    }
                },
            )
        }
    }
    SideEffect {
        nav.items = items
        nav.onBack = {
            if (confirmExit) {
                confirmExit = false
                true
            } else {
                false
            }
        }
    }
    // A confirmacao de saida so vale na aba Jogo.
    LaunchedEffect(tab) { if (tab != MenuTab.GAME) confirmExit = false }

    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .widthIn(max = 820.dp)
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer, shape)
            .border(1.dp, DfColors.Orange.copy(alpha = 0.35f), shape),
    ) {
        // -------------------------------------------------------------- cabecalho
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(DfColors.Orange.copy(alpha = 0.22f), Color.Transparent)))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("DRAGON FURY", style = LogoStyle, color = DfColors.Gold)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.ingame_title),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Button(
                onClick = { actions.resume() },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DfColors.Orange, contentColor = Color(0xFF2B1000)),
            ) { Text(stringResource(R.string.ingame_resume), fontWeight = FontWeight.Bold, maxLines = 1) }
        }

        // ------------------------------------------------------------------ abas
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShoulderHint("LB") { nav.switchTab(-1) }
            Row(
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.Center,
            ) {
                tabs.forEachIndexed { i, t ->
                    val sel = t == tab
                    Column(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (sel) Brush.horizontalGradient(listOf(DfColors.Gold, DfColors.Orange))
                                else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.05f))),
                            )
                            .clickable { nav.selectTab(i) }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(t.icon, null, tint = if (sel) Color(0xFF2B1000) else Color.White.copy(alpha = 0.8f), modifier = Modifier.size(22.dp))
                        Text(
                            stringResource(t.title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                            color = if (sel) Color(0xFF2B1000) else Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                        )
                    }
                }
            }
            ShoulderHint("RB") { nav.switchTab(1) }
        }

        // ---------------------------------------------------------------- linhas
        val list = rememberLazyListState()
        val sel = nav.row.intValue
        LaunchedEffect(tab, sel) {
            val visible = list.layoutInfo.visibleItemsInfo
            val first = visible.firstOrNull()?.index ?: 0
            val last = visible.lastOrNull()?.index ?: 0
            if (visible.isEmpty() || sel <= first || sel >= last) list.animateScrollToItem((sel - 1).coerceAtLeast(0))
        }
        LazyColumn(
            state = list,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(items) { i, item ->
                MenuRow(item, selected = i == sel) { nav.row.intValue = i }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        // ---------------------------------------------------------------- rodape
        Text(
            stringResource(R.string.ingame_nav_hint),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.25f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ShoulderHint(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
    }
}

/** Uma linha: titulo a esquerda e o controle a direita. A selecionada fica realcada. */
@Composable
private fun MenuRow(item: MItem, selected: Boolean, onSelect: () -> Unit) {
    if (item is MItem.Note) {
        Text(
            item.label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
        return
    }
    val shape = RoundedCornerShape(12.dp)
    val alpha = if (item.enabled) 1f else 0.45f
    val accentAction = item is MItem.Action && item.accent
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(
                when {
                    accentAction -> DfColors.Orange.copy(alpha = if (selected) 0.95f else 0.75f)
                    selected -> DfColors.Orange.copy(alpha = 0.20f)
                    else -> Color.White.copy(alpha = 0.04f)
                },
                shape,
            )
            .then(if (selected) Modifier.border(2.dp, DfColors.Gold, shape) else Modifier)
            .clickable {
                onSelect()
                when (item) {
                    is MItem.Toggle -> if (item.enabled) item.set(!item.value)
                    is MItem.Action -> if (item.enabled) item.run()
                    else -> Unit
                }
            }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(
                item.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected || accentAction) FontWeight.Bold else FontWeight.Medium,
                color = (if (accentAction) Color(0xFF2B1000) else Color.White).copy(alpha = alpha),
            )
            val detail = (item as? MItem.Action)?.detail
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.65f))
            }
        }
        when (item) {
            is MItem.Toggle -> Switch(
                checked = item.value,
                onCheckedChange = { onSelect(); item.set(it) },
                enabled = item.enabled,
            )
            is MItem.Choice -> ChoiceControl(item, onSelect)
            is MItem.Slider -> SliderControl(item, onSelect)
            is MItem.Action -> if (!accentAction) {
                Icon(Icons.Filled.ChevronRight, null, tint = Color.White.copy(alpha = 0.6f * alpha))
            }
            is MItem.Note -> Unit
        }
    }
}

/** Opcoes como pilulas: toca-se numa, ou muda-se com ← →. */
@Composable
private fun ChoiceControl(item: MItem.Choice, onSelect: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.3f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item.options.forEachIndexed { i, label ->
            val on = i == item.index
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (on) DfColors.Orange else Color.Transparent)
                    .clickable(enabled = item.enabled) {
                        onSelect()
                        if (!on) item.set(i)
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) Color(0xFF2B1000) else Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Slider (aplica ao soltar) com ‹ › para o comando e o valor ao lado. */
@Composable
private fun SliderControl(item: MItem.Slider, onSelect: () -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(item.value) }
    val shown = if (dragging) local else item.value
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = shown,
            onValueChange = {
                onSelect()
                dragging = true
                local = it
            },
            onValueChangeFinished = {
                dragging = false
                item.set(local)
            },
            valueRange = item.range,
            enabled = item.enabled,
            modifier = Modifier.width(200.dp),
        )
        Text(
            item.format(shown),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = DfColors.Gold,
            modifier = Modifier.width(56.dp).padding(start = 8.dp),
        )
    }
}

/** Numeros para cvars (ponto decimal, sem notacao cientifica). */
private object TomlNum {
    fun d(x: Double): String = String.format(java.util.Locale.ROOT, "%.4f", x)
}
