package com.dfdx047.dragonfury.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dfdx047.dragonfury.R
import com.dfdx047.dragonfury.data.AppLanguage
import com.dfdx047.dragonfury.data.DarkMode
import com.dfdx047.dragonfury.data.GameOrientation
import com.dfdx047.dragonfury.data.ThemeMode
import com.dfdx047.dragonfury.ui.LauncherViewModel
import com.dfdx047.dragonfury.ui.components.Level
import com.dfdx047.dragonfury.ui.components.SectionCard
import com.dfdx047.dragonfury.ui.components.SettingDropdown
import com.dfdx047.dragonfury.ui.components.SettingSegmented
import com.dfdx047.dragonfury.ui.components.SettingSlider
import com.dfdx047.dragonfury.ui.components.SettingSwitch
import com.dfdx047.dragonfury.ui.components.StatusLine
import kotlin.math.roundToInt

/**
 * Aba Configuracoes: so o geral (audio, idioma e modos do jogo, orientacao, tema e idioma
 * da app, atualizacoes). Graficos em PerformanceScreen, controles em ControlsScreen, drivers
 * em DriversScreen e logs/diagnostico em DebugScreen.
 */
@Composable
fun SettingsScreen(vm: LauncherViewModel) {
    val c = vm.config

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StatusLine(stringResource(R.string.settings_saved_note), Level.INFO)

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
            SettingSwitch(
                stringResource(R.string.settings_online),
                stringResource(R.string.settings_online_sub),
                checked = c.online,
            ) { on -> vm.updateConfig { it.copy(online = on) } }
            SettingSwitch(
                stringResource(R.string.settings_form_select),
                stringResource(R.string.settings_form_select_sub),
                checked = c.formSelect,
            ) { on -> vm.updateConfig { it.copy(formSelect = on) } }
            SettingSegmented(
                title = stringResource(R.string.orientation),
                options = listOf(
                    GameOrientation.LANDSCAPE to stringResource(R.string.orientation_landscape),
                    GameOrientation.REVERSE to stringResource(R.string.orientation_reverse),
                    GameOrientation.AUTO to stringResource(R.string.orientation_auto),
                ),
                selected = vm.gameOrientation,
                onSelect = vm::setOrientation,
            )
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
                    AppLanguage.SPANISH to "Español",
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
}
