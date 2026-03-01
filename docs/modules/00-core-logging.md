# 蘑菇打卡 - core-logging 模块详细设计

**版本**：v1.1
**日期**：2026-03-01
**状态**：待审核

**变更记录**：
| 版本 | 日期 | 变更内容 |
|-----|------|---------|
| v1.0 | 2026-03-01 | 初版 |
| v1.1 | 2026-03-01 | Release 版本仅输出 ERROR；INFO 设计原则化（只记录模块边界事件、事件数量上限、清单约束）；日志文件总上限 512KB、保留 2 天 |

---

## 一、模块职责与边界

### 职责
- 提供全应用统一的日志输出门面（`MushroomLogger`），屏蔽底层实现
- 按构建类型（Debug / Release）控制日志级别和输出目标
- 将 WARN 及以上级别日志滚动写入本地文件，支持近2天留存（Debug 构建含 INFO）
- 收集设备和应用诊断摘要，辅助问题复现
- 提供日志导出功能（ZIP 打包，系统分享）

### 边界
- 不包含任何业务逻辑
- 不依赖 `core-domain`，是被所有模块依赖的基础设施层
- 不依赖 `android.util.Log`（对外完全屏蔽，内部实现中使用）

### 模块依赖关系
```
core-logging
    ▲   ▲   ▲   ▲
    │   │   │   │
core-data  core-ui  feature-*  service-*
（所有其他模块均依赖 core-logging）
```

---

## 二、日志级别定义与输出策略

| 级别 | 枚举值 | 用途 | Debug 构建 | Release 构建 |
|-----|-------|------|-----------|------------|
| VERBOSE | `LogLevel.V` | 详细调试（Flow emit、UI 重组） | Logcat | 不输出 |
| DEBUG | `LogLevel.D` | 开发调试（入参、中间值） | Logcat | 不输出 |
| INFO | `LogLevel.I` | 各模块关键流程节点（见第三章） | Logcat + 文件 | **不输出** |
| WARN | `LogLevel.W` | 非预期但可恢复的情况 | Logcat + 文件 | 文件 |
| ERROR | `LogLevel.E` | 异常和错误（含完整栈跟踪） | Logcat + 文件 | **Logcat + 文件** |

**Release 版本只输出 ERROR 和 WARN**，INFO 及以下完全关闭。ERROR 含完整栈跟踪，是线上问题定位的唯一手段。

---

## 三、INFO 日志设计原则

INFO 日志只在 Debug 构建下记录，用于开发阶段通过日志时间线还原各模块关键流程，**不是**记录所有操作的流水账。

### 原则一：只记录模块边界事件，不记录内部步骤

每个模块只在两类位置输出 INFO：
- **流程入口**：Use Case 被调用，表明某项用户行为触发了该模块
- **流程结果**：Use Case 执行完成，表明该模块关键状态发生了变化

中间步骤（数据库读取、数据转换、规则计算过程）使用 DEBUG，不用 INFO。

```kotlin
// ✅ 正确：入口 + 结果各一条 INFO
class CheckInTaskUseCase(...) {
    suspend operator fun invoke(taskId: Long): Result<CheckIn> = runCatching {
        MushroomLogger.i(TAG, "打卡 taskId=$taskId")
        val checkIn = doCheckIn(taskId)
        MushroomLogger.i(TAG, "打卡完成 isEarly=${checkIn.isEarly} earlyMin=${checkIn.earlyMinutes}")
        checkIn
    }.onFailure { e ->
        MushroomLogger.e(TAG, "打卡失败 taskId=$taskId", e)
    }
}

// ❌ 错误：中间步骤不用 INFO
MushroomLogger.i(TAG, "开始查询任务")        // ❌ 内部步骤
MushroomLogger.i(TAG, "计算是否提前完成")    // ❌ 内部步骤
```

### 原则二：每个模块 INFO 事件数量有上限

| 模块规模 | INFO 事件上限 |
|---------|------------|
| 核心模块（task、checkin、mushroom） | 每模块 ≤ 5 个 |
| 普通模块（reward、milestone、statistics） | 每模块 ≤ 3 个 |
| 基础设施（core-data、core-logging） | ≤ 2 个 |

超出上限的场景一律使用 DEBUG。

### 原则三：INFO 事件清单在模块文档中明确定义

