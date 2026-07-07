package app.dift.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Minimalist near-monochrome palette: one accent, calm neutrals. Deliberately not dynamic-color —
// the app should look identical everywhere and stay visually quiet.
private val Accent = Color(0xFF4C6FFF)

private val LightColors = lightColorScheme(
    primary = Accent,
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1A1B1E),
    onSurface = Color(0xFF1A1B1E),
)

private val DarkColors = darkColorScheme(
    primary = Accent,
    background = Color(0xFF121316),
    surface = Color(0xFF1A1B1E),
    onBackground = Color(0xFFE8EAED),
    onSurface = Color(0xFFE8EAED),
)

@Composable
fun DiftTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
