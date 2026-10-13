package com.xiaodlb.tdmk

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xiaodlb.tdmk.data.Appearance

internal fun Appearance.resolveDark(systemDark: Boolean): Boolean = when (this) {
    Appearance.SYSTEM -> systemDark
    Appearance.LIGHT -> false
    Appearance.DARK -> true
}
internal val LightColors = lightColorScheme(
    primary = Color(0xFF333C45), onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E8EB), onPrimaryContainer = Color(0xFF22292F),
    secondary = Color(0xFF555B60), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4E8EB), onSecondaryContainer = Color(0xFF22292F),
    background = Color(0xFFFAFAF9), surface = Color(0xFFFAFAF9), surfaceContainer = Color(0xFFF0F1F2),
    onSurface = Color(0xFF202327), onSurfaceVariant = Color(0xFF555B60),
    outline = Color(0xFF727B83), outlineVariant = Color(0xFFD0D6DA),
)
internal val DarkColors = darkColorScheme(
    primary = Color(0xFFD7E0E6), onPrimary = Color(0xFF1C242A),
    primaryContainer = Color(0xFF333F47), onPrimaryContainer = Color(0xFFE2E9EE),
    secondary = Color(0xFFB7C0C7), onSecondary = Color(0xFF1C242A),
    secondaryContainer = Color(0xFF354049), onSecondaryContainer = Color(0xFFE2E9EE),
    background = Color(0xFF14171A), surface = Color(0xFF14171A), surfaceContainer = Color(0xFF1F2428),
    onSurface = Color(0xFFE8ECEF), onSurfaceVariant = Color(0xFFB7C0C7),
    outline = Color(0xFF8C979F), outlineVariant = Color(0xFF3F484F),
)
internal object ShellSpacing {
    val page = 24.dp
    val vertical = 32.dp
    val gap = 16.dp
    val control = 56.dp
    val corner = 12.dp
}
@Composable
internal fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = Typography(), content = content)
}