各模块详细设计文档必须列出该模块允许的全部 INFO 事件，形成约束清单，禁止随意新增。

**全局 INFO 事件清单**（所有模块汇总）：

| 模块 | INFO 事件 | 日志示例 |
|-----|---------|---------|
| APP 启动 | 应用启动（1个） | `[APP] 启动 v1.2.0 db=3` |
| core-data | DB Migration 完成（1个） | `[DB] Migration 1→2 完成` |
| feature-task | 任务创建、任务删除（2个） | `[TASK] 创建 id=42 date=2026-03-01` |
| feature-checkin | 打卡触发、打卡完成（2个） | `[CHECKIN] 完成 taskId=42 isEarly=true earlyMin=35` |
| feature-mushroom | 蘑菇发放、蘑菇扣除（2个） | `[MUSHROOM] 发放 SMALL×1 src=TASK balance={S:12,M:3}` |
| feature-reward | 拼图解锁（1个） | `[REWARD] 解锁 rewardId=3 progress=8/20` |
| feature-milestone | 成绩录入（1个） | `[MILESTONE] 录入 id=5 score=92` |
| core-logging | 日志导出完成（1个） | `[LOG] 导出完成 size=128KB` |

**合计：全局至多 13 个 INFO 事件**，保持日志精简。

---

## 四、核心接口设计

### 4.1 MushroomLogger（日志门面）

```kotlin
// core-logging/src/main/java/com/mushroom/logging/MushroomLogger.kt

object MushroomLogger {

    private var writer: LogWriter = NoOpLogWriter  // 由 Hilt 在 Application 初始化时注入

    fun init(writer: LogWriter) {
        this.writer = writer
    }

    fun v(tag: String, message: String) =
        writer.log(LogLevel.V, tag, message, null)

    fun d(tag: String, message: String) =
        writer.log(LogLevel.D, tag, message, null)

    fun i(tag: String, message: String) =
        writer.log(LogLevel.I, tag, message, null)

    fun w(tag: String, message: String, throwable: Throwable? = null) =
        writer.log(LogLevel.W, tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        writer.log(LogLevel.E, tag, message, throwable)
}

enum class LogLevel { V, D, I, W, E }
```

### 4.2 LogWriter（输出策略接口）

```kotlin
interface LogWriter {
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?)
}

// Debug 构建实现：同时输出 Logcat 和文件
class DebugLogWriter(private val fileWriter: LogFileWriter) : LogWriter {
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        val formatted = formatMessage(level, tag, message, throwable)
        android.util.Log.println(level.androidPriority, tag, message)
        if (level >= LogLevel.I) fileWriter.write(formatted)
    }
}

// Release 构建实现：只输出 WARN 和 ERROR，INFO 及以下全部裁剪
class ReleaseLogWriter(private val fileWriter: LogFileWriter) : LogWriter {
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        if (level < LogLevel.W) return   // VERBOSE / DEBUG / INFO 全部不输出
        val formatted = formatMessage(level, tag, message, throwable)
        fileWriter.write(formatted)
        android.util.Log.println(level.androidPriority, tag, formatted)
    }
}
```

### 4.3 LogFileWriter（滚动文件写入）

```kotlin
class LogFileWriter(private val context: Context) {

    // 文件路径：<filesDir>/logs/mushroom_log_20260301.txt
    private val logDir: File = File(context.filesDir, "logs")

    companion object {
        const val MAX_TOTAL_SIZE_KB = 512    // 所有日志文件总大小上限
        const val MAX_RETAIN_DAYS   = 2      // 保留最近2天
    }

    fun write(message: String) {
        val file = getCurrentLogFile()
        file.appendText(message + "\n")
    }

    private fun getCurrentLogFile(): File {
        val dateStr = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
        return File(logDir, "mushroom_log_$dateStr.txt").also { it.parentFile?.mkdirs() }
    }

    fun purgeAndRotate() {
        val cutoff = LocalDate.now().minusDays(MAX_RETAIN_DAYS.toLong())
        // 1. 删除2天前的日志文件
        logDir.listFiles()
            ?.filter { parseFileDate(it.name)?.isBefore(cutoff) == true }
            ?.forEach { it.delete() }
        // 2. 若所有剩余文件总大小仍超过512KB，按时间从旧到新继续删除直到达标
        val files = logDir.listFiles()?.sortedBy { it.name } ?: return
        var totalKB = files.sumOf { it.length() } / 1024
        for (file in files) {
            if (totalKB <= MAX_TOTAL_SIZE_KB) break
            totalKB -= file.length() / 1024
            file.delete()
        }
    }
}
```

