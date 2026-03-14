package com.mushroom.adventure.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun MushroomAdventureTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) flavorDarkColorScheme() else flavorLightColorScheme()

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
