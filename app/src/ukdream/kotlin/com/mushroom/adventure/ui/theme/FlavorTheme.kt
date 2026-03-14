package com.mushroom.adventure.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val OxfordBlue = Color(0xFF1A3A6B)
private val OxfordBlueDark = Color(0xFF0F2545)
private val BritishGold = Color(0xFFC5A028)

fun flavorLightColorScheme(): ColorScheme = lightColorScheme(
    primary = OxfordBlue,
    secondary = BritishGold,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4E2F4),
    onPrimaryContainer = Color(0xFF0A1929),
    tertiary = Color(0xFF8B1A1A),
)

fun flavorDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = OxfordBlueDark,
    secondary = BritishGold,
    onPrimary = Color.White,
    tertiary = Color(0xFFCF6679),
)
