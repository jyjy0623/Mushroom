package com.mushroom.adventure

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mushroom.adventure.navigation.AppBottomNavigationBar
import com.mushroom.adventure.navigation.AppDestination
import com.mushroom.adventure.navigation.AppNavGraph
import com.mushroom.adventure.parent.ParentAuthCoordinator
import com.mushroom.adventure.parent.PinRepository
import com.mushroom.adventure.parent.ui.PinVerifyDialog
import com.mushroom.adventure.ui.theme.MushroomAdventureTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var parentAuthCoordinator: ParentAuthCoordinator
    @Inject lateinit var pinRepository: PinRepository

    private val bottomNavRoutes = setOf(
        AppDestination.DailyTaskList.route,
        AppDestination.MushroomLedger.route,
        AppDestination.RewardList.route,
        AppDestination.Statistics.route,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MushroomAdventureTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val showBottomBar = currentRoute in bottomNavRoutes

                // 全局 PIN 验证 Dialog — 由 ParentAuthCoordinator 驱动
                val pendingRequest by parentAuthCoordinator.pendingRequest.collectAsStateWithLifecycle()
                var pinError by remember { mutableStateOf<String?>(null) }

                pendingRequest?.let { request ->
                    PinVerifyDialog(
                        reason = request.reason,
                        errorMessage = pinError,
                        onConfirm = { pin ->
                            val ok = pinRepository.verifyPin(pin)
                            if (ok) {
                                pinError = null
                                parentAuthCoordinator.submitPin(pin, pinRepository)
                            } else {
                                pinError = "PIN 错误，请重试"
                            }
                        },
                        onDismiss = {
                            pinError = null
                            parentAuthCoordinator.cancelAuth()
                        }
                    )
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (showBottomBar) {
                            AppBottomNavigationBar(navController = navController)
                        }
                    }
                ) { innerPadding ->
                    AppNavGraph(
                        navController = navController,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
