package com.dfdx047.dragonfury.ui.components

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dfdx047.dragonfury.DragonFuryApp
import com.dfdx047.dragonfury.R

/**
 * Redes do desenvolvedor: um botao por rede, na cor da marca (vermelho do YouTube, blurple do
 * Discord, grafite do GitHub, azul do Ko-fi), com o icone num circulo branco.
 *
 * Icones: se existir res/drawable/ic_social_<rede>.xml (ou .png) — por exemplo o logotipo
 * oficial tirado do kit de marca de cada rede — usa-se esse, com as cores originais. Sem ele
 * fica um icone generico do Material (play, conversa, codigo, chavena).
 */
private data class Social(
    val key: String,
    val name: String,
    val handle: Int,
    val url: String,
    val fallback: ImageVector,
    val color: Color,
    val dark: Color,
)

private val SOCIALS = listOf(
    Social("youtube", "YouTube", R.string.social_youtube_handle, DragonFuryApp.YOUTUBE_URL, Icons.Filled.PlayArrow,
        Color(0xFFFF0033), Color(0xFFB00020)),
    Social("discord", "Discord", R.string.social_discord_handle, DragonFuryApp.DISCORD_URL, Icons.Filled.Forum,
        Color(0xFF5865F2), Color(0xFF3B44B8)),
    Social("github", "GitHub", R.string.social_github_handle, DragonFuryApp.DEVELOPER_GITHUB_URL, Icons.Filled.Code,
        Color(0xFF2F363D), Color(0xFF0D1117)),
    Social("kofi", "Ko-fi", R.string.social_kofi_handle, DragonFuryApp.KOFI_URL, Icons.Filled.LocalCafe,
        Color(0xFF29ABE0), Color(0xFF1B7FB0)),
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
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Tela larga: os quatro numa linha; estreita: dois por linha.
        val perRow = if (maxWidth >= 600.dp) 4 else 2
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SOCIALS.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { s -> SocialButton(s, Modifier.weight(1f)) { openUrl(context, s.url) } }
                }
            }
        }
    }
}

@SuppressLint("DiscouragedApi")
@Composable
private fun SocialButton(s: Social, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    val custom = remember(s.key) {
        context.resources.getIdentifier("ic_social_${s.key}", "drawable", context.packageName)
    }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .height(54.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(s.color, s.dark)), shape)
            .drawBehind {
                // Brilho leve em cima, para nao ficar chapado.
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), endY = size.height * 0.6f))
            }
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .background(Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (custom != 0) {
                Icon(painterResource(custom), null, tint = Color.Unspecified, modifier = Modifier.size(24.dp))
            } else {
                Icon(s.fallback, null, tint = s.color, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                s.name,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                fontStyle = FontStyle.Italic,
                maxLines = 1,
            )
            Text(
                stringResource(s.handle),
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
