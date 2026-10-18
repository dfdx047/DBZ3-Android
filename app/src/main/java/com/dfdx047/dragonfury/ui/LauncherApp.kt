@file:OptIn(ExperimentalMaterial3Api::class)

package com.dfdx047.dragonfury.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dfdx047.dragonfury.R
import com.dfdx047.dragonfury.ui.screens.AboutScreen
import com.dfdx047.dragonfury.ui.screens.CheatsScreen
import com.dfdx047.dragonfury.ui.screens.DebugScreen
import com.dfdx047.dragonfury.ui.screens.ControlsScreen
import com.dfdx047.dragonfury.ui.screens.DriversScreen
import com.dfdx047.dragonfury.ui.screens.HomeScreen
import com.dfdx047.dragonfury.ui.screens.ModsScreen
import com.dfdx047.dragonfury.ui.screens.PerformanceScreen
import com.dfdx047.dragonfury.ui.screens.SettingsScreen
import com.dfdx047.dragonfury.ui.screens.TexturesScreen
import com.dfdx047.dragonfury.DragonFuryApp
import com.dfdx047.dragonfury.ui.components.openUrl
import com.dfdx047.dragonfury.ui.components.dragonBackdrop
import com.dfdx047.dragonfury.ui.theme.DfColors
import com.dfdx047.dragonfury.ui.theme.LogoStyle

enum class LauncherTab(@StringRes val title: Int, val icon: ImageVector) {
    HOME(R.string.tab_home, Icons.Filled.Home),
    PERFORMANCE(R.string.tab_performance, Icons.Filled.Speed),
    CONTROLS(R.string.tab_controls, Icons.Filled.Gamepad),
    MODS(R.string.tab_mods, Icons.Filled.Extension),
    TEXTURES(R.string.tab_textures, Icons.Filled.Texture),
    CHEATS(R.string.tab_cheats, Icons.Filled.AutoFixHigh),
    DRIVERS(R.string.tab_drivers, Icons.Filled.Memory),
    SETTINGS(R.string.tab_settings, Icons.Filled.Settings),
    DEBUG(R.string.tab_debug, Icons.Filled.BugReport),
    ABOUT(R.string.tab_about, Icons.Filled.Info),
}

@Composable
fun LauncherApp(vm: LauncherViewModel) {
    var tab by rememberSaveable { mutableStateOfTab() }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(vm.message) {
        val m = vm.message ?: return@LaunchedEffect
        snackbar.showSnackbar(context.getString(m.res, *m.args.toTypedArray()))
        vm.message = null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .dragonBackdrop(
                    base = MaterialTheme.colorScheme.background,
                    glow = MaterialTheme.colorScheme.primary,
                    lines = MaterialTheme.colorScheme.primary,
                )
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Header()
            BusyBar(vm.busy)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                TabContent(tab, vm) { tab = it }
            }
            BottomTabs(tab) { tab = it }
        }
    }

    if (vm.showGpuNotice) GpuNotice(vm, openTab = { tab = it })
    if (vm.showUpdateDialog) UpdateDialog(vm)
    vm.error?.let { ErrorDialog(vm, it) }
}

private fun mutableStateOfTab() = androidx.compose.runtime.mutableStateOf(LauncherTab.HOME)

/** Cabecalho fino: so o nome, em dourado inclinado como o titulo do jogo. */
@Composable
private fun Header() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("DRAGON FURY", style = LogoStyle, color = DfColors.Gold)
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.header_subtitle),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Barra das abas em baixo (mais facil de alcancar com o polegar): so texto, a aba atual com um
 * traco laranja por cima. Rola na horizontal quando nao cabem todas.
 */
