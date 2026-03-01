# 蘑菇大冒险 - core-logging 模块详细设计

**版本**：v1.2
**日期**：2026-03-01
**状态**：待审核

**变更记录**：
| 版本 | 日期 | 变更内容 |
|-----|------|---------|
| v1.0 | 2026-03-01 | 初版 |
| v1.1 | 2026-03-01 | Release 版本仅输出 ERROR；INFO 设计原则化（只记录模块边界事件、事件数量上限、清单约束）；日志文件总上限 512KB、保留 2 天 |
| v1.2 | 2026-03-01 | 导出包新增 Claude 分析入口文档和错误索引，日志文件加入 Session 分隔标记，面向 Claude Code CLI 直接分析设计 |

---

## 一、模块职责与边界

### 职责
- 提供全应用统一的日志输出门面（`MushroomLogger`），屏蔽底层实现
- 按构建类型（Debug / Release）控制日志级别和输出目标
- 将 WARN 及以上级别日志滚动写入本地文件，支持近2天留存（Debug 构建含 INFO）
- 收集设备和应用诊断摘要，辅助问题复现
- 提供日志导出功能（ZIP 打包，系统分享），导出包设计为可直接输入 Claude Code CLI 进行自动分析

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
    // 导出包结构设计为可直接输入 Claude Code CLI 进行自动分析
    suspend fun export(): Result<Uri> = runCatching {
        val summary = diagnosticCollector.collect()
        val zipFile = createZip(summary)
        MushroomLogger.i(TAG, "日志导出完成 size=${zipFile.length() / 1024}KB")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
    }.onFailure { e ->
        MushroomLogger.e(TAG, "日志导出失败", e)
    }

    private suspend fun createZip(summary: DiagnosticSummary): File {
        val zipName = "mushroom_diagnostics_${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.zip"
        val zipFile = File(context.cacheDir, zipName)
        val logFiles = logDir.listFiles()?.sortedBy { it.name } ?: emptyList()
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            // 1. 分析入口文档（Claude 首先读取此文件）
            zip.putNextEntry(ZipEntry("CLAUDE_ANALYSIS_BRIEF.md"))
            zip.write(buildAnalysisBrief(summary, logFiles).toByteArray())
            // 2. 诊断摘要
            zip.putNextEntry(ZipEntry("diagnostic_summary.txt"))
            zip.write(summary.toReadableString().toByteArray())
            // 3. 错误索引（预提取所有 ERROR/WARN 行）
            zip.putNextEntry(ZipEntry("error_index.txt"))
            zip.write(buildErrorIndex(logFiles).toByteArray())
            // 4. 原始日志文件
            logFiles.forEach { logFile ->
                zip.putNextEntry(ZipEntry("logs/${logFile.name}"))
                logFile.inputStream().copyTo(zip)
            }
        }
        return zipFile
    }

    private fun buildAnalysisBrief(summary: DiagnosticSummary, logFiles: List<File>): String = """
        # 蘑菇大冒险 - 诊断日志分析入口

        ## 如何分析本包

        本 ZIP 包专为输入 Claude Code CLI 设计。建议按以下顺序读取文件：

        1. **此文件（CLAUDE_ANALYSIS_BRIEF.md）** — 了解整体结构和分析策略
        2. **error_index.txt** — 查看所有 ERROR/WARN 的预提取索引，快速定位问题
        3. **diagnostic_summary.txt** — 查看设备环境和应用状态
        4. **logs/*.txt** — 按需查阅原始日志上下文

        ## 应用信息

        - 应用版本：${summary.appVersion} (build ${summary.buildCode})
        - 数据库版本：${summary.dbVersion}
        - 设备：${summary.deviceModel} / Android ${summary.androidVersion} (API ${summary.apiLevel})
        - 可用存储：${summary.availableStorageMB} MB
        - 导出时间：${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}

        ## 日志文件列表

        ${logFiles.joinToString("\n") { "- logs/${it.name}（${it.length() / 1024} KB）" }.ifEmpty { "- （无日志文件）" }}

        ## 日志格式说明

        ```
        {yyyy-MM-dd HH:mm:ss.SSS} {LEVEL}/{TAG}: {message}
        ```

        级别：V（Verbose）< D（Debug）< I（Info）< W（Warn）< E（Error）

        ## Tag 索引（按模块）

        | Tag | 模块 | 关注内容 |
        |-----|------|---------|
        | APP | 应用启动 | 版本号、启动成功与否 |
        | DB | core-data | Migration、数据库操作异常 |
        | TASK | feature-task | 任务创建、删除 |
        | CHECKIN | feature-checkin | 打卡触发、完成、提前标识 |
        | MUSHROOM | feature-mushroom | 蘑菇发放、扣除、余额变化 |
        | REWARD | feature-reward | 拼图解锁进度 |
        | MILESTONE | feature-milestone | 成绩录入、里程碑达成 |
        | STATS | feature-statistics | 统计数据刷新 |
        | NOTIF | service-notification | 提醒调度 |
        | LOG_EXPORT | core-logging | 日志导出 |

        ## Session 分隔

        每次应用启动在日志中写入分隔标记：
        ```
        ════════════════════════════════════════════════════════════
        SESSION START  ${summary.appVersion}  {timestamp}
        ════════════════════════════════════════════════════════════
        ```
        通过搜索 `SESSION START` 可区分不同启动周期。

        ## 推荐分析策略

        - **定位崩溃**：查看 error_index.txt，找最近的 E/ 行，再到原始日志查看前后上下文
        - **还原操作流程**：在日志中搜索 `SESSION START`，找到问题所在的启动周期，按时间线阅读 I/ 行
        - **蘑菇数据异常**：搜索 `MUSHROOM` tag，关注 balance 字段变化是否合理
        - **数据库问题**：搜索 `DB` tag，关注 Migration 和 ERROR 行
    """.trimIndent()

    private fun buildErrorIndex(logFiles: List<File>): String {
        val sb = StringBuilder()
        sb.appendLine("# 错误与警告索引")
        sb.appendLine("# 格式：[文件名:行号] LEVEL/TAG: message")
        sb.appendLine("# 生成时间：${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
        sb.appendLine()
        var hasAny = false
        logFiles.forEach { file ->
            file.useLines { lines ->
                lines.forEachIndexed { index, line ->
                    if (line.contains(" E/") || line.contains(" W/")) {
                        sb.appendLine("[${file.name}:${index + 1}] $line")
                        hasAny = true
                    }
                }
            }
        }
        if (!hasAny) sb.appendLine("（无 ERROR 或 WARN 记录）")
        return sb.toString()
    }

    companion object { private const val TAG = "LOG_EXPORT" }
}
```

---

## 五、面向 Claude 分析的日志格式约定

### 5.1 Session 分隔标记

每次应用启动时，在写入第一条日志前先写入 Session 分隔线，使 Claude 能将日志按启动周期切割分析：

```kotlin
// Application.onCreate 中，init 之后立即写入
private fun writeSessionStart(fileWriter: LogFileWriter, version: String) {
    val sep = "═".repeat(60)
    val ts  = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    fileWriter.write(sep)
    fileWriter.write("SESSION START  $version  $ts")
    fileWriter.write(sep)
}
```

实际日志效果：
```
════════════════════════════════════════════════════════════
SESSION START  1.2.0  2026-03-01 08:30:00
════════════════════════════════════════════════════════════
2026-03-01 08:30:00.123 I/APP: 应用启动 version=1.2.0
2026-03-01 08:30:00.456 I/DB: Migration 2→3 完成
...
════════════════════════════════════════════════════════════
SESSION START  1.2.0  2026-03-01 18:45:12
════════════════════════════════════════════════════════════
2026-03-01 18:45:12.001 I/APP: 应用启动 version=1.2.0
```

### 5.2 导出 ZIP 包结构

```
mushroom_diagnostics_20260301.zip
├── CLAUDE_ANALYSIS_BRIEF.md    ← Claude 首先读取，含分析策略和 Tag 索引
├── diagnostic_summary.txt      ← 设备信息、应用状态摘要
├── error_index.txt             ← 所有 ERROR/WARN 行预提取索引（含文件名和行号）
└── logs/
    ├── mushroom_log_20260228.txt
    └── mushroom_log_20260301.txt