### 4.4 DiagnosticCollector（诊断摘要收集）

```kotlin
class DiagnosticCollector @Inject constructor(
    private val context: Context,
    private val mushroomRepository: MushroomRepository,   // 获取蘑菇余额摘要
    private val taskRepository: TaskRepository             // 获取任务总数
) {
    suspend fun collect(): DiagnosticSummary {
        return DiagnosticSummary(
            appVersion = BuildConfig.VERSION_NAME,
            buildCode = BuildConfig.VERSION_CODE,
            dbVersion = getDatabaseVersion(),
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            availableStorageMB = getAvailableStorageMB(),
            totalTasks = taskRepository.getTotalCount(),
            completedTasks = taskRepository.getCompletedCount(),
            mushroomBalance = mushroomRepository.getBalance().first(),
            lastError = getLastErrorLine()
        )
    }
}

data class DiagnosticSummary(
    val appVersion: String,
    val buildCode: Int,
    val dbVersion: Int,
    val androidVersion: String,
    val apiLevel: Int,
    val deviceModel: String,
    val availableStorageMB: Long,
    val totalTasks: Int,
    val completedTasks: Int,
    val mushroomBalance: MushroomBalance,
    val lastError: String?
)
```

### 4.5 LogExporter（日志打包导出）

```kotlin
class LogExporter @Inject constructor(
    private val context: Context,
    private val diagnosticCollector: DiagnosticCollector,
    private val fileWriter: LogFileWriter
) {
    // 打包近2天日志 + 诊断摘要为 ZIP，返回可分享的 FileProvider URI
    suspend fun export(): Result<Uri> = runCatching {
        val summary = diagnosticCollector.collect()
        val zipFile = createZip(summary)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
    }.onFailure { e ->
        MushroomLogger.e(TAG, "日志导出失败", e)
    }

    private suspend fun createZip(summary: DiagnosticSummary): File {
        val zipName = "mushroom_diagnostics_${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.zip"
        val zipFile = File(context.cacheDir, zipName)
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            // 写入诊断摘要
            zip.putNextEntry(ZipEntry("diagnostic_summary.txt"))
            zip.write(summary.toReadableString().toByteArray())
            // 写入日志文件
            logDir.listFiles()?.forEach { logFile ->
                zip.putNextEntry(ZipEntry("logs/${logFile.name}"))
                logFile.inputStream().copyTo(zip)
            }
        }
        return zipFile
    }

    companion object { private const val TAG = "LogExporter" }
}
```

---

## 五、Tag 命名规范

各模块使用固定 Tag 标识，便于日志过滤定界：

| 模块 | Tag 常量 | 示例 |
|-----|---------|------|
| core-data | `"DB"` | `[DB] Migration 1→2 完成` |
| feature-task | `"TASK"` | `[TASK] 任务创建 id=42` |
| feature-checkin | `"CHECKIN"` | `[CHECKIN] 打卡完成 taskId=42 isEarly=true` |
| feature-mushroom | `"MUSHROOM"` | `[MUSHROOM] 发放 SMALL×1 balance={SMALL:12}` |
| feature-reward | `"REWARD"` | `[REWARD] 拼图更新 8/20块` |
| feature-milestone | `"MILESTONE"` | `[MILESTONE] 成绩录入 score=92` |
| feature-statistics | `"STATS"` | `[STATS] 统计刷新 period=30d` |
| service-notification | `"NOTIF"` | `[NOTIF] 提醒已调度 taskId=42` |
| LogExporter | `"LOG_EXPORT"` | `[LOG_EXPORT] 导出完成 size=128KB` |

每个模块在伴生对象中定义：
```kotlin
companion object {
    private const val TAG = "CHECKIN"
}
// 使用时：MushroomLogger.i(TAG, "打卡完成 taskId=$taskId")
```

---

## 六、日志输出格式

```
{timestamp} {level}/{tag}: {message}
{optional stacktrace}
```

