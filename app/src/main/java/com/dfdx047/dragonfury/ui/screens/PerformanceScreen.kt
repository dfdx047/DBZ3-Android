package com.dfdx047.dragonfury.ui.screens

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
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dfdx047.dragonfury.R
import com.dfdx047.dragonfury.data.GameConfig
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
 * Aba Desempenho: tudo o que pesa na GPU (FPS, resolucao interna, efeitos do jogo,
 * filtros de imagem, proporcao). As otimizacoes internas do renderizador estao na Depuracao. Tudo vai para o
 * dbz3_user.toml (GameConfig), lido pelo jogo no arranque.
 */
@Composable
fun PerformanceScreen(vm: LauncherViewModel) {
    val c = vm.config

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StatusLine(stringResource(R.string.settings_saved_note), Level.INFO)
        OutlinedButton(onClick = { vm.openGpuNotice() }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Lightbulb, null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.about_gpu_notice))
        }

        // ------------------------------------------------------------ desempenho
        SectionCard(stringResource(R.string.settings_performance), Icons.Filled.Speed) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_api), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text("Vulkan", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Text(stringResource(R.string.settings_api_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            // frameCap 30 grava dbz3_frame_cap = 30 e dbz3_frameskip = 1 (GameConfig.toToml).
            SettingSwitch(
                stringResource(R.string.perf_frameskip),
                stringResource(R.string.perf_frameskip_sub),
                checked = c.frameCap == 30,
            ) { on -> vm.updateConfig { it.copy(frameCap = if (on) 30 else 60) } }
            SettingSegmented(
                title = stringResource(R.string.settings_resolution),
                subtitle = stringResource(R.string.perf_resolution_sub),
                options = (1..GameConfig.MAX_RESOLUTION_SCALE).map { s -> s to "${s}x (${720 * s}p)" },
                selected = c.resolutionScale,
                onSelect = { v -> vm.updateConfig { it.copy(resolutionScale = v) } },
            )
            if (c.resolutionScale >= 2) StatusLine(stringResource(R.string.perf_resolution_mem), Level.INFO)
            if (c.resolutionScale >= 3) StatusLine(stringResource(R.string.settings_resolution_warn), Level.WARN)
            SettingSwitch(stringResource(R.string.settings_show_fps), checked = c.showFps) { on -> vm.updateConfig { it.copy(showFps = on) } }
        }

        // ------------------------------------------------------- efeitos do jogo
        SectionCard(
            stringResource(R.string.perf_effects_title),
            Icons.Filled.BlurOn,
            subtitle = stringResource(R.string.perf_effects_sub),
        ) {
            SettingSwitch(
                stringResource(R.string.fx_bloom),
                stringResource(R.string.fx_bloom_sub),
                checked = c.fxBloom,
                enabled = c.rtDirect,
            ) { on -> vm.updateConfig { it.copy(fxBloom = on) } }
            SettingSwitch(
                stringResource(R.string.fx_dof),
                stringResource(R.string.fx_dof_sub),
                checked = c.fxDof,
                enabled = c.rtDirect,
            ) { on -> vm.updateConfig { it.copy(fxDof = on) } }
            SettingSwitch(
                stringResource(R.string.fx_fxaa),
                stringResource(R.string.fx_fxaa_sub),
                checked = c.fxFxaa,
                enabled = c.rtDirect,
            ) { on -> vm.updateConfig { it.copy(fxFxaa = on) } }
            if (!c.rtDirect) StatusLine(stringResource(R.string.fx_needs_rt_direct), Level.WARN)
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

    }
}
