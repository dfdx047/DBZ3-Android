package com.dfdx047.dragonfury.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dfdx047.dragonfury.R
import com.dfdx047.dragonfury.ui.LauncherViewModel
import com.dfdx047.dragonfury.ui.components.Level
import com.dfdx047.dragonfury.ui.components.SectionCard
import com.dfdx047.dragonfury.ui.components.SettingSwitch
import com.dfdx047.dragonfury.ui.components.StatusLine

/**
 * Aba Depuracao: o que serve para testes e para mandar logs, nao para jogar. Logs e
 * diagnostico de desempenho, otimizacoes internas do renderizador (ligadas por omissao),
 * correcoes do Turnip para testes A/B e o cvars.txt.
 */
@Composable
fun DebugScreen(vm: LauncherViewModel) {
    val c = vm.config
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StatusLine(stringResource(R.string.debug_intro), Level.WARN)

        // ------------------------------------------------------------------- logs
        SectionCard(stringResource(R.string.debug_logs_title), Icons.Filled.Description, subtitle = stringResource(R.string.debug_logs_sub)) {
            Button(onClick = { vm.exportLogs(context) }, enabled = vm.busy == null, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.IosShare, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.export_logs))
            }
            SettingSwitch(
                stringResource(R.string.settings_verbose_log),
                stringResource(R.string.settings_verbose_log_sub),
                checked = c.verboseLog,
            ) { on -> vm.updateConfig { it.copy(verboseLog = on) } }
            SettingSwitch(
                stringResource(R.string.settings_perf_log),
                stringResource(R.string.settings_perf_log_sub),
                checked = c.perfLogging,
            ) { on -> vm.updateConfig { it.copy(perfLogging = on) } }
        }

        // ------------------------------------------------ otimizacoes da GPU (internas)
        SectionCard(
            stringResource(R.string.perf_gpu_title),
            Icons.Filled.Memory,
            subtitle = stringResource(R.string.perf_gpu_sub),
        ) {
            SettingSwitch(
                stringResource(R.string.settings_rt_direct),
                stringResource(R.string.settings_rt_direct_sub),
                checked = c.rtDirect,
            ) { on -> vm.updateConfig { it.copy(rtDirect = on) } }
            SettingSwitch(
                stringResource(R.string.settings_spec_shaders),
                stringResource(R.string.settings_spec_shaders_sub),
                checked = c.specShaders,
            ) { on -> vm.updateConfig { it.copy(specShaders = on) } }
            // O valor 2 (R/B invertido) so serviu para confirmar a ordem dos canais:
            // fica so na cvar, para testes.
            SettingSwitch(
                stringResource(R.string.settings_direct_resolve),
                stringResource(R.string.settings_direct_resolve_sub),
                checked = c.directResolve != 0,
            ) { on -> vm.updateConfig { it.copy(directResolve = if (on) 1 else 0) } }
            if (c.directResolve != 0) {
                SettingSwitch(
                    stringResource(R.string.settings_direct_resolve_fast),
                    stringResource(R.string.settings_direct_resolve_fast_sub),
                    checked = c.directResolveFast,
                ) { on -> vm.updateConfig { it.copy(directResolveFast = on) } }
            }
        }

        // ------------------------------------------------------------ Turnip (A/B)
        // Cada opcao liga um conjunto de correcoes no arranque do jogo
        // (GameActivity.applyTurnipCompat).
        SectionCard(stringResource(R.string.turnip_compat_title), Icons.Filled.Build, subtitle = stringResource(R.string.turnip_compat_sub)) {
            listOf(
                1 to R.string.turnip_compat_single_buffer,
                8 to R.string.turnip_compat_bc,
                2 to R.string.turnip_compat_sysmem,
                4 to R.string.turnip_compat_nolrz,
                16 to R.string.turnip_compat_paths,
                32 to R.string.turnip_compat_depth,
                64 to R.string.turnip_compat_sync,
            ).forEach { (bit, label) ->
                SettingSwitch(
                    stringResource(label),
                    checked = (vm.turnipCompatFlags and bit) != 0,
                ) { on -> vm.setTurnipCompatFlag(bit, on) }
            }
        }

        // -------------------------------------------------------------- cvars.txt
        SectionCard(stringResource(R.string.debug_cvars_title), Icons.Filled.Tune) {
            Text(stringResource(R.string.debug_cvars_text), style = MaterialTheme.typography.bodySmall)
        }
    }
}
