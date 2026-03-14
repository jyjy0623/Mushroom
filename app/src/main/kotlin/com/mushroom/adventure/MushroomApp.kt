package com.mushroom.adventure

import android.app.Application
import com.mushroom.adventure.core.network.repository.AuthRepository
import com.mushroom.core.data.seed.DeductionConfigSeed
import com.mushroom.core.data.seed.ScoringRuleTemplateSeed
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
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

private const val TAG = "APP"

@HiltAndroidApp
class MushroomApp : Application() {

    @Inject lateinit var logWriter: LogWriter
    @Inject lateinit var logFileWriter: LogFileWriter
    @Inject lateinit var taskTemplateSeed: TaskTemplateSeed
    @Inject lateinit var scoringRuleTemplateSeed: ScoringRuleTemplateSeed
    @Inject lateinit var deductionConfigSeed: DeductionConfigSeed
    @Inject lateinit var mushroomRewardEngine: MushroomRewardEngine
    @Inject lateinit var taskGeneratorService: TaskGeneratorService
    @Inject lateinit var authRepository: AuthRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        MushroomLogger.init(logWriter)
        logFileWriter.writeSessionStart()
        MushroomLogger.i(TAG, "MushroomApp.onCreate — application started")

        installCrashHandler()

        appScope.launch { taskTemplateSeed.seed() }
        appScope.launch { scoringRuleTemplateSeed.seed() }
        appScope.launch { deductionConfigSeed.seed() }

        // 每次 App 启动时为今天生成重复任务（幂等，多次调用无害）
        appScope.launch {
            runCatching { taskGeneratorService.generateForDate(LocalDate.now()) }
                .onFailure { MushroomLogger.e(TAG, "generateForDate failed", it) }
        }

        // 恢复登录会话：如果本地有 token，自动拉取用户资料
        appScope.launch {
            runCatching { authRepository.restoreSession() }
                .onFailure { MushroomLogger.e(TAG, "restoreSession failed", it) }
        }

        // Touch the engine so its init block subscribes to the event bus
        MushroomLogger.i(TAG, "MushroomRewardEngine initialized: $mushroomRewardEngine")
    }

    /**
     * 注册全局未捕获异常处理器。
     * 崩溃发生时同步写入 crash_*.txt，确保日志在进程被 kill 前落盘。
     */
    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val timestamp = LocalDateTime.now().toString().replace(':', '-')
                val crashFile = File(filesDir, "logs/crash_$timestamp.txt")
                crashFile.parentFile?.mkdirs()
                crashFile.writeText(buildString {
                    appendLine("========== CRASH @ $timestamp ==========")
                    appendLine("Thread: ${thread.name}")
                    appendLine()
                    appendLine(throwable.stackTraceToString())
                })
                MushroomLogger.e(TAG, "UNCAUGHT EXCEPTION on thread=${thread.name}", throwable)
            } catch (_: Throwable) {
                // 写文件本身失败时不能再抛，否则死循环
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
