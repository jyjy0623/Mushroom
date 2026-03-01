package com.mushroom.adventure.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.os.Build

private val MushroomGreen = Color(0xFF4CAF50)
private val MushroomGreenDark = Color(0xFF388E3C)
private val MushroomGold = Color(0xFFFFB300)

private val LightColorScheme = lightColorScheme(
    primary = MushroomGreen,
    secondary = MushroomGold,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF1B5E20),
)

private val DarkColorScheme = darkColorScheme(
    primary = MushroomGreenDark,
    secondary = MushroomGold,
    onPrimary = Color.White,
)

@Composable
fun MushroomAdventureTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