@Composable
private fun BottomTabs(selected: LauncherTab, onSelect: (LauncherTab) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .height(48.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LauncherTab.entries.forEach { t ->
                val on = t == selected
                Box(
                    Modifier
                        .fillMaxHeight()
                        .clickable { onSelect(t) }
                        .drawBehind {
                            if (on) {
                                drawRect(
                                    accent,
                                    size = androidx.compose.ui.geometry.Size(size.width, 3.dp.toPx()),
                                )
                            }
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(t.title),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Janela de erro: o titulo, o que aconteceu (texto traduzido), os detalhes tecnicos e o pedido
 * para salvar o log e mandar no Discord.
 */
@Composable
private fun ErrorDialog(vm: LauncherViewModel, report: ErrorReport) {
    val context = LocalContext.current
    var showDetail by rememberSaveable(report.id) { androidx.compose.runtime.mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { vm.dismissError() },
        title = { Text(stringResource(report.title)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(report.message, *report.args.toTypedArray()), style = MaterialTheme.typography.bodyMedium)
                if (report.detail.isNotBlank()) {
                    TextButton(onClick = { showDetail = !showDetail }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text(stringResource(R.string.error_details))
                    }
                    if (showDetail) {
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Text(
                                report.detail,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.error_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.exportLogs(context) }) { Text(stringResource(R.string.error_save_log)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { openUrl(context, DragonFuryApp.DISCORD_URL) }) {
                    Text(stringResource(R.string.error_open_discord))
                }
                TextButton(onClick = { vm.dismissError() }) { Text(stringResource(R.string.close)) }
            }
        },
    )
}

@Composable
private fun BusyBar(busy: Busy?) {
    if (busy == null) return
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            stringResource(busy.label) + if (busy.detail.isNotEmpty()) " · ${busy.detail}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.heightIn(min = 4.dp))
        if (busy.progress != null) {
            LinearProgressIndicator(progress = { busy.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TabContent(tab: LauncherTab, vm: LauncherViewModel, goTo: (LauncherTab) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        when (tab) {
            LauncherTab.HOME -> HomeScreen(vm, goTo)
            LauncherTab.PERFORMANCE -> PerformanceScreen(vm)
            LauncherTab.CONTROLS -> ControlsScreen(vm)
            LauncherTab.MODS -> ModsScreen(vm)
            LauncherTab.TEXTURES -> TexturesScreen(vm)
            LauncherTab.CHEATS -> CheatsScreen(vm)
            LauncherTab.DRIVERS -> DriversScreen(vm)
            LauncherTab.SETTINGS -> SettingsScreen(vm)
            LauncherTab.DEBUG -> DebugScreen(vm)
            LauncherTab.ABOUT -> AboutScreen(vm)
        }
    }
}

/**
 * Aviso de primeiro arranque (e pelo Sobre): em que GPUs/drivers o jogo foi otimizado
 * e o que fazer nas Mali. Fechar marca-o como visto.
 */
@Composable
private fun GpuNotice(vm: LauncherViewModel, openTab: (LauncherTab) -> Unit) {
    AlertDialog(
        onDismissRequest = { vm.dismissGpuNotice() },
        title = { Text(stringResource(R.string.gpu_notice_title)) },
        text = {
            Text(
                stringResource(R.string.gpu_notice_text),
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = { vm.dismissGpuNotice() }) { Text(stringResource(R.string.gpu_notice_ok)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    vm.dismissGpuNotice()
                    openTab(LauncherTab.DRIVERS)
                }) { Text(stringResource(R.string.tab_drivers)) }
                TextButton(onClick = {
                    vm.dismissGpuNotice()
                    openTab(LauncherTab.PERFORMANCE)
                }) { Text(stringResource(R.string.tab_performance)) }
            }
        },
    )
}

@Composable
private fun UpdateDialog(vm: LauncherViewModel) {
    val info = vm.updateInfo ?: return
    val context = LocalContext.current
    val language = context.resources.configuration.locales[0].language
    AlertDialog(
        onDismissRequest = { vm.showUpdateDialog = false },
        title = { Text(stringResource(R.string.update_title, info.version)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.update_current, com.dfdx047.dragonfury.BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(stringResource(R.string.update_changelog), style = MaterialTheme.typography.titleSmall)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        info.changelog(language).ifBlank { stringResource(R.string.update_no_changelog) },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.downloadUpdate(context) }) {
                Text(stringResource(if (info.apkUrl != null) R.string.update_install else R.string.update_open_page))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { vm.skipUpdate() }) { Text(stringResource(R.string.update_skip)) }
                TextButton(onClick = { vm.showUpdateDialog = false }) { Text(stringResource(R.string.later)) }
            }
        },
    )
}
