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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Refresh
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
import com.dfdx047.dragonfury.mods.ModProblem
import com.dfdx047.dragonfury.mods.ModType
import com.dfdx047.dragonfury.ui.LauncherViewModel
import com.dfdx047.dragonfury.ui.components.Level
import com.dfdx047.dragonfury.ui.components.Pill
import com.dfdx047.dragonfury.ui.components.SectionCard
import com.dfdx047.dragonfury.ui.components.StatusLine
import com.dfdx047.dragonfury.ui.components.formatBytes
import com.dfdx047.dragonfury.ui.theme.DfColors

@Composable
fun ModsScreen(vm: LauncherViewModel) {
    val pickZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.installModZip(uri)
    }
    var toDelete by remember { mutableStateOf<ModInfo?>(null) }
    val mods = vm.mods.filter { it.type != ModType.TEXTURES }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(stringResource(R.string.mods_title), Icons.Filled.Extension, subtitle = stringResource(R.string.mods_subtitle)) {
                Text(stringResource(R.string.mods_help), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { pickZip.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                        enabled = vm.busy == null,
                    ) {
                        Text(stringResource(R.string.mods_install_zip))
                    }
                    OutlinedButton(onClick = { vm.refreshMods() }) {
                        Text(stringResource(R.string.refresh))
                    }
                }
                val problems = mods.count { it.enabled && it.hasErrors }
                if (problems > 0) StatusLine(stringResource(R.string.mods_with_problems, problems), Level.WARN)
                if (vm.texturePacks.isNotEmpty()) {
                    StatusLine(stringResource(R.string.mods_texture_hint, vm.texturePacks.size), Level.INFO)
                }
            }
        }
        if (mods.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.mods_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        items(mods, key = { it.folderName }) { mod ->
            ModCard(
                mod,
                onToggle = { vm.setModEnabled(mod, it) },
                onFix = { vm.fixMod(mod) },
                onDelete = { toDelete = mod },
            )
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
fun modTypeLabel(t: ModType): String = stringResource(
    when (t) {
        ModType.CHARACTER -> R.string.mod_type_character
        ModType.COSTUME -> R.string.mod_type_costume
        ModType.ROSTER -> R.string.mod_type_roster
        ModType.AUDIO -> R.string.mod_type_audio
        ModType.DATA -> R.string.mod_type_data
        ModType.TEXTURES -> R.string.mod_type_textures
        ModType.MODEL_SWAP -> R.string.mod_type_swap
        ModType.PORT -> R.string.mod_type_port
        ModType.MOVESET -> R.string.mod_type_moveset
        ModType.OTHER -> R.string.mod_type_other
    },
)

@Composable
fun problemText(p: ModProblem): Pair<String, Level> = when (p) {
    ModProblem.Empty -> stringResource(R.string.mod_problem_empty) to Level.WARN
    is ModProblem.Nested -> stringResource(R.string.mod_problem_nested, p.inner) to Level.ERROR
    is ModProblem.MissingFile -> stringResource(R.string.mod_problem_missing, p.path) to Level.ERROR
    is ModProblem.BadToml -> stringResource(R.string.mod_problem_toml, p.file, p.message) to Level.ERROR
    is ModProblem.BadTextureName -> stringResource(R.string.mod_problem_tex_name, p.count) to Level.WARN
    is ModProblem.BadTextureSize -> stringResource(R.string.mod_problem_tex_size, p.file) to Level.ERROR
    is ModProblem.Overridden -> stringResource(R.string.mod_problem_overridden, p.path, p.winner) to Level.INFO
}

@Composable
private fun ModCard(mod: ModInfo, onToggle: (Boolean) -> Unit, onFix: () -> Unit, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (mod.enabled) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(mod.displayName, style = MaterialTheme.typography.titleMedium)
                    val meta = listOfNotNull(
                        mod.author.takeIf { it.isNotBlank() },
                        mod.version.takeIf { it.isNotBlank() }?.let { "v$it" },
                        stringResource(R.string.mod_files, mod.fileCount),
                        formatBytes(mod.sizeBytes),
                    ).joinToString(" · ")
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = mod.enabled, onCheckedChange = onToggle)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(modTypeLabel(mod.type), DfColors.Blue)
                if (mod.folderName.startsWith("_")) Pill(stringResource(R.string.mod_generated), DfColors.Warn)
            }
            if (mod.description.isNotBlank()) {
                Text(mod.description, style = MaterialTheme.typography.bodySmall)
            }
            mod.problems.forEach { p ->
                val (t, l) = problemText(p)
                StatusLine(t, l)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (mod.problems.any { it is ModProblem.Nested }) {
                    TextButton(onClick = onFix) {
                        Text(stringResource(R.string.fix))
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
            }
        }
    }
}
