package com.dfdx047.dragonfury.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dfdx047.dragonfury.R
import com.dfdx047.dragonfury.data.FileStatus
import com.dfdx047.dragonfury.data.GameOrientation
import com.dfdx047.dragonfury.ui.LauncherTab
import com.dfdx047.dragonfury.ui.LauncherViewModel
import com.dfdx047.dragonfury.ui.components.Level
import com.dfdx047.dragonfury.ui.components.Pill
import com.dfdx047.dragonfury.ui.components.SectionCard
import com.dfdx047.dragonfury.ui.components.SettingSegmented
import com.dfdx047.dragonfury.ui.components.StatusLine
import com.dfdx047.dragonfury.ui.components.color
import com.dfdx047.dragonfury.ui.theme.DfColors

@Composable
fun HomeScreen(vm: LauncherViewModel, goTo: (LauncherTab) -> Unit) {
    val context = LocalContext.current
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) vm.importGame(uri)
    }
    val install = vm.install
    var showFiles by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Cartao principal: estado + JOGAR
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(DfColors.Navy, DfColors.BlueDeep, DfColors.Orange)),
                    RoundedCornerShape(24.dp),
                )
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_launcher_foreground),
                        null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(72.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.game_title), style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text(stringResource(R.string.game_subtitle), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                    }
                }
                val (statusText, level) = when {
                    install == null -> stringResource(R.string.status_checking) to Level.INFO
                    install.xex?.status == FileStatus.EU_REGION -> stringResource(R.string.status_eu) to Level.ERROR
                    install.playable -> stringResource(R.string.status_ready) to Level.OK
                    install.installed -> stringResource(R.string.status_incomplete) to Level.WARN
                    else -> stringResource(R.string.status_not_installed) to Level.WARN
                }
                Pill(statusText, if (level == Level.OK) DfColors.Yellow else level.color())
                Button(
                    onClick = { vm.play(context) },
                    enabled = install?.playable == true && vm.busy == null,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DfColors.Orange, contentColor = Color(0xFF2B1000)),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.play), style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        SectionCard(
            stringResource(R.string.game_files_title),
            Icons.Filled.FolderOpen,
            subtitle = stringResource(R.string.game_files_subtitle),
        ) {
            Text(stringResource(R.string.game_files_help), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { pickFolder.launch(null) }, enabled = vm.busy == null) {
                    Icon(Icons.Filled.FolderOpen, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (install?.installed == true) R.string.reimport else R.string.import_game))
                }
                OutlinedButton(onClick = { vm.refreshInstall(true) }, enabled = install?.installed == true && vm.busy == null) {
                    Icon(Icons.Filled.VerifiedUser, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.verify))
                }
            }
            if (install != null && install.installed) {
                TextButton(onClick = { showFiles = !showFiles }) {
                    Text(stringResource(if (showFiles) R.string.hide_files else R.string.show_files))
                }
                if (showFiles) {
                    install.checks.forEach { c ->
                        val (label, lvl) = when (c.status) {
                            FileStatus.OK -> stringResource(R.string.file_ok) to Level.OK
                            FileStatus.MISSING -> stringResource(if (c.required) R.string.file_missing_required else R.string.file_missing_optional) to
                                (if (c.required) Level.ERROR else Level.INFO)
                            FileStatus.DIFFERENT -> stringResource(R.string.file_different) to Level.WARN
                            FileStatus.WRONG_SIZE -> stringResource(R.string.file_wrong_size) to Level.ERROR
                            FileStatus.WRONG_HASH -> stringResource(R.string.file_wrong_hash) to Level.ERROR
                            FileStatus.EU_REGION -> stringResource(R.string.file_eu) to Level.ERROR
                        }
                        StatusLine("${c.name} — $label", lvl)
                    }
                }
            }
        }

        SectionCard(stringResource(R.string.launch_options_title), Icons.Filled.ScreenRotation) {
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
            OutlinedButton(onClick = { vm.createShortcut(context) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.AddToHomeScreen, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.create_shortcut))
            }
            Text(stringResource(R.string.create_shortcut_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionCard(stringResource(R.string.quick_links_title), Icons.Filled.SportsEsports) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(onClick = { goTo(LauncherTab.SETTINGS) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.tab_settings))
                }
                FilledTonalButton(onClick = { goTo(LauncherTab.DRIVERS) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.tab_drivers))
                }
            }
            val active = vm.activeDriver
            Text(
                if (active == null) stringResource(R.string.driver_current_system) else stringResource(R.string.driver_current, active),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
