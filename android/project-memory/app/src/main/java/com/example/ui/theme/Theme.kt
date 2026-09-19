package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
  primary = BurntOrange, onPrimary = CanvasDark, secondary = TextSecondaryDark,
  background = CanvasDark, onBackground = TextPrimaryDark, surface = SurfaceDark,
  onSurface = TextPrimaryDark, surfaceVariant = SurfaceRaisedDark,
  onSurfaceVariant = TextSecondaryDark, outline = SilverBorderDark,
  error = androidx.compose.ui.graphics.Color(0xFFFFB4AB)
)

private val LightColorScheme = lightColorScheme(
  primary = BurntOrange, onPrimary = androidx.compose.ui.graphics.Color.White,
  secondary = TextSecondaryLight, background = CanvasLight, onBackground = TextPrimaryLight,
  surface = SurfaceLight, onSurface = TextPrimaryLight, surfaceVariant = SurfaceRaisedLight,
  onSurfaceVariant = TextSecondaryLight, outline = SilverBorderLight
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
