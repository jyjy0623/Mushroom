package com.mushroom.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mushroom.core.domain.entity.MushroomLevel

/**
 * Flavor-aware display extensions for MushroomLevel.
 * String resources are defined in core-ui defaults and overridden by app flavor source sets.
 */

@Composable
fun MushroomLevel.themedDisplayName(): String = stringResource(
    when (this) {
        MushroomLevel.SMALL -> R.string.level_small
        MushroomLevel.MEDIUM -> R.string.level_medium
        MushroomLevel.LARGE -> R.string.level_large
        MushroomLevel.GOLD -> R.string.level_gold
        MushroomLevel.LEGEND -> R.string.level_legend
    }
)

@Composable
fun MushroomLevel.themedEmoji(): String = stringResource(
    when (this) {
        MushroomLevel.SMALL -> R.string.level_emoji_small
        MushroomLevel.MEDIUM -> R.string.level_emoji_medium
        MushroomLevel.LARGE -> R.string.level_emoji_large
        MushroomLevel.GOLD -> R.string.level_emoji_gold
        MushroomLevel.LEGEND -> R.string.level_emoji_legend
    }
)
