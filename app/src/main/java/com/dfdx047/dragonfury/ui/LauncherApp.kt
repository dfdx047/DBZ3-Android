@file:OptIn(ExperimentalMaterial3Api::class)

package com.dfdx047.dragonfury.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import com.dfdx047.dragonfury.ui.components.SlantStripe
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
    ) { inner ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .dragonBackdrop(
                    base = MaterialTheme.colorScheme.background,
                    glow = MaterialTheme.colorScheme.primary,
                    lines = MaterialTheme.colorScheme.primary.copy(alpha = 0.035f),
                )
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val wide = maxWidth > maxHeight && maxWidth >= 600.dp
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    SideRail(tab) { tab = it }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        BusyBar(vm.busy)
                        TabContent(tab, vm) { tab = it }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Header()
                    ScrollableTabRow(
                        selectedTabIndex = tab.ordinal,
                        edgePadding = 12.dp,
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    ) {
                        LauncherTab.entries.forEach { t ->
                            Tab(
                                selected = t == tab,
                                onClick = { tab = t },
                                text = { Text(stringResource(t.title), maxLines = 1) },
                                icon = { Icon(t.icon, null) },
                            )
                        }
                    }
                    BusyBar(vm.busy)
                    TabContent(tab, vm) { tab = it }
                }
            }
        }
    }

    if (vm.showGpuNotice) GpuNotice(vm, openTab = { tab = it })
    if (vm.showUpdateDialog) UpdateDialog(vm)
}

private fun mutableStateOfTab() = androidx.compose.runtime.mutableStateOf(LauncherTab.HOME)

/**
 * Cabecalho (ecras estreitos): faixa azul-noite -> laranja como o fundo dos menus do jogo,
 * com cortes diagonais, a esfera do Dragon Fury e o titulo italico dourado com sombra.
 */
@Composable
private fun Header() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(listOf(DfColors.Navy, DfColors.BlueDeep, DfColors.OrangeDeep)),
            )
            .drawBehind {
                // Cortes diagonais claros (as "lascas" de energia dos menus do Budokai 3).
                val h = size.height
                for (k in 0 until 3) {
                    val x = size.width * (0.58f + k * 0.11f)
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(x, h)
                        lineTo(x + h * 0.55f, 0f)
                        lineTo(x + h * 0.55f + 10f + k * 6f, 0f)
                        lineTo(x + 10f + k * 6f, h)
                        close()
                    }
                    drawPath(path, DfColors.Gold.copy(alpha = 0.10f + k * 0.05f))
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_launcher_foreground),
            null,
            tint = androidx.compose.ui.graphics.Color.Unspecified,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text("DRAGON FURY", style = LogoStyle, color = DfColors.Gold)
            SlantStripe(Modifier.width(96.dp).height(4.dp))
            Text(
                stringResource(R.string.header_subtitle),
                style = MaterialTheme.typography.labelMedium,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun SideRail(selected: LauncherTab, onSelect: (LauncherTab) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
        modifier = Modifier
            .fillMaxHeight()
            .width(96.dp)
            .drawWithContent {
                drawContent()
                // Faixa de energia laranja -> azul na borda direita da barra.
                drawRect(
                    Brush.verticalGradient(listOf(DfColors.Gold, DfColors.Orange, DfColors.KiBlue.copy(alpha = 0.5f))),
                    topLeft = androidx.compose.ui.geometry.Offset(size.width - 3.dp.toPx(), 0f),
                    size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height),
                )
            },
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_launcher_foreground),
                null,
                tint = androidx.compose.ui.graphics.Color.Unspecified,
                modifier = Modifier.size(56.dp),
            )
            LauncherTab.entries.forEach { t ->
                NavigationRailItem(
                    selected = t == selected,
                    onClick = { onSelect(t) },
                    icon = { Icon(t.icon, null) },
                    label = { Text(stringResource(t.title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
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
        icon = { Icon(Icons.Filled.Memory, null) },
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
