package com.dfdx047.dragonfury.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dfdx047.dragonfury.R
import com.dfdx047.dragonfury.mods.ModInfo
import com.dfdx047.dragonfury.textures.RuntimeTextureLine
import com.dfdx047.dragonfury.textures.TextureIssue
import com.dfdx047.dragonfury.textures.TexturePackReport
import com.dfdx047.dragonfury.textures.TexturePacks
import com.dfdx047.dragonfury.ui.LauncherViewModel
import com.dfdx047.dragonfury.ui.components.Level
import com.dfdx047.dragonfury.ui.components.Pill
import com.dfdx047.dragonfury.ui.components.SectionCard
import com.dfdx047.dragonfury.ui.components.SettingSwitch
import com.dfdx047.dragonfury.ui.components.StatusLine
import com.dfdx047.dragonfury.ui.components.formatBytes
import com.dfdx047.dragonfury.ui.theme.DfColors

@Composable
fun TexturesScreen(vm: LauncherViewModel) {
    val pickZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.installTexturePackZip(uri)
    }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        vm.importTextureImages(uris)
    }
    var toDelete by remember { mutableStateOf<ModInfo?>(null) }
    val packs = vm.texturePacks
    val reports = vm.textureReports

    // Primeira visita: valida logo (so le cabecalhos, e rapido).
    LaunchedEffect(packs.size) {
        if (reports == null && packs.isNotEmpty() && vm.busy == null) vm.validateTextures()
        vm.refreshRuntimeLog()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(stringResource(R.string.textures_title), Icons.Filled.Texture, subtitle = stringResource(R.string.textures_subtitle)) {
                SettingSwitch(
                    title = stringResource(R.string.textures_enabled),
                    subtitle = stringResource(R.string.textures_enabled_sub),
                    checked = vm.config.texturePacksOn,
                ) { on -> vm.updateConfig { it.copy(texturePacksOn = on) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { pickZip.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                        enabled = vm.busy == null,
                    ) {
                        Text(stringResource(R.string.textures_install_zip))
                    }
                    OutlinedButton(
                        onClick = { pickImages.launch(arrayOf("image/png", "image/x-dds", "image/vnd-ms.dds", "application/octet-stream")) },
                        enabled = vm.busy == null,
                    ) {
                        Text(stringResource(R.string.textures_add_images))
                    }
                }
                OutlinedButton(onClick = { vm.validateTextures() }, enabled = vm.busy == null && packs.isNotEmpty()) {
                    Text(stringResource(R.string.textures_validate))
                }
            }
        }
        item {
            SectionCard(stringResource(R.string.textures_rules_title), Icons.Filled.Checklist) {
                Text(stringResource(R.string.textures_rules), style = MaterialTheme.typography.bodySmall)
                StatusLine(stringResource(R.string.textures_vulkan_note), Level.INFO)
            }
        }
        if (packs.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.textures_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        items(packs, key = { it.folderName }) { pack ->
            PackCard(
                pack,
                reports?.firstOrNull { it.mod.folderName == pack.folderName },
                onToggle = { vm.setTexturePackEnabled(pack, it) },
                onDelete = { toDelete = pack },
            )
        }
        if (reports != null) {
            val totalBytes = reports.filter { it.mod.enabled }.sumOf { it.decodedBytes }
            item {
                val over = totalBytes > TexturePacks.RUNTIME_CACHE_BUDGET
                StatusLine(
                    stringResource(if (over) R.string.textures_ram_over else R.string.textures_ram, formatBytes(totalBytes)),
                    if (over) Level.WARN else Level.INFO,
                )
            }
        }
        item {
            RuntimeLogCard(vm.runtimeTextureLog)
        }
    }

    toDelete?.let { mod ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.mods_delete_title)) },
            text = { Text(stringResource(R.string.mods_delete_text, mod.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteMod(mod)
                    toDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun issueText(i: TextureIssue): String = stringResource(
    when (i) {
        TextureIssue.OK -> R.string.tex_issue_ok
        TextureIssue.BAD_NAME -> R.string.tex_issue_bad_name
        TextureIssue.UNREADABLE -> R.string.tex_issue_unreadable
        TextureIssue.DDS_FORMAT -> R.string.tex_issue_dds
        TextureIssue.BAD_SIZE -> R.string.tex_issue_bad_size
        TextureIssue.DUPLICATE -> R.string.tex_issue_duplicate
    },
)

@Composable
private fun PackCard(pack: ModInfo, report: TexturePackReport?, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(pack.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(
                            pack.author.takeIf { it.isNotBlank() },
                            stringResource(R.string.mod_files, pack.fileCount),
                            formatBytes(pack.sizeBytes),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = pack.enabled, onCheckedChange = onToggle)
            }
            if (report == null) {
                Text(stringResource(R.string.textures_not_validated), style = MaterialTheme.typography.bodySmall)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(stringResource(R.string.textures_ok_count, report.okCount), DfColors.Ok)
                    if (report.warnCount > 0) Pill(stringResource(R.string.textures_warn_count, report.warnCount), DfColors.Warn)
                    if (report.errorCount > 0) Pill(stringResource(R.string.textures_error_count, report.errorCount), DfColors.Error)
                    if (report.maxScale > 0) Pill("x${report.maxScale}", DfColors.Blue)
                }
                val status = when {
                    report.okCount == 0 -> stringResource(R.string.textures_status_none) to Level.ERROR
                    report.errorCount > 0 -> stringResource(R.string.textures_status_partial) to Level.WARN
                    else -> stringResource(R.string.textures_status_ok) to Level.OK
                }
                StatusLine(status.first, status.second)
                val issues = report.files.filter { it.issue != TextureIssue.OK }
                if (issues.isNotEmpty()) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(stringResource(if (expanded) R.string.hide_details else R.string.show_details, issues.size))
                    }
                    if (expanded) {
                        issues.take(100).forEach { f ->
                            val lvl = if (f.issue == TextureIssue.DUPLICATE) Level.WARN else Level.ERROR
                            val extra = if (f.detail.isNotEmpty()) " (${f.detail})" else ""
                            StatusLine("${f.file.name}: ${issueText(f.issue)}$extra", lvl)
                        }
                        if (issues.size > 100) Text("+${issues.size - 100}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Row {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
            }
        }
    }
}

@Composable
private fun RuntimeLogCard(lines: List<RuntimeTextureLine>) {
    SectionCard(
        stringResource(R.string.textures_log_title),
        Icons.Filled.ReceiptLong,
        subtitle = stringResource(R.string.textures_log_subtitle),
    ) {
        if (lines.isEmpty()) {
            Text(stringResource(R.string.textures_log_empty), style = MaterialTheme.typography.bodySmall)
        } else {
            lines.take(60).forEach { l ->
                StatusLine(
                    l.text,
                    when (l.level) {
                        RuntimeTextureLine.Level.INFO -> Level.INFO
                        RuntimeTextureLine.Level.WARN -> Level.WARN
                        RuntimeTextureLine.Level.ERROR -> Level.ERROR
                    },
                )
            }
        }
    }
}
