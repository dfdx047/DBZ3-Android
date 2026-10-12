package com.dfdx047.dragonfury.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dfdx047.dragonfury.R
import com.dfdx047.dragonfury.ui.LauncherViewModel
import com.dfdx047.dragonfury.ui.components.Level
import com.dfdx047.dragonfury.ui.components.SectionCard
import com.dfdx047.dragonfury.ui.components.SettingSwitch
import com.dfdx047.dragonfury.ui.components.StatusLine

@Composable
fun CheatsScreen(vm: LauncherViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionCard(stringResource(R.string.cheats_title), Icons.Filled.AutoFixHigh, subtitle = stringResource(R.string.cheats_subtitle)) {
            SettingSwitch(
                title = stringResource(R.string.cheat_unlock_characters),
                subtitle = stringResource(R.string.cheat_unlock_characters_sub),
                checked = vm.config.unlockAllCharacters,
            ) { on -> vm.updateConfig { it.copy(unlockAllCharacters = on) } }
            StatusLine(stringResource(R.string.cheats_save_safe), Level.OK)
        }
        SectionCard(stringResource(R.string.cheats_soon_title), Icons.Filled.HourglassTop) {
            listOf(
                R.string.cheat_soon_capsules,
                R.string.cheat_soon_transformations,
                R.string.cheat_soon_zeni,
                R.string.cheat_soon_stages,
            ).forEach { res ->
                SettingSwitch(title = stringResource(res), checked = false, enabled = false) {}
            }
            Text(
                stringResource(R.string.cheats_soon_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
