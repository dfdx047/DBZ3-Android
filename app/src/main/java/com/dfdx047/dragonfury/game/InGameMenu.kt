@file:OptIn(ExperimentalMaterial3Api::class)

package com.dfdx047.dragonfury.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.dfdx047.dragonfury.R
import com.dfdx047.dragonfury.controls.ControlsEditor
import com.dfdx047.dragonfury.data.ButtonBadge
import com.dfdx047.dragonfury.data.ControlStyle
import com.dfdx047.dragonfury.data.Directional
import com.dfdx047.dragonfury.data.LauncherPrefs
import com.dfdx047.dragonfury.ui.theme.DfColors
import kotlin.math.roundToInt

/**
 * O GameActivity e um SDLActivity (Activity simples, sem Lifecycle do AndroidX).
 * Para pôr um ComposeView por cima do jogo basta um "dono" de lifecycle e de
 * saved state sempre ativo.
 */
class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    init {
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}

enum class InGameScreen { CLOSED, MENU, EDITOR }

/** Ações do menu, implementadas pelo GameOverlay. */
interface InGameActions {
    fun resume()
    fun openEditor()
    fun closeEditor()
    fun openPortMenu()
    fun applyAppearance()
}

/** Painel de vidro fosco por cima do jogo pausado. */
@Composable
fun InGameOverlay(screen: InGameScreen, prefs: LauncherPrefs, actions: InGameActions) {
    when (screen) {
        InGameScreen.CLOSED -> Unit
        InGameScreen.EDITOR -> ControlsEditor(prefs, overGame = true) { actions.closeEditor() }
        InGameScreen.MENU -> Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            InGameMenuCard(prefs, actions)
        }
    }
}

@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
            ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun InGameMenuCard(prefs: LauncherPrefs, actions: InGameActions) {
    var directional by remember { mutableStateOf(prefs.directional) }
    var style by remember { mutableStateOf(prefs.controlStyle) }
    var badge by remember { mutableStateOf(prefs.buttonBadge) }
    var opacity by remember { mutableFloatStateOf(prefs.touchOpacity) }
    var haptics by remember { mutableStateOf(prefs.touchHaptics) }

    Column(
        Modifier
            .padding(12.dp)
            .widthIn(max = 560.dp)
            .background(Color(0xE6120C1E), RoundedCornerShape(24.dp))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Tune, null, tint = DfColors.Yellow)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ingame_title), style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(stringResource(R.string.ingame_paused), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
            }
        }

        Text(stringResource(R.string.ingame_directional), color = Color.White, style = MaterialTheme.typography.labelLarge)
        Segmented(
            listOf(Directional.DPAD to stringResource(R.string.directional_dpad), Directional.STICK to stringResource(R.string.directional_stick)),
            directional,
        ) {
            directional = it
            prefs.directional = it
            actions.applyAppearance()
        }

        Text(stringResource(R.string.settings_touch_style), color = Color.White, style = MaterialTheme.typography.labelLarge)
        Segmented(
            listOf(ControlStyle.DRAGON to "Dragon Fury", ControlStyle.XBOX to "Xbox", ControlStyle.PLAYSTATION to "PlayStation"),
            style,
        ) {
            style = it
            prefs.controlStyle = it
            actions.applyAppearance()
        }

        if (style == ControlStyle.DRAGON) {
            Text(stringResource(R.string.ingame_badge), color = Color.White, style = MaterialTheme.typography.labelLarge)
            Segmented(
                listOf(ButtonBadge.XBOX to "Xbox", ButtonBadge.PLAYSTATION to "PlayStation", ButtonBadge.NONE to stringResource(R.string.badge_none)),
                badge,
            ) {
                badge = it
                prefs.buttonBadge = it
                actions.applyAppearance()
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_touch_opacity), color = Color.White, modifier = Modifier.width(110.dp))
            Slider(
                value = opacity,
                onValueChange = { opacity = it },
                onValueChangeFinished = {
                    prefs.touchOpacity = opacity
                    actions.applyAppearance()
                },
                valueRange = 0.1f..1f,
                modifier = Modifier.weight(1f),
            )
            Text("${(opacity * 100).roundToInt()}%", color = Color.White, modifier = Modifier.width(48.dp).padding(start = 6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_touch_haptics), color = Color.White, modifier = Modifier.weight(1f))
            Switch(checked = haptics, onCheckedChange = {
                haptics = it
                prefs.touchHaptics = it
                actions.applyAppearance()
            })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(onClick = { actions.openEditor() }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Edit, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.settings_touch_edit), maxLines = 1)
            }
            FilledTonalButton(onClick = { actions.openPortMenu() }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.ingame_port_menu), maxLines = 1)
            }
        }
        Button(
            onClick = { actions.resume() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = DfColors.Orange, contentColor = Color(0xFF2B1000)),
        ) {
            Icon(Icons.Filled.PlayArrow, null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.ingame_resume))
        }
    }
}
