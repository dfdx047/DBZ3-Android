package com.dfdx047.dragonfury.ui.screens

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dfdx047.dragonfury.R
import com.dfdx047.dragonfury.controls.ControlLayoutStore
import com.dfdx047.dragonfury.controls.ControlsEditorActivity
import com.dfdx047.dragonfury.data.AppLanguage
import com.dfdx047.dragonfury.data.ButtonBadge
import com.dfdx047.dragonfury.data.ControlStyle
import com.dfdx047.dragonfury.data.DarkMode
import com.dfdx047.dragonfury.data.Directional
import com.dfdx047.dragonfury.data.ThemeMode
import com.dfdx047.dragonfury.data.TouchMode
import com.dfdx047.dragonfury.ui.LauncherViewModel
import com.dfdx047.dragonfury.ui.components.Level
import com.dfdx047.dragonfury.ui.components.SectionCard
import com.dfdx047.dragonfury.ui.components.SettingDropdown
import com.dfdx047.dragonfury.ui.components.SettingSegmented
import com.dfdx047.dragonfury.ui.components.SettingSlider
import com.dfdx047.dragonfury.ui.components.SettingSwitch
import com.dfdx047.dragonfury.ui.components.StatusLine
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: LauncherViewModel) {
    val c = vm.config
    val context = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StatusLine(stringResource(R.string.settings_saved_note), Level.INFO)

        // ------------------------------------------------------------ desempenho
        SectionCard(stringResource(R.string.settings_performance), Icons.Filled.Speed) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_api), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text("Vulkan", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Text(stringResource(R.string.settings_api_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            SettingSlider(
                title = stringResource(R.string.settings_resolution),
                subtitle = stringResource(R.string.settings_resolution_sub),
                value = c.resolutionScale.toFloat(),
                range = 1f..4f,
                steps = 2,
                label = { v -> val s = v.roundToInt(); "${s}x (${720 * s}p)" },
            ) { v -> vm.updateConfig { it.copy(resolutionScale = v.roundToInt()) } }
            if (c.resolutionScale >= 3) StatusLine(stringResource(R.string.settings_resolution_warn), Level.WARN)
            SettingSegmented(
                title = stringResource(R.string.settings_frame_cap),
                options = listOf(30 to "30 FPS", 60 to "60 FPS"),
                selected = c.frameCap,
                onSelect = { v -> vm.updateConfig { it.copy(frameCap = v) } },
            )
            SettingSwitch(stringResource(R.string.settings_show_fps), checked = c.showFps) { on -> vm.updateConfig { it.copy(showFps = on) } }
        }

        // ---------------------------------------------------------------- imagem
        SectionCard(stringResource(R.string.settings_image), Icons.Filled.Wallpaper) {
            SettingSegmented(
                title = stringResource(R.string.settings_filter),
                subtitle = stringResource(R.string.settings_filter_sub),
                options = listOf("bilinear" to "Bilinear", "cas" to "AMD CAS", "fsr" to "AMD FSR 1"),
                selected = c.presentEffect,
                onSelect = { v -> vm.updateConfig { it.copy(presentEffect = v) } },
            )
            if (c.presentEffect == "fsr") {
                SettingDropdown(
                    title = stringResource(R.string.settings_fsr_mode),
                    subtitle = stringResource(R.string.settings_fsr_mode_sub),
                    options = listOf(
                        "native" to stringResource(R.string.fsr_native),
                        "quality" to stringResource(R.string.fsr_quality),
                        "balanced" to stringResource(R.string.fsr_balanced),
                        "performance" to stringResource(R.string.fsr_performance),
                        "ultra_performance" to stringResource(R.string.fsr_ultra),
                    ),
                    selected = c.fsrRender,
                    onSelect = { v -> vm.updateConfig { it.copy(fsrRender = v) } },
                )
                if (c.fsrRender != "native" && c.resolutionScale < 2) {
                    StatusLine(stringResource(R.string.settings_fsr_mode_no_effect), Level.INFO)
                }
                SettingSlider(
                    title = stringResource(R.string.settings_fsr_sharpness),
                    value = (2.0 - c.fsrSharpness).toFloat(),
                    range = 0f..2f,
                    label = { v -> "${(v / 2f * 100).roundToInt()}%" },
                ) { v -> vm.updateConfig { it.copy(fsrSharpness = (2.0 - v).coerceIn(0.0, 2.0)) } }
            }
            if (c.presentEffect == "cas") {
                SettingSlider(
                    title = stringResource(R.string.settings_cas_sharpness),
                    value = c.casSharpness.toFloat(),
                    range = 0f..1f,
                    label = { v -> "${(v * 100).roundToInt()}%" },
                ) { v -> vm.updateConfig { it.copy(casSharpness = v.toDouble()) } }
            }
            StatusLine(stringResource(R.string.settings_fsr3_note), Level.INFO)
            HorizontalDivider()
            SettingDropdown(
                title = stringResource(R.string.settings_aa),
                options = listOf(
                    "none" to stringResource(R.string.off),
                    "fxaa" to "FXAA",
                    "fxaa_extreme" to "FXAA Extreme",
                ),
                selected = c.fxaa,
                onSelect = { v -> vm.updateConfig { it.copy(fxaa = v) } },
            )
            SettingSwitch(
                stringResource(R.string.settings_msaa),
                stringResource(R.string.settings_msaa_sub),
                checked = c.msaa2x,
            ) { on -> vm.updateConfig { it.copy(msaa2x = on) } }
            SettingDropdown(
                title = stringResource(R.string.settings_aniso),
                options = listOf(0 to stringResource(R.string.off), 1 to "1x", 2 to "2x", 3 to "4x", 4 to "8x", 5 to "16x"),
                selected = c.anisotropic,
                onSelect = { v -> vm.updateConfig { it.copy(anisotropic = v) } },
            )
            SettingSlider(
                title = stringResource(R.string.settings_rim_light),
                subtitle = stringResource(R.string.settings_rim_light_sub),
                value = c.rimLight.toFloat(),
                range = 0f..1f,
                label = { v -> "${(v * 100).roundToInt()}%" },
            ) { v -> vm.updateConfig { it.copy(rimLight = v.toDouble()) } }
            SettingSwitch(stringResource(R.string.settings_dither), stringResource(R.string.settings_dither_sub), checked = c.dither) { on ->
                vm.updateConfig { it.copy(dither = on) }
            }
        }

        // ------------------------------------------------------------- proporcao
        SectionCard(stringResource(R.string.settings_aspect), Icons.Filled.PhotoSizeSelectLarge) {
            SettingSegmented(
                title = stringResource(R.string.settings_aspect_mode),
                options = listOf(true to stringResource(R.string.aspect_original), false to stringResource(R.string.aspect_stretch)),
                selected = c.letterbox,
                onSelect = { v -> vm.updateConfig { it.copy(letterbox = v) } },
            )
            StatusLine(stringResource(R.string.settings_widescreen_note), Level.INFO)
        }

        // ----------------------------------------------------------------- audio
        SectionCard(stringResource(R.string.settings_audio), Icons.Filled.VolumeUp) {
            SettingSlider(
                title = stringResource(R.string.settings_volume),
                value = c.masterVolume.toFloat(),
                range = 0f..1f,
                label = { v -> "${(v * 100).roundToInt()}%" },
            ) { v -> vm.updateConfig { it.copy(masterVolume = v.toDouble()) } }
            SettingSwitch(stringResource(R.string.settings_mute), checked = c.mute) { on -> vm.updateConfig { it.copy(mute = on) } }
        }

        // ------------------------------------------------------------------ jogo
        SectionCard(stringResource(R.string.settings_game), Icons.Filled.SportsEsports) {
            SettingDropdown(
                title = stringResource(R.string.settings_game_language),
                subtitle = stringResource(R.string.settings_game_language_sub),
                options = listOf(1 to "English", 2 to "日本語", 3 to "Deutsch", 4 to "Français", 5 to "Español", 6 to "Italiano"),
                selected = c.gameLanguage,
                onSelect = { v -> vm.updateConfig { it.copy(gameLanguage = v) } },
            )
            SettingSwitch(
                stringResource(R.string.settings_tag_team),
                stringResource(R.string.settings_tag_team_sub),
                checked = c.tagTeam,
            ) { on -> vm.updateConfig { it.copy(tagTeam = on) } }
        }

        // -------------------------------------------------------------- controles
        SectionCard(stringResource(R.string.settings_controls), Icons.Filled.Gamepad) {
            SettingSegmented(
                title = stringResource(R.string.settings_touch),
                subtitle = stringResource(R.string.settings_touch_mode_sub),
                options = listOf(
                    TouchMode.AUTO to stringResource(R.string.touch_mode_auto),
                    TouchMode.ON to stringResource(R.string.touch_mode_on),
                    TouchMode.OFF to stringResource(R.string.touch_mode_off),
                ),
                selected = vm.touchMode,
                onSelect = vm::setTouchMode,
            )
            SettingSegmented(
                title = stringResource(R.string.ingame_directional),
                subtitle = stringResource(R.string.settings_directional_sub),
                options = listOf(
                    Directional.DPAD to stringResource(R.string.directional_dpad),
                    Directional.STICK to stringResource(R.string.directional_stick),
                ),
                selected = vm.directional,
                onSelect = vm::setDirectional,
            )
            SettingSegmented(
                title = stringResource(R.string.settings_touch_style),
                options = listOf(
                    ControlStyle.DRAGON to "Dragon Fury",
                    ControlStyle.XBOX to "Xbox",
                    ControlStyle.PLAYSTATION to "PlayStation",
                ),
                selected = vm.controlStyle,
                onSelect = vm::setControlStyle,
            )
            if (vm.controlStyle == ControlStyle.DRAGON) {
                SettingSegmented(
                    title = stringResource(R.string.ingame_badge),
                    options = listOf(
                        ButtonBadge.XBOX to "Xbox",
                        ButtonBadge.PLAYSTATION to "PlayStation",
                        ButtonBadge.NONE to stringResource(R.string.badge_none),
                    ),
                    selected = vm.buttonBadge,
                    onSelect = vm::setButtonBadge,
                )
            }
            SettingSlider(
                title = stringResource(R.string.settings_touch_opacity),
                value = vm.touchOpacity,
                range = 0.1f..1f,
                label = { v -> "${(v * 100).roundToInt()}%" },
            ) { v -> vm.setTouchOpacity(v) }
            SettingSwitch(stringResource(R.string.settings_touch_haptics), checked = vm.touchHaptics, onChange = vm::setTouchHaptics)
            SettingSwitch(
                stringResource(R.string.settings_pause_menus),
                stringResource(R.string.settings_pause_menus_sub),
                checked = vm.pauseOnMenus,
                onChange = vm::setPauseOnMenus,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { context.startActivity(Intent(context, ControlsEditorActivity::class.java)) }) {
                    Icon(Icons.Filled.Edit, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_touch_edit))
                }
                OutlinedButton(onClick = { confirmReset = true }) {
                    Icon(Icons.Filled.RestartAlt, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.reset))
                }
            }
            StatusLine(stringResource(R.string.settings_touch_helpers_note), Level.INFO)
            HorizontalDivider()
            Text(stringResource(R.string.settings_physical_pad), style = MaterialTheme.typography.titleSmall)
            SettingSlider(
                title = stringResource(R.string.settings_deadzone),
                value = c.deadzone.toFloat(),
                range = 0f..0.5f,
                label = { v -> "${(v * 100).roundToInt()}%" },
            ) { v -> vm.updateConfig { it.copy(deadzone = v.toDouble()) } }
            SettingSwitch(stringResource(R.string.settings_rumble), checked = c.rumble) { on -> vm.updateConfig { it.copy(rumble = on) } }
        }

        // -------------------------------------------------------------- aplicacao
        SectionCard(stringResource(R.string.settings_app), Icons.Filled.Palette) {
            SettingSegmented(
                title = stringResource(R.string.settings_theme),
                subtitle = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) stringResource(R.string.settings_theme_material_unavailable) else null,
                options = listOf(
                    ThemeMode.DRAGON to "Dragon Fury",
                    ThemeMode.MATERIAL_YOU to "Material You",
                ),
                selected = vm.themeMode,
                onSelect = vm::setTheme,
            )
            SettingSegmented(
                title = stringResource(R.string.settings_dark),
                options = listOf(
                    DarkMode.SYSTEM to stringResource(R.string.dark_system),
                    DarkMode.LIGHT to stringResource(R.string.dark_light),
                    DarkMode.DARK to stringResource(R.string.dark_dark),
                ),
                selected = vm.darkMode,
                onSelect = vm::setDark,
            )
            SettingSegmented(
                title = stringResource(R.string.settings_language),
                subtitle = stringResource(R.string.settings_language_sub),
                options = listOf(
                    AppLanguage.SYSTEM to stringResource(R.string.lang_system),
                    AppLanguage.PORTUGUESE to "Português",
                    AppLanguage.ENGLISH to "English",
                ),
                selected = vm.appLanguage,
                onSelect = vm::setLanguage,
            )
            SettingSwitch(
                stringResource(R.string.settings_check_updates),
                checked = vm.checkUpdatesOnStart,
                onChange = vm::setCheckUpdatesOnStart,
            )
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.settings_touch_reset_title)) },
            text = { Text(stringResource(R.string.settings_touch_reset_text)) },
            confirmButton = {
                TextButton(onClick = {
                    ControlLayoutStore.reset()
                    confirmReset = false
                }) { Text(stringResource(R.string.reset)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
