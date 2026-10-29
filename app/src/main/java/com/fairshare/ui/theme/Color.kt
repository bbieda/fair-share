package com.fairshare.ui.theme

import androidx.compose.ui.graphics.Color

// Material 3 dark scheme from the design
val Background = Color(0xFF0E1514)
val OnBackground = Color(0xFFDDE4E3)
val OnSurfaceVariant = Color(0xFFBEC9C8)
val SurfaceContainer = Color(0xFF1A2121)
val SurfaceContainerHighest = Color(0xFF2F3636)
val Primary = Color(0xFF80D5D4)
val OnPrimary = Color(0xFF003737)
val PrimaryContainer = Color(0xFF004F4F)
val OnPrimaryContainer = Color(0xFF9CF1F0)
val SecondaryContainer = Color(0xFF324B4B)
val OnSecondaryContainer = Color(0xFFCCE8E7)
val TertiaryContainer = Color(0xFF23476A)
val OnTertiaryContainer = Color(0xFFD1E4FF)
val Outline = Color(0xFF889392)
val OutlineVariant = Color(0xFF3F4948)
val Error = Color(0xFFFFB4AB)
val OnError = Color(0xFF690005)

// App-specific colors
val Positive = Color(0xFF8FD99A)
val Negative = Color(0xFFFFB4AB)
val UnreadBackground = Color(0xFF162020)

val ChartTeal = Color(0xFF80D5D4)
val ChartBlue = Color(0xFFB3C8E8)
val ChartPink = Color(0xFFEFB8C8)
val ChartOrange = Color(0xFFFFB870)
val ChartPurple = Color(0xFFCFBCFF)
val ChartGray = Color(0xFF899392)

enum class AvatarColors(val container: Color, val content: Color) {
    Pink(Color(0xFF633B48), Color(0xFFFFD8E4)),
    Blue(Color(0xFF2B4678), Color(0xFFD6E3FF)),
    Orange(Color(0xFF653E00), Color(0xFFFFDDB3)),
    Purple(Color(0xFF4F378B), Color(0xFFE9DDFF)),
    Green(Color(0xFF2D4F2B), Color(0xFFC2EFB0)),
    Teal(SecondaryContainer, OnSecondaryContainer),
}
