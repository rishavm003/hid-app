package com.haven.notes.ui.theme

import androidx.compose.ui.graphics.Color

// Girly & Pastel Theme Palette
val PastelBlushPink = Color(0xFFF06292)
val PastelRose = Color(0xFFE56B91)
val PastelSkyBlue = Color(0xFF64B5F6)
val PastelMintGreen = Color(0xFF81C784)
val PastelLavender = Color(0xFFBA68C8)
val PastelPeach = Color(0xFFFFB74D)

// Note Card Pastel Backgrounds
val NotePink = Color(0xFFFFF0F5)
val NoteBlue = Color(0xFFF0F8FF)
val NoteGreen = Color(0xFFF1FBF4)
val NoteLavender = Color(0xFFFAF3FD)
val NoteYellow = Color(0xFFFFFDF0)

// Note Card Borders
val NotePinkBorder = Color(0xFFF8D7E3)
val NoteBlueBorder = Color(0xFFD6E9F8)
val NoteGreenBorder = Color(0xFFD4F1DC)
val NoteLavenderBorder = Color(0xFFEEDCF8)
val NoteYellowBorder = Color(0xFFFBEFD0)

// Base UI Colors
val BackgroundLight = Color(0xFFFFF8FA) // soft strawberry-milk ivory
val CardBackground = Color(0xFFFFFFFF)
val CardBorder = Color(0xFFF5DCE6)
val CrimsonSOS = Color(0xFFE54360) // chic coral berry rose
val SageAccent = Color(0xFFE56B91) // blush rose for primary buttons
val PastelGreenAccent = Color(0xFF4CAF50)
val PastelBlueAccent = Color(0xFF29B6F6)

// Text Colors
val TextPrimary = Color(0xFF38222E) // soft dark plum/berry
val TextSecondary = Color(0xFF856677)
val Ink = Color(0xFF38222E)

// Decoy Camouflage Screen
val DecoyBg = Color(0xFFFFF7F9)
val DecoyHeader = Color(0xFF8C3E5E)
val DecoySearchBorder = Color(0xFFF7D5E2)

// Note Palette Model
data class NotePalette(
    val id: Int,
    val name: String,
    val emoji: String,
    val bg: Color,
    val border: Color,
    val accent: Color
)

val NotePalettes = listOf(
    NotePalette(0, "Blush", "🌸", NotePink, NotePinkBorder, PastelBlushPink),
    NotePalette(1, "Sky", "🫧", NoteBlue, NoteBlueBorder, PastelSkyBlue),
    NotePalette(2, "Mint", "🌿", NoteGreen, NoteGreenBorder, PastelMintGreen),
    NotePalette(3, "Lilac", "💜", NoteLavender, NoteLavenderBorder, PastelLavender),
    NotePalette(4, "Butter", "✨", NoteYellow, NoteYellowBorder, PastelPeach)
)

fun getNotePalette(colorIndex: Int): NotePalette {
    val idx = if (colorIndex in NotePalettes.indices) colorIndex else {
        val rem = colorIndex % NotePalettes.size
        if (rem < 0) rem + NotePalettes.size else rem
    }
    return NotePalettes[idx]
}
