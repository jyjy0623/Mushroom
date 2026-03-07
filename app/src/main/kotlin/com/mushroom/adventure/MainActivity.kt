package com.mushroom.adventure

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mushroom.adventure.navigation.AppBottomNavigationBar
import com.mushroom.adventure.navigation.AppDestination
import com.mushroom.adventure.navigation.AppNavGraph
import com.mushroom.adventure.ui.theme.MushroomAdventureTheme
import com.mushroom.adventure.update.UpdatePromptDialog
import com.mushroom.adventure.update.UpdateViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

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
        // 强制状态栏图标为深色（App 始终使用浅色背景，不随系统深色模式改变）
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
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
