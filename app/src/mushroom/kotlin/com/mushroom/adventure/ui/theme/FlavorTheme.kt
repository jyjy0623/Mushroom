package com.mushroom.adventure.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val MushroomGreen = Color(0xFF4CAF50)
private val MushroomGreenDark = Color(0xFF388E3C)
private val MushroomGold = Color(0xFFFFB300)

fun flavorLightColorScheme(): ColorScheme = lightColorScheme(
    primary = MushroomGreen,
    secondary = MushroomGold,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF1B5E20),
)

fun flavorDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = MushroomGreenDark,
    secondary = MushroomGold,
    onPrimary = Color.White,
)
