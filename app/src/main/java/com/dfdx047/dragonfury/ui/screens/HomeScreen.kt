package com.dfdx047.dragonfury.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dfdx047.dragonfury.R
import com.dfdx047.dragonfury.data.FileStatus
import com.dfdx047.dragonfury.ui.LauncherTab
import com.dfdx047.dragonfury.ui.LauncherViewModel
import com.dfdx047.dragonfury.ui.components.KiClashAnimation
import com.dfdx047.dragonfury.ui.components.Level
import com.dfdx047.dragonfury.ui.components.Pill
import com.dfdx047.dragonfury.ui.components.SectionCard
import com.dfdx047.dragonfury.ui.components.SlantStripe
import com.dfdx047.dragonfury.ui.components.SocialLinks
import com.dfdx047.dragonfury.ui.components.StatusLine
import com.dfdx047.dragonfury.ui.components.color
import com.dfdx047.dragonfury.ui.theme.DfColors
import com.dfdx047.dragonfury.ui.theme.LogoStyle

/**
 * Aba Inicio: animacao do choque de ki com o titulo e o JOGAR, os ficheiros do jogo
 * (importar .iso ou pasta), as redes do desenvolvedor e atalhos. As opcoes ficam nas abas
 * proprias (Desempenho, Controles, Drivers, Configuracoes, Depuracao).
 */
@Composable
fun HomeScreen(vm: LauncherViewModel, goTo: (LauncherTab) -> Unit) {
    val context = LocalContext.current
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) vm.importGame(uri)
    }
    val pickIso = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.importIso(uri)
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
        // ---------------------------------------------------------------- destaque
        val heroShape = RoundedCornerShape(24.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(heroShape)
                .border(
                    1.5.dp,
                    Brush.linearGradient(listOf(DfColors.Gold, DfColors.Orange.copy(alpha = 0.4f), DfColors.KiBlue.copy(alpha = 0.6f))),
                    heroShape,
                )
                .background(Color(0xFF07061A)),
        ) {
            Box(Modifier.fillMaxWidth().height(210.dp)) {
                KiClashAnimation(Modifier.fillMaxSize())
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_launcher_foreground),
                        null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(58.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text("DRAGON FURY", style = LogoStyle, color = DfColors.Gold)
                        SlantStripe(Modifier.width(120.dp).height(5.dp))
                        Text(
                            stringResource(R.string.game_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontStyle = FontStyle.Italic,
                            color = Color.White.copy(alpha = 0.92f),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xFF07061A), DfColors.Navy, DfColors.BlueDeep)))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val (statusText, level) = when {
                    install == null -> stringResource(R.string.status_checking) to Level.INFO
                    install.xex?.status == FileStatus.EU_REGION -> stringResource(R.string.status_eu) to Level.ERROR
                    install.playable -> stringResource(R.string.status_ready) to Level.OK
                    install.installed -> stringResource(R.string.status_incomplete) to Level.WARN
                    else -> stringResource(R.string.status_not_installed) to Level.WARN
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(statusText, if (level == Level.OK) DfColors.Gold else level.color())
                    Spacer(Modifier.weight(1f))
                    Text(
                        stringResource(R.string.game_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
                Button(
                    onClick = { vm.play(context) },
                    enabled = install?.playable == true && vm.busy == null,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DfColors.Orange, contentColor = Color(0xFF2B1000)),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.play),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        fontStyle = FontStyle.Italic,
                    )
                }
            }
        }

        // ------------------------------------------------------- ficheiros do jogo
        SectionCard(
            stringResource(R.string.game_files_title),
            Icons.Filled.Album,
            subtitle = stringResource(R.string.game_files_subtitle),
        ) {
            Text(stringResource(R.string.game_files_help), style = MaterialTheme.typography.bodySmall)
            // .iso primeiro (o mais pratico); a pasta extraida continua a funcionar.
            Button(
                onClick = { pickIso.launch(arrayOf("application/x-iso9660-image", "application/octet-stream", "*/*")) },
                enabled = vm.busy == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Album, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(if (install?.installed == true) R.string.reimport_iso else R.string.import_iso))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { pickFolder.launch(null) }, enabled = vm.busy == null) {
                    Icon(Icons.Filled.FolderOpen, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.import_folder))
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

        // ------------------------------------------------------------------ redes
        SectionCard(
            stringResource(R.string.social_title),
            Icons.Filled.Groups,
            subtitle = stringResource(R.string.social_subtitle),
        ) {
            SocialLinks()
        }

        // ---------------------------------------------------------------- atalhos
        SectionCard(stringResource(R.string.quick_links_title), Icons.Filled.SportsEsports) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(onClick = { goTo(LauncherTab.PERFORMANCE) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.tab_performance), maxLines = 1)
                }
                FilledTonalButton(onClick = { goTo(LauncherTab.CONTROLS) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.tab_controls), maxLines = 1)
                }
                FilledTonalButton(onClick = { goTo(LauncherTab.DRIVERS) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.tab_drivers), maxLines = 1)
                }
            }
            val active = vm.activeDriver
            Text(
                if (active == null) stringResource(R.string.driver_current_system) else stringResource(R.string.driver_current, active),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { vm.createShortcut(context) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.AddToHomeScreen, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.create_shortcut))
            }
            Text(stringResource(R.string.create_shortcut_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
