package com.haven.notes.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ColorScheme = lightColorScheme(
    primary = PastelRose,
    onPrimary = CardBackground,
    primaryContainer = NotePink,
    onPrimaryContainer = TextPrimary,
    secondary = PastelSkyBlue,
    onSecondary = CardBackground,
    secondaryContainer = NoteBlue,
    onSecondaryContainer = TextPrimary,
    tertiary = PastelMintGreen,
    onTertiary = CardBackground,
    tertiaryContainer = NoteGreen,
    onTertiaryContainer = TextPrimary,
    background = BackgroundLight,
    onBackground = TextPrimary,
    surface = CardBackground,
    onSurface = TextPrimary,
    error = CrimsonSOS,
    onError = CardBackground
)

@Composable
fun HavenNotesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        content = content
    )
}
