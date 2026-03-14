package com.mushroom.adventure.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.mushroom.adventure.R
import com.mushroom.core.ui.R as CoreUiR

private data class BottomNavItem(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val destination: AppDestination
)

private val bottomNavItems = listOf(
    BottomNavItem(R.string.tab_tasks, Icons.Default.CheckCircle, AppDestination.DailyTaskList),
    BottomNavItem(CoreUiR.string.tab_ledger, Icons.Default.Home, AppDestination.MushroomLedger),
    BottomNavItem(R.string.tab_rewards, Icons.Default.ShoppingCart, AppDestination.RewardList),
    BottomNavItem(R.string.tab_statistics, Icons.Default.Star, AppDestination.Statistics),
    BottomNavItem(R.string.tab_settings, Icons.Default.Settings, AppDestination.Settings),
)

@Composable
fun AppBottomNavigationBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        bottomNavItems.forEach { item ->
            val label = stringResource(item.labelRes)
            NavigationBarItem(
                selected = currentRoute == item.destination.route,
                onClick = {
                    navController.navigate(item.destination.route) {
                        popUpTo(AppDestination.DailyTaskList.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(imageVector = item.icon, contentDescription = label) },
                label = { Text(label) }
            )
        }
    }
}
