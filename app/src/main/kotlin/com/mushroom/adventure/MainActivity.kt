package com.mushroom.adventure

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
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
import com.mushroom.adventure.update.UpdatePromptDialog
import com.mushroom.adventure.update.UpdateViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var parentAuthCoordinator: ParentAuthCoordinator
    @Inject lateinit var pinRepository: PinRepository

    private val updateViewModel: UpdateViewModel by viewModels()

    private val bottomNavRoutes = setOf(
        AppDestination.DailyTaskList.route,
        AppDestination.MushroomLedger.route,
        AppDestination.RewardList.route,
        AppDestination.Statistics.route,
        AppDestination.Settings.route,
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

                // 版本更新检测：主界面加载完成后在后台触发一次，不阻塞启动流程
                LaunchedEffect(Unit) {
                    updateViewModel.checkForUpdate()
                }

                // 版本更新弹窗
                val updateInfo by updateViewModel.updateInfo.collectAsStateWithLifecycle()
                updateInfo?.let { info ->
                    UpdatePromptDialog(
                        info = info,
                        currentVersion = BuildConfig.VERSION_NAME,
                        onDismiss = { updateViewModel.dismissUpdate() },
                        onDownload = {
                            updateViewModel.dismissUpdate()
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl)))
                        },
                    )
                }

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
                        updateViewModel = updateViewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
