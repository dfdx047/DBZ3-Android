package com.dfdx047.dragonfury.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dfdx047.dragonfury.BuildConfig
import com.dfdx047.dragonfury.DragonFuryApp
import com.dfdx047.dragonfury.R
import com.dfdx047.dragonfury.ui.LauncherViewModel
import com.dfdx047.dragonfury.ui.UpdateState
import com.dfdx047.dragonfury.ui.components.SectionCard
import com.dfdx047.dragonfury.ui.components.SlantStripe
import com.dfdx047.dragonfury.ui.theme.LogoStyle

/**
 * Aba Sobre, no formato do projeto original (README do NovaPowers): ficha do jogo, o projeto
 * de PC em que o port se baseia, o que o port Android acrescenta, creditos e aviso legal.
 */
@Composable
fun AboutScreen(vm: LauncherViewModel) {
    val context = LocalContext.current
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(R.drawable.ic_launcher_foreground), null, tint = Color.Unspecified, modifier = Modifier.size(132.dp))
        Text("DRAGON FURY", style = LogoStyle, color = MaterialTheme.colorScheme.primary)
        SlantStripe(Modifier.width(140.dp).height(5.dp))
        Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Ficha, como a tabela do README original.
        SectionCard(stringResource(R.string.about_sheet_title), Icons.Filled.SportsMma, modifier = Modifier.fillMaxWidth()) {
            listOf(
                R.string.about_sheet_players to stringResource(R.string.about_sheet_players_value),
                R.string.about_sheet_platform to stringResource(R.string.about_sheet_platform_value),
                R.string.about_sheet_engine to stringResource(R.string.about_sheet_engine_value),
                R.string.about_sheet_genre to stringResource(R.string.about_sheet_genre_value),
                R.string.about_sheet_base to "DBZ Budokai 3 HD Collection v${DragonFuryApp.ORIGINAL_VERSION}",
                R.string.about_sheet_version to BuildConfig.VERSION_NAME,
            ).forEachIndexed { i, (label, value) ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Row(Modifier.fillMaxWidth()) {
                    Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.42f))
                    Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.58f))
                }
            }
        }

        // Projeto original.
        SectionCard(
            stringResource(R.string.about_original_title),
            Icons.Filled.Favorite,
            modifier = Modifier.fillMaxWidth(),
            subtitle = "NovaPowers · Windows / Linux",
        ) {
            Text(stringResource(R.string.about_original_text), style = MaterialTheme.typography.bodySmall)
            FilledTonalButton(onClick = { open(DragonFuryApp.ORIGINAL_PROJECT_URL) }, modifier = Modifier.fillMaxWidth()) {
                Text("novapowers0/DBZ-Budokai-3-HD-Collection", maxLines = 1)
            }
        }

        // Este port.
        SectionCard(
            stringResource(R.string.about_port_title),
            Icons.Filled.PhoneAndroid,
            modifier = Modifier.fillMaxWidth(),
            subtitle = stringResource(R.string.about_developer, DragonFuryApp.DEVELOPER),
        ) {
            Text(stringResource(R.string.about_port_text), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { open(DragonFuryApp.GITHUB_URL) }, modifier = Modifier.weight(1f)) {
                    Text("GitHub", maxLines = 1)
                }
                OutlinedButton(
                    onClick = { vm.checkUpdates(manual = true) },
                    enabled = vm.updateState != UpdateState.CHECKING,
                    modifier = Modifier.weight(1f),
                ) {
                    if (vm.updateState == UpdateState.CHECKING) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.SystemUpdate, null)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.about_check_updates), maxLines = 1)
                }
            }
            if (vm.updateState == UpdateState.AVAILABLE) {
                TextButton(onClick = { vm.showUpdateDialog = true }) {
                    Text(stringResource(R.string.about_update_available, vm.updateInfo?.version ?: ""))
                }
            }
        }

        SectionCard(stringResource(R.string.about_credits_title), Icons.Filled.Gavel, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.about_credits), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.about_license), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.about_legal), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
