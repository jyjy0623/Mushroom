package com.mushroom.adventure

import android.app.Application
import com.mushroom.core.data.seed.DeductionConfigSeed
import com.mushroom.core.data.seed.TaskTemplateSeed
import com.mushroom.core.domain.service.TaskGeneratorService
import com.mushroom.core.logging.LogFileWriter
import com.mushroom.core.logging.LogWriter
import com.mushroom.core.logging.MushroomLogger
import com.mushroom.feature.mushroom.reward.MushroomRewardEngine
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

private const val TAG = "APP"

@HiltAndroidApp
class MushroomApp : Application() {

    @Inject lateinit var logWriter: LogWriter
    @Inject lateinit var logFileWriter: LogFileWriter
    @Inject lateinit var taskTemplateSeed: TaskTemplateSeed
    @Inject lateinit var deductionConfigSeed: DeductionConfigSeed
    @Inject lateinit var mushroomRewardEngine: MushroomRewardEngine
    @Inject lateinit var taskGeneratorService: TaskGeneratorService

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        MushroomLogger.init(logWriter)
        logFileWriter.writeSessionStart()
        MushroomLogger.i(TAG, "MushroomApp.onCreate — application started")

        appScope.launch { taskTemplateSeed.seed() }
        appScope.launch { deductionConfigSeed.seed() }

        // 每次 App 启动时为今天生成重复任务（幂等，多次调用无害）
        appScope.launch {
            runCatching { taskGeneratorService.generateForDate(LocalDate.now()) }
                .onFailure { MushroomLogger.e(TAG, "generateForDate failed", it) }
        }

        // Touch the engine so its init block subscribes to the event bus
        MushroomLogger.i(TAG, "MushroomRewardEngine initialized: $mushroomRewardEngine")
    }
}
