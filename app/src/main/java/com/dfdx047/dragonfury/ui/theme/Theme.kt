package com.dfdx047.dragonfury.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dfdx047.dragonfury.data.DarkMode
import com.dfdx047.dragonfury.data.ThemeMode

/** Paleta Dragon Fury: laranja de esfera, azul de aura, amarelo de ki. */
object DfColors {
    val Orange = Color(0xFFFF8A1F)
    val OrangeDeep = Color(0xFFE0560B)
    val Yellow = Color(0xFFFFC94A)
    val Blue = Color(0xFF1E5BFF)
    val BlueDeep = Color(0xFF0B2A8C)
    val Navy = Color(0xFF0B1A4A)
    val StarRed = Color(0xFFD7261E)
    /** Cores do jogo (menus do Budokai 3 HD): dourado do titulo, azul de ki, roxo de energia. */
    val Gold = Color(0xFFFFE27A)
    val KiBlue = Color(0xFF3FD0FF)
    val KiWhite = Color(0xFFEFFBFF)
    val Energy = Color(0xFF9B4DFF)
    val Ok = Color(0xFF2E9E5B)
    val Warn = Color(0xFFE0A000)
    val Error = Color(0xFFD7261E)
}

private val DragonLight = lightColorScheme(
    primary = DfColors.OrangeDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC2),
    onPrimaryContainer = Color(0xFF331200),
    secondary = DfColors.Blue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE3FF),
    onSecondaryContainer = Color(0xFF001550),
    tertiary = Color(0xFF8A6100),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE08F),
    onTertiaryContainer = Color(0xFF2B1D00),
    background = Color(0xFFFFF7EE),
    onBackground = Color(0xFF1F1A16),
    surface = Color(0xFFFFF7EE),
    onSurface = Color(0xFF1F1A16),
    surfaceVariant = Color(0xFFF3E0D2),
    onSurfaceVariant = Color(0xFF52443A),
    surfaceContainer = Color(0xFFFCEBDD),
    surfaceContainerHigh = Color(0xFFF7E3D3),
    outline = Color(0xFF85746A),
    error = DfColors.Error,
)

private val DragonDark = darkColorScheme(
    primary = DfColors.Orange,
    onPrimary = Color(0xFF3A1500),
    primaryContainer = Color(0xFF7A3000),
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFF8FA8FF),
    onSecondary = Color(0xFF002080),
    secondaryContainer = DfColors.BlueDeep,
    onSecondaryContainer = Color(0xFFDDE3FF),
    tertiary = DfColors.Yellow,
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5B4300),
    onTertiaryContainer = Color(0xFFFFE08F),
    background = Color(0xFF120C1E),
    onBackground = Color(0xFFF1E6DD),
    surface = Color(0xFF120C1E),
    onSurface = Color(0xFFF1E6DD),
    surfaceVariant = Color(0xFF2A2236),
    onSurfaceVariant = Color(0xFFD8C8BC),
    surfaceContainer = Color(0xFF1C1529),
    surfaceContainerHigh = Color(0xFF241C33),
    outline = Color(0xFF9E8E84),
    error = Color(0xFFFF6B5E),
)

// Titulos em italico pesado, como os menus do Budokai 3 (letras inclinadas e grossas); o
// texto corrido fica normal para ler bem.
private val DfTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, letterSpacing = 0.3.sp),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
    )
}

/** Logotipo: italico, grosso, com sombra escura (como o titulo do jogo). */
val LogoStyle = TextStyle(
    fontWeight = FontWeight.Black,
    fontStyle = FontStyle.Italic,
    fontSize = 30.sp,
    letterSpacing = 1.sp,
    shadow = Shadow(color = Color(0xCC3A1200), offset = Offset(3f, 3f), blurRadius = 4f),
)

@Composable
fun DragonFuryTheme(themeMode: ThemeMode, darkMode: DarkMode, content: @Composable () -> Unit) {
    val dark = when (darkMode) {
        DarkMode.SYSTEM -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }
    val ctx = LocalContext.current
    val scheme: ColorScheme = if (themeMode == ThemeMode.MATERIAL_YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        if (dark) DragonDark else DragonLight
    }
    MaterialTheme(colorScheme = scheme, typography = DfTypography, content = content)
}