实际示例：
```
2026-03-01 08:32:11.234 I/CHECKIN: 打卡完成 taskId=42 isEarly=true earlyMin=35
2026-03-01 08:32:11.267 I/MUSHROOM: 发放蘑菇 SMALL×1 source=TASK sourceId=42 newBalance={SMALL:12,MEDIUM:3}
2026-03-01 08:32:11.890 W/DB: 重复打卡请求已忽略 taskId=42 date=2026-03-01
2026-03-01 08:32:55.123 E/CHECKIN: 打卡失败 taskId=99
    java.lang.IllegalStateException: Task not found id=99
        at com.mushroom.data.repository.TaskRepositoryImpl.getById(TaskRepositoryImpl.kt:58)
        at com.mushroom.feature.checkin.usecase.CheckInTaskUseCase.invoke(CheckInTaskUseCase.kt:32)
```

---

## 七、各模块日志接入规范

### 7.1 Use Case 统一异常处理模板

```kotlin
class SomeUseCase(...) {
    suspend operator fun invoke(param: Type): Result<Output> = runCatching {
        // 业务逻辑（内部步骤使用 DEBUG，不用 INFO）
        val result = doSomething(param)
        MushroomLogger.i(TAG, "执行完成 result=$result")
        result
    }.onFailure { e ->
        MushroomLogger.e(TAG, "执行失败 param=$param", e)
    }
    companion object { private const val TAG = "MODULE_NAME" }
}
```

### 7.2 Repository 实现异常处理模板

```kotlin
class SomeRepositoryImpl @Inject constructor(private val dao: SomeDao) : SomeRepository {
    override suspend fun insert(entity: Entity): Long {
        return runCatching {
            dao.insert(entity.toDbEntity()).also { id ->
                MushroomLogger.d(TAG, "插入成功 id=$id")
            }
        }.getOrElse { e ->
            MushroomLogger.e(TAG, "数据库插入失败 entity=$entity", e)
            throw e  // Repository 层重新抛出，由 Use Case 统一处理
        }
    }
    companion object { private const val TAG = "DB" }
}
```

### 7.3 关键业务节点 INFO 日志要求

以下场景**必须**输出 INFO 级别日志：
- 任务创建 / 删除
- 用户打卡（含提前完成标识）
- 蘑菇发放 / 扣除 / 消耗（含数量和操作后余额）
- 拼图进度变更
- 里程碑成绩录入
- 数据库 Migration 执行
- 日志导出操作

---

## 八、Settings 页面集成

在 `SettingsScreen` 中新增"诊断与帮助"入口：

```
设置
└── 诊断与帮助
    ├── 导出日志          → 调用 LogExporter.export()，弹出系统分享对话框
    ├── 查看诊断摘要      → 展示 DiagnosticSummary 的可读格式
    └── 清除日志文件      → 手动清理所有日志文件（释放存储空间）
```

---

## 九、Hilt 依赖注入配置

```kotlin
// core-logging/src/main/java/com/mushroom/logging/di/LoggingModule.kt

@Module
@InstallIn(SingletonComponent::class)
object LoggingModule {

    @Provides
    @Singleton
    fun provideLogFileWriter(@ApplicationContext context: Context): LogFileWriter =
        LogFileWriter(context)

    @Provides
    @Singleton
    fun provideLogWriter(fileWriter: LogFileWriter): LogWriter =
        if (BuildConfig.DEBUG) DebugLogWriter(fileWriter)
        else ReleaseLogWriter(fileWriter)
}

// Application.onCreate 中初始化
class MushroomApp : Application() {
    @Inject lateinit var logWriter: LogWriter
    @Inject lateinit var logFileWriter: LogFileWriter

    override fun onCreate() {
        super.onCreate()
        MushroomLogger.init(logWriter)
        MushroomLogger.i("APP", "应用启动 version=${BuildConfig.VERSION_NAME}")
        // 清理过期日志
        logFileWriter.purgeAndRotate()
    }
}
```

---

## 十、包结构

```
core-logging/src/main/java/com/mushroom/logging/
├── MushroomLogger.kt          # 日志门面（全局单例）
├── LogLevel.kt                # 日志级别枚举
├── LogWriter.kt               # 输出策略接口 + Debug/Release 实现
├── LogFileWriter.kt           # 滚动文件写入
├── DiagnosticCollector.kt     # 诊断摘要收集
├── LogExporter.kt             # 日志打包导出
└── di/
    └── LoggingModule.kt       # Hilt Module
```

---

*文档结束*
