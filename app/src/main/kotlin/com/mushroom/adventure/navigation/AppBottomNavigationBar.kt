package com.mushroom.adventure.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

private data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val destination: AppDestination
)

private val bottomNavItems = listOf(
    BottomNavItem("任务", Icons.Default.CheckCircle, AppDestination.DailyTaskList),
    BottomNavItem("蘑菇", Icons.Default.Spa, AppDestination.MushroomLedger),
    BottomNavItem("奖品", Icons.Default.CardGiftcard, AppDestination.RewardList),
    BottomNavItem("统计", Icons.Default.BarChart, AppDestination.Statistics),
)

@Composable
fun AppBottomNavigationBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        bottomNavItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.destination.route,
                onClick = {
                    navController.navigate(item.destination.route) {
                        popUpTo(AppDestination.DailyTaskList.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                label = { Text(item.label) }
            )
        }
    }
}
