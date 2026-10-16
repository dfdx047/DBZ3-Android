package com.dfdx047.dragonfury.ui.components

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
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dfdx047.dragonfury.DragonFuryApp
import com.dfdx047.dragonfury.R

/**
 * Redes do desenvolvedor. Cada botao e uma "capsula" (pontas redondas, como as capsulas do
 * Dragon Ball) com as cores da rede: vermelho do YouTube, roxo-azulado do Discord, grafite do
 * GitHub e azul-ceu do Ko-fi. Os icones sao os genericos do Material (ecra com play, conversa,
 * codigo, chavena), nao os logotipos oficiais.
 */
private data class Social(
    val name: String,
    val handle: Int,
    val url: String,
    val icon: ImageVector,
    val colors: List<Color>,
)

private val SOCIALS = listOf(
    Social("YouTube", R.string.social_youtube_handle, DragonFuryApp.YOUTUBE_URL, Icons.Filled.SmartDisplay,
        listOf(Color(0xFFFF2D2D), Color(0xFFC4001A))),
    Social("Discord", R.string.social_discord_handle, DragonFuryApp.DISCORD_URL, Icons.Filled.Forum,
        listOf(Color(0xFF7983F5), Color(0xFF4752C4))),
    Social("GitHub", R.string.social_github_handle, DragonFuryApp.DEVELOPER_GITHUB_URL, Icons.Filled.Code,
        listOf(Color(0xFF3D444D), Color(0xFF161B22))),
    Social("Ko-fi", R.string.social_kofi_handle, DragonFuryApp.KOFI_URL, Icons.Filled.LocalCafe,
        listOf(Color(0xFF3CC4F0), Color(0xFF1F8FC6))),
)

@Composable
fun SocialLinks(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    fun open(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Ecra largo: os quatro numa linha; estreito: dois por linha.
        val perRow = if (maxWidth >= 560.dp) 4 else 2
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SOCIALS.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { s ->
                        SocialCapsule(s, Modifier.weight(1f)) { open(s.url) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SocialCapsule(s: Social, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .height(56.dp)
            .clip(shape)
            .background(Brush.linearGradient(s.colors), shape)
            .drawBehind {
                // Brilho em cima e um risco diagonal claro, como o reflexo de uma capsula.
                drawRect(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), endY = size.height * 0.55f),
                )
                drawLine(
                    Color.White.copy(alpha = 0.12f),
                    Offset(size.width * 0.62f, size.height),
                    Offset(size.width * 0.78f, 0f),
                    strokeWidth = size.height * 0.35f,
                )
            }
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(Color.White.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(s.icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
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