```

### 5.3 用户使用方式

用户将 ZIP 包解压后，在终端执行：

```bash
claude "请分析 CLAUDE_ANALYSIS_BRIEF.md 和 error_index.txt，告诉我应用发生了什么问题"
```

Claude Code CLI 读取 `CLAUDE_ANALYSIS_BRIEF.md` 后，即可了解包结构、Tag 含义和分析策略，无需用户额外解释，直接给出问题定位结论。

---

## 六、Tag 命名规范

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

## 七、日志输出格式

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

## 八、各模块日志接入规范

### 8.1 Use Case 统一异常处理模板

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

### 8.2 Repository 实现异常处理模板

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

### 8.3 关键业务节点 INFO 日志要求

以下场景**必须**输出 INFO 级别日志：
- 任务创建 / 删除
- 用户打卡（含提前完成标识）
- 蘑菇发放 / 扣除 / 消耗（含数量和操作后余额）
- 拼图进度变更
- 里程碑成绩录入
- 数据库 Migration 执行
- 日志导出操作

---

## 九、Settings 页面集成

在 `SettingsScreen` 中新增"诊断与帮助"入口：

```
设置
└── 诊断与帮助
    ├── 导出日志          → 调用 LogExporter.export()，弹出系统分享对话框
    ├── 查看诊断摘要      → 展示 DiagnosticSummary 的可读格式
    └── 清除日志文件      → 手动清理所有日志文件（释放存储空间）
```

---

## 十、Hilt 依赖注入配置

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
        // 先写 Session 分隔标记，再写启动日志，便于 Claude 按启动周期切割分析
        writeSessionStart(logFileWriter, BuildConfig.VERSION_NAME)
        MushroomLogger.i("APP", "应用启动 version=${BuildConfig.VERSION_NAME} db=${getDatabaseVersion()}")
        // 清理过期日志
        logFileWriter.purgeAndRotate()
    }

    private fun writeSessionStart(fileWriter: LogFileWriter, version: String) {
        val sep = "═".repeat(60)
        val ts  = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        fileWriter.write(sep)
        fileWriter.write("SESSION START  $version  $ts")
        fileWriter.write(sep)
    }
}
```

---

## 十一、包结构

```
core-logging/src/main/java/com/mushroom/logging/
├── MushroomLogger.kt          # 日志门面（全局单例）
├── LogLevel.kt                # 日志级别枚举
├── LogWriter.kt               # 输出策略接口 + Debug/Release 实现
├── LogFileWriter.kt           # 滚动文件写入 + Session 分隔标记
├── DiagnosticCollector.kt     # 诊断摘要收集
├── LogExporter.kt             # 日志打包导出（含 CLAUDE_ANALYSIS_BRIEF 生成）
└── di/
    └── LoggingModule.kt       # Hilt Module
```

---

*文档结束*
