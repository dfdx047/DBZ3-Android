package com.dfdx047.dragonfury.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dfdx047.dragonfury.DragonFuryApp

/**
 * Redes do desenvolvedor: quatro botoes pequenos so com o nome (sem icones nem cores de marca),
 * numa linha.
 */
private val SOCIALS = listOf(
    "YouTube" to DragonFuryApp.YOUTUBE_URL,
    "Discord" to DragonFuryApp.DISCORD_URL,
    "GitHub" to DragonFuryApp.DEVELOPER_GITHUB_URL,
    "Ko-fi" to DragonFuryApp.KOFI_URL,
)

/** Abre um link no navegador (ou no app da rede). */
fun openUrl(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}

@Composable
fun SocialLinks(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SOCIALS.forEach { (name, url) ->
            OutlinedButton(
                onClick = { openUrl(context, url) },
                modifier = Modifier.weight(1f).height(36.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                Text(name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
