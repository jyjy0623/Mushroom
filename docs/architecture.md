# 蘑菇大冒险 - 学习激励成长应用
## 总体架构设计文档

版本 v2.1，日期 2026-03-01，待审核

变更记录：
| 版本 | 日期 | 变更内容 |
|-----|------|---------|
| v1.0 | 2026-03-01 | 初版 |
| v1.1 | 2026-03-01 | 同步需求v1.1~v1.3 |
| v2.0 | 2026-03-01 | 重构为总体架构文档+模块分离 |
| v2.1 | 2026-03-01 | 新增设计原则：数据版本兼容、DFX可维护性、分级日志 |

---

## 一、架构设计原则

### 原则1：Clean Architecture 三层架构

```
┌─────────────────────────────────┐
│         Presentation Layer       │  ← Compose UI + ViewModel
├─────────────────────────────────┤
│           Domain Layer           │  ← 实体、Repository接口、服务接口、用例
├─────────────────────────────────┤
│            Data Layer            │  ← Room DAO、Repository实现、数据源
└─────────────────────────────────┘
```

- UI 层不直接访问数据库
- Domain 层不依赖任何框架
- 依赖方向：UI → Domain ← Data

### 原则2：面向接口编程，为扩展开放

所有服务通过接口定义，由 Hilt 注入具体实现，扩展时只需新增实现类，不修改调用方。

```kotlin
// 示例：任务生成服务接口，V1手动实现，V2替换为AI实现
interface TaskGeneratorService {
    suspend fun generate(input: TaskGeneratorInput): Result<List<TaskTemplate>>
}

// V1 实现
class ManualTaskGeneratorService @Inject constructor() : TaskGeneratorService {
    override suspend fun generate(input: TaskGeneratorInput) = Result.success(emptyList<TaskTemplate>())
}

// V2 实现（不修改接口，不修改调用方）
class LLMTaskGeneratorService @Inject constructor(
    private val llmClient: LLMClient
) : TaskGeneratorService {
    override suspend fun generate(input: TaskGeneratorInput): Result<List<TaskTemplate>> {
        return llmClient.generateTasks(input)
    }
}
```

### 原则3：单一数据源

- 每类数据有且仅有一个来源（Room 数据库）
- ViewModel 通过 Repository 获取数据，不缓存副本
- UI 状态由 Flow 驱动，始终与数据库保持同步

### 原则4：响应式数据流 Kotlin Coroutines + Flow

- 所有列表和余额数据以 `Flow<T>` 形式暴露
- ViewModel 使用 `stateIn` 将 Flow 转为 `StateFlow<UiState>`
- 数据库写操作使用 `suspend fun`，在 IO 调度器执行
- UI 层使用 `collectAsStateWithLifecycle()` 收集状态

### 原则5：模块化设计

- 按功能拆分为独立 Gradle 模块（feature-*）
- 模块间通过接口和事件总线通信，不直接依赖
- 每个模块可独立编译、测试、替换

### 原则6：离线优先

- 所有数据优先存储在本地 Room 数据库
- 无网络时功能完整可用
- V2 引入云同步时，本地数据优先，后台同步

### 原则7：可配置性优先于硬编码

- 蘑菇等级配置、扣分规则、奖励规则均存储在数据库，支持运行时修改
- 预设模板作为初始数据（Seed Data）写入，用户可覆盖
- 不在代码中硬编码奖励数量、分数阈值等业务参数

---

### 原则8：数据版本兼容与升级安全

**目标**：每次版本升级新增功能时，不破坏用户已有数据，确保从任意旧版本升级后数据完整、功能正常。

#### 8.1 数据库 Migration 策略

采用 Room Migration 机制管理数据库版本演进，**禁止**使用 `fallbackToDestructiveMigration()`（该方式会清空用户数据）。

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 只允许增量操作：ADD COLUMN、CREATE TABLE、CREATE INDEX
        // 禁止：DROP TABLE、DROP COLUMN、修改已有列类型
        database.execSQL(
            "ALTER TABLE tasks ADD COLUMN template_type TEXT DEFAULT NULL"
        )
    }
}

val db = Room.databaseBuilder(context, MushroomDatabase::class.java, "mushroom_database")
    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)  // 逐版本注册
    .build()
```

#### 8.2 字段扩展规则

| 变更类型 | 允许 | 处理方式 |
|---------|------|---------|
| 新增表 | ✅ | `CREATE TABLE` in Migration |
| 新增可空字段 | ✅ | `ALTER TABLE ADD COLUMN ... DEFAULT NULL` |
| 新增带默认值字段 | ✅ | `ALTER TABLE ADD COLUMN ... DEFAULT <value>` |
| 删除字段 | ❌ | 保留字段，标记废弃（加 `_deprecated` 后缀注释） |
| 删除表 | ❌ | 保留表结构，业务层停止读写 |
| 修改字段类型 | ❌ | 新增字段存新值，旧字段保留 |
| 重命名字段/表 | ❌ | 新增字段/表，迁移数据，保留旧结构 |

#### 8.3 配置数据的向前兼容

系统预设数据（蘑菇等级配置、预设扣分项、预设模板）采用**幂等 Upsert** 策略：

```kotlin
// 每次应用启动时执行，OnConflictStrategy.IGNORE 保证不覆盖用户已修改的配置
@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun insertIfNotExists(config: MushroomConfigEntity)
```

新版本新增的预设配置项，通过版本号标记控制只插入一次：

```kotlin
// app_metadata 表记录已执行的初始化版本
if (appMetadata.lastSeedVersion < CURRENT_SEED_VERSION) {
    seedNewConfigs()
    appMetadata.lastSeedVersion = CURRENT_SEED_VERSION
}
```

#### 8.4 数据备份与恢复

- 支持将数据库导出为 JSON 文件（人工可读格式），用于跨设备迁移和紧急数据恢复
- 导出格式按 Domain Entity 序列化，不依赖数据库内部结构，具备跨版本兼容性
- 导入时执行版本校验，字段缺失时使用默认值填充，多余字段忽略

```kotlin
data class BackupPayload(
    val exportVersion: Int,          // 导出格式版本，独立于 DB 版本
    val exportedAt: String,
    val tasks: List<Task>,
    val mushrooms: List<MushroomTransaction>,
    val rewards: List<Reward>,
    val milestones: List<Milestone>
    // 新版本新增字段加在末尾，旧版导入时自动忽略
)
```

---

### 原则9：DFX——可维护性与快速问题定界

**目标**：应用发生异常时，能够通过日志和诊断信息快速定界（发生在哪个模块）、定位（发生在哪行代码），减少问题排查时间。

#### 9.1 整体 DFX 架构

```
用户反馈问题
    │
    ▼
用户在 APP 内导出日志文件（Settings → 诊断 → 导出日志）
    │
    ▼
日志文件（mushroom_log_yyyyMMdd.txt）+ 设备信息摘要
    │
    ▼
开发者分析：
    ├── 按模块 Tag 过滤日志（快速定界）
    ├── 查看 ERROR 级别栈跟踪（定位代码行）
    └── 结合操作时间线还原现场
```

#### 9.2 模块健康状态监控

每个 feature 模块在关键节点输出 INFO 级别日志，标识正常工作状态，便于通过日志时间线还原用户操作路径：

| 模块 | 关键 INFO 日志示例 |
|-----|-----------------|
| feature-task | `[TASK] 任务创建成功 id=42 title=数学作业 date=2026-03-01` |
| feature-checkin | `[CHECKIN] 打卡完成 taskId=42 isEarly=true earlyMin=35` |
| feature-mushroom | `[MUSHROOM] 发放蘑菇 SMALL×1 source=TASK sourceId=42 balance={SMALL:12}` |
| feature-reward | `[REWARD] 拼图更新 rewardId=3 unlocked=8/20 (40%)` |
| feature-milestone | `[MILESTONE] 成绩录入 id=5 score=92 reward=GOLD×2` |
| core-data | `[DB] Migration 1→2 执行完成` |

#### 9.3 异常快速定位

所有 Use Case 和 Repository 实现统一捕获异常，输出 ERROR 级别日志（含完整栈跟踪），并将错误状态传递给 ViewModel 展示给用户：

```kotlin
// Use Case 统一异常处理模板
class CheckInTaskUseCase(...) {
    suspend operator fun invoke(taskId: Long): Result<CheckIn> = runCatching {
        // 业务逻辑
    }.onFailure { e ->
        MushroomLogger.e(TAG, "打卡失败 taskId=$taskId", e)  // ERROR + 栈跟踪
    }
    companion object { private const val TAG = "CheckInTaskUseCase" }
}
```

#### 9.4 诊断信息收集

导出日志时附带设备诊断摘要，辅助问题复现：

```
=== 诊断摘要 ===
APP 版本：1.2.0 (build 45)
数据库版本：3
Android 版本：14 (API 34)
设备型号：Xiaomi 14
可用存储：12.3 GB
任务总数：128 / 已完成：95
蘑菇余额：小蘑菇×23 中蘑菇×5 大蘑菇×1
最近错误：[ERROR] 2026-03-01 08:32:11 CheckInTaskUseCase - ...
```

---

### 原则10：分级日志设计

**目标**：通过统一的日志模块（`core-logging`），输出精简、有价值的日志信息。Release 版本日常运行只记录 ERROR，对用户设备性能和存储影响最小；Debug 版本开放完整日志辅助开发。出现问题时，ERROR 日志结合诊断摘要足以快速定界定位。

#### 10.1 日志级别定义与输出策略

| 级别 | 用途 | Debug 构建 | Release 构建 |
|-----|------|-----------|------------|
| `VERBOSE` | 详细调试（Flow emit、UI 重组） | Logcat | 不输出 |
| `DEBUG` | 开发调试（入参、中间计算结果） | Logcat | 不输出 |
| `INFO` | **各模块关键流程节点**（见 10.2） | Logcat + 文件 | **不输出** |
| `WARN` | 非预期但可恢复的情况 | Logcat + 文件 | 文件 |
| `ERROR` | 异常和错误（含完整栈跟踪） | Logcat + 文件 | **Logcat + 文件** |

> **Release 版本只记录 ERROR 和 WARN**，INFO 及以下全部关闭。ERROR 日志含完整栈跟踪，是线上问题定位的唯一手段。

#### 10.2 INFO 日志设计原则

INFO 日志的核心价值是**在 Debug 阶段通过日志时间线还原各模块的关键流程**，不是记录所有操作。遵循以下原则：

**原则一：只记录模块边界事件，不记录内部步骤**

每个模块只在以下两类位置输出 INFO：
- **流程入口**：Use Case 被调用，表明某项用户行为触发了该模块
- **流程结果**：Use Case 执行完成，表明该模块的关键状态发生了变化

中间步骤（数据库读取、数据转换、规则计算过程）使用 DEBUG 级别，不用 INFO。

```kotlin
// ✅ 正确：只在入口和结果输出 INFO
class CheckInTaskUseCase(...) {
    suspend operator fun invoke(taskId: Long): Result<CheckIn> = runCatching {
        MushroomLogger.i(TAG, "打卡 taskId=$taskId")        // 入口：用户行为触发
        val checkIn = doCheckIn(taskId)
        MushroomLogger.i(TAG, "打卡完成 isEarly=${checkIn.isEarly} earlyMin=${checkIn.earlyMinutes}")  // 结果：状态变化
        checkIn
    }.onFailure { e ->
        MushroomLogger.e(TAG, "打卡失败 taskId=$taskId", e)
    }
}

// ❌ 错误：中间步骤不使用 INFO
class CheckInTaskUseCase(...) {
    suspend operator fun invoke(taskId: Long): Result<CheckIn> = runCatching {
        MushroomLogger.i(TAG, "开始查询任务")               // ❌ 内部步骤不用 INFO
        val task = taskRepository.getById(taskId)
        MushroomLogger.i(TAG, "计算是否提前完成")           // ❌ 内部步骤不用 INFO
        ...
    }
}
```

**原则二：每个模块的 INFO 事件数量有上限**

每个 feature 模块**至多**定义以下数量的 INFO 事件，超出则降为 DEBUG：

| 模块规模 | INFO 事件上限 |
|---------|------------|
| 核心模块（task、checkin、mushroom） | 每模块 ≤ 5 个 |
| 普通模块（reward、milestone、statistics） | 每模块 ≤ 3 个 |
| 基础设施（core-data、core-logging） | ≤ 2 个（Migration、启动） |

**原则三：INFO 事件清单需在模块设计文档中明确定义**

每个模块的详细设计文档中必须列出该模块允许的全部 INFO 事件，形成约束清单。未在清单中的场景一律使用 DEBUG，禁止随意新增 INFO 日志。

各模块允许的 INFO 事件（完整清单）：

| 模块 | 允许的 INFO 事件 | 日志示例 |
|-----|---------------|---------|
| APP启动 | 应用启动（1个） | `[APP] 启动 v1.2.0 db=3` |
| core-data | DB Migration 完成（1个） | `[DB] Migration 1→2 完成` |
| feature-task | 任务创建、任务删除（2个） | `[TASK] 创建 id=42 date=2026-03-01` |
| feature-checkin | 打卡触发、打卡完成（2个） | `[CHECKIN] 完成 taskId=42 isEarly=true earlyMin=35` |
| feature-mushroom | 蘑菇发放、蘑菇扣除（2个） | `[MUSHROOM] 发放 SMALL×1 src=TASK balance={S:12,M:3}` |
| feature-reward | 拼图解锁（1个） | `[REWARD] 解锁 rewardId=3 progress=8/20` |
| feature-milestone | 成绩录入（1个） | `[MILESTONE] 录入 id=5 score=92` |
| core-logging | 日志导出完成（1个） | `[LOG] 导出完成 size=128KB` |

#### 10.3 ERROR 日志规范

ERROR 是 Release 版本唯一的日志输出，必须包含足够的上下文：

```kotlin
// ERROR 必须包含：操作名称 + 关键参数 + 完整异常栈
MushroomLogger.e(TAG, "打卡失败 taskId=$taskId", exception)

// WARN 用于可恢复的异常情况（无需栈跟踪）
MushroomLogger.w(TAG, "余额不足，扣分降级 需要=$required 实际=$actual")
```

#### 10.4 日志文件管理策略

```
整体日志存储策略：
  总容量上限：512 KB（所有日志文件之和）
  保留时长：最近 2 天
  文件组织：每天一个文件（mushroom_log_20260301.txt）
  超出策略：总大小超过 512KB 时，优先删除最旧文件

日志写入时序：
  应用启动 → purgeOldFiles()（清理2天前文件）
           → checkTotalSize()（若总大小超512KB，删最旧文件至达标）
           → 正常写入
```

```kotlin
class LogFileWriter(private val context: Context) {
    private val logDir = File(context.filesDir, "logs")

    companion object {
        const val MAX_TOTAL_SIZE_KB = 512       // 所有日志文件总上限
        const val MAX_RETAIN_DAYS  = 2          // 保留最近2天
    }

    fun purgeAndRotate() {
        val cutoff = LocalDate.now().minusDays(MAX_RETAIN_DAYS.toLong())
        // 1. 删除2天前的文件
        logDir.listFiles()?.filter { parseFileDate(it.name)?.isBefore(cutoff) == true }
              ?.forEach { it.delete() }
        // 2. 若总大小仍超512KB，按时间从旧到新继续删除，直到达标
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

#### 10.5 统一日志接口

```kotlin
object MushroomLogger {
    fun v(tag: String, message: String)                              // Debug 构建 Logcat
    fun d(tag: String, message: String)                              // Debug 构建 Logcat
    fun i(tag: String, message: String)                              // Debug 构建 Logcat + 文件
    fun w(tag: String, message: String, throwable: Throwable? = null) // Debug + Release 文件
    fun e(tag: String, message: String, throwable: Throwable? = null) // Debug + Release Logcat + 文件
}
```

Release 构建的 `LogWriter` 实现中，INFO 调用直接返回（编译后开销接近于零）：

```kotlin
class ReleaseLogWriter(private val fileWriter: LogFileWriter) : LogWriter {
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        if (level < LogLevel.W) return   // VERBOSE / DEBUG / INFO 全部裁剪
        val formatted = formatMessage(level, tag, message, throwable)
        fileWriter.write(formatted)
        android.util.Log.println(level.androidPriority, tag, formatted)
    }
}
```

#### 10.3 日志输出策略

```
Debug 构建（开发阶段）：
    VERBOSE / DEBUG → Android Logcat（不写文件，不影响性能）
    INFO / WARN / ERROR → Logcat + 滚动文件

Release 构建（用户设备）：
    VERBOSE / DEBUG → 不输出（编译时裁剪）
    INFO → 滚动文件（仅记录，不输出 Logcat）
    WARN / ERROR → 滚动文件 + 结构化错误记录
```

#### 10.4 日志文件管理

```kotlin
// 文件存储位置：应用私有目录（用户无法直接访问，需通过APP导出）
// Context.getFilesDir() / logs / mushroom_log_20260301.txt

// 滚动策略
LogFilePolicy(
    maxFileSizeKB = 512,        // 单文件最大 512KB
    maxRetainDays = 7,          // 保留最近7天
    rotateAtMidnight = true     // 每天0点新建日志文件
)
```

日志格式（结构化，便于解析）：
```
2026-03-01 08:32:11.234 I/CHECKIN: 打卡完成 taskId=42 isEarly=true earlyMin=35
2026-03-01 08:32:11.267 I/MUSHROOM: 发放蘑菇 SMALL×1 source=TASK sourceId=42
2026-03-01 08:32:11.891 E/DB: 数据库写入失败 table=check_ins
    java.io.IOException: disk full
        at com.mushroom.data.repository.CheckInRepositoryImpl.recordCheckIn(CheckInRepositoryImpl.kt:45)
        at com.mushroom.feature.checkin.usecase.CheckInTaskUseCase.invoke(CheckInTaskUseCase.kt:32)
```

#### 10.5 日志导出流程

```
Settings → 诊断与帮助 → 导出日志
    │
    ▼
收集近7天日志文件 + 生成诊断摘要
    │
    ▼
打包为 ZIP（mushroom_diagnostics_20260301.zip）
    │
    ▼
调用系统分享对话框（发邮件/微信/钉钉给开发者）
```

#### 10.6 core-logging 模块在 Gradle 中的位置

```
mushroom-app/
├── core/
│   ├── core-logging/     # 新增：统一日志模块
│   │   ├── MushroomLogger（日志门面）
│   │   ├── LogFileWriter（文件滚动写入）
│   │   ├── DiagnosticCollector（诊断摘要收集）
│   │   └── LogExporter（日志打包导出）
│   ├── core-ui/
│   ├── core-data/
│   └── core-domain/
```

> 所有模块（core-data、feature-* 等）均依赖 `core-logging`，不依赖 Android 的 `Log` 类，确保日志策略统一可控。

## 二、技术选型

### 核心技术栈

| 类别 | 技术 | 版本/说明 |
|------|------|---------|
| 开发语言 | Kotlin | 2.0+ |
| UI 框架 | Jetpack Compose | Material3 |
| 架构模式 | MVVM + Clean Architecture | ViewModel + StateFlow |
| 依赖注入 | Hilt | Dagger Hilt |
| 数据库 | Room | SQLite 封装 |
| 异步处理 | Kotlin Coroutines + Flow | IO/Main 调度器 |
| 图片加载 | Coil | Compose 扩展 |
| 图表 | Vico | Compose 图表库 |
| 导航 | Navigation Compose | 单 Activity 架构 |
| 序列化 | Kotlin Serialization | JSON 数据传递 |
| 单元测试 | JUnit5 + Turbine | Flow 测试 |

### V2 扩展技术预留

| 类别 | 技术 | 用途 |
|------|------|------|
| 网络 | Retrofit + OkHttp | 云同步、LLM API |
| 语音识别 | Android SpeechRecognizer / 第三方 | 语音输入任务 |
| 云端 | Firebase / 自建后端 | 数据备份同步 |

---

## 三、Gradle 模块划分

```
mushroom-app/
├── app/                          # 应用入口、DI装配、NavGraph
├── core/
│   ├── core-logging/             # 统一日志（MushroomLogger、文件滚动、日志导出）
│   ├── core-ui/                  # 公共UI组件（蘑菇图标、拼图组件、通用动画）
│   ├── core-data/                # 数据库基础设施（Room、DAO基类、Mapper基类）
│   └── core-domain/              # 共享实体、Repository接口、服务接口定义
├── feature/
│   ├── feature-task/             # 任务管理（任务CRUD、模板管理）
│   ├── feature-checkin/          # 打卡记录（每日打卡、历史、streak）
│   ├── feature-mushroom/         # 蘑菇体系（账本、余额、扣分、奖励引擎）
│   ├── feature-reward/           # 奖品兑换（拼图系统、时长型奖品）
│   ├── feature-milestone/        # 里程碑（成绩录入、分数段奖励）
│   └── feature-statistics/       # 数据统计（图表、趋势分析）
└── service/
    ├── service-task-generator/   # 任务生成（V1手动/V2 AI）
    └── service-notification/     # 通知服务
```

### 模块依赖规则

- **所有模块**均依赖 `core-logging`，通过 `MushroomLogger` 统一输出日志，不直接调用 `android.util.Log`
- feature 模块只依赖 core-domain，不互相依赖
- core-data 实现 core-domain 中的 Repository 接口
- app 模块负责依赖注入的装配（Hilt Module绑定）
- service 模块被 feature 模块按需依赖

---

## 四、模块清单与对应详细设计文档索引

| 模块 | 职责说明 | 详细设计文档 |
|------|---------|------------|
| core-logging | 统一日志门面、文件滚动写入、日志导出 | [模块设计/00-core-logging.md] |
| core-domain | 所有共享实体、Repository接口、服务接口 | [模块设计/01-core-domain.md] |
| core-data | 数据库、DAO、Repository实现 | [模块设计/02-core-data.md] |
| core-ui | 公共UI组件库 | [模块设计/03-core-ui.md] |
| feature-task | 任务管理+模板 | [模块设计/04-feature-task.md] |
| feature-checkin | 打卡记录 | [模块设计/05-feature-checkin.md] |
| feature-mushroom | 蘑菇体系+扣分 | [模块设计/06-feature-mushroom.md] |
| feature-reward | 奖品兑换+拼图 | [模块设计/07-feature-reward.md] |
| feature-milestone | 里程碑 | [模块设计/08-feature-milestone.md] |
| feature-statistics | 数据统计 | [模块设计/09-feature-statistics.md] |

---

## 五、模块间接口定义（核心部分）

这是总体设计中最重要的章节，明确模块间的契约。

### 5.1 全局导航接口

```kotlin
// 各 feature 模块向 app 模块暴露的导航入口（NavigationDestination）
// feature 模块不互相调用，统一通过 app 层的 NavGraph 协调

sealed class AppDestination(val route: String) {
    object Home : AppDestination("home")
    object TaskList : AppDestination("task/list")
    object TaskEdit : AppDestination("task/edit?taskId={taskId}")
    object TaskTemplates : AppDestination("task/templates")
    object CheckInCalendar : AppDestination("checkin/calendar")
    object MushroomLedger : AppDestination("mushroom/ledger")
    object DeductionRecord : AppDestination("mushroom/deduction/record")
    object DeductionHistory : AppDestination("mushroom/deduction/history")
    object DeductionConfig : AppDestination("mushroom/deduction/config")
    object RewardList : AppDestination("reward/list")
    object RewardDetail : AppDestination("reward/detail/{rewardId}")
    object MilestoneList : AppDestination("milestone/list")
    object MilestoneEdit : AppDestination("milestone/edit?milestoneId={milestoneId}")
    object Statistics : AppDestination("statistics")
    object Settings : AppDestination("settings")
}
```

### 5.2 Repository 接口契约（core-domain 定义，core-data 实现）

```kotlin
// ---- 任务模块 ----
interface TaskRepository {
    fun getTasksByDate(date: LocalDate): Flow<List<Task>>
    suspend fun createTask(task: Task): Long
    suspend fun updateTask(task: Task)
    suspend fun updateTaskStatus(id: Long, status: TaskStatus)
    suspend fun deleteTask(id: Long)
    fun getBuiltInTemplates(): Flow<List<TaskTemplate>>
    fun getCustomTemplates(): Flow<List<TaskTemplate>>
    suspend fun saveTemplate(template: TaskTemplate): Long
    suspend fun deleteTemplate(id: Long)
}

// ---- 打卡模块 ----
interface CheckInRepository {
    suspend fun recordCheckIn(checkIn: CheckIn): Long
    fun getCheckInsByDateRange(start: LocalDate, end: LocalDate): Flow<List<CheckIn>>
    fun getCurrentStreak(): Flow<Int>
    fun getLongestStreak(): Flow<Int>
}

// ---- 蘑菇模块 ----
interface MushroomRepository {
    fun getBalance(): Flow<Map<MushroomLevel, Int>>
    fun getLedger(): Flow<List<MushroomTransaction>>
    suspend fun earn(transaction: MushroomTransaction)
    suspend fun deduct(transaction: MushroomTransaction): Result<Unit>
    suspend fun spend(transaction: MushroomTransaction): Result<Unit>
}

interface DeductionRepository {
    fun getConfigs(): Flow<List<DeductionConfig>>
    suspend fun updateConfig(config: DeductionConfig)
    fun getRecords(): Flow<List<DeductionRecord>>
    suspend fun addRecord(record: DeductionRecord): Long
    suspend fun updateAppealStatus(recordId: Long, status: AppealStatus, note: String?)
    fun getTodayCount(configId: Long): Flow<Int>
}

// ---- 奖品模块 ----
interface RewardRepository {
    fun getActiveRewards(): Flow<List<Reward>>
    fun getPuzzleProgress(rewardId: Long): Flow<PuzzleProgress>
    fun getTimeRewardBalance(rewardId: Long): Flow<TimeRewardBalance>
    suspend fun createReward(reward: Reward): Long
    suspend fun updateReward(reward: Reward)
    suspend fun recordExchange(exchange: RewardExchange)
    suspend fun claimReward(rewardId: Long)
}

// ---- 里程碑模块 ----
interface MilestoneRepository {
    fun getMilestones(): Flow<List<Milestone>>
    fun getMilestonesBySubject(subject: Subject): Flow<List<Milestone>>
    suspend fun createMilestone(milestone: Milestone): Long
    suspend fun recordScore(milestoneId: Long, score: Int)
    suspend fun deleteMilestone(id: Long)
}

// ---- 关键日期 ----
interface KeyDateRepository {
    fun getKeyDates(): Flow<List<KeyDate>>
    suspend fun createKeyDate(keyDate: KeyDate): Long
    suspend fun deleteKeyDate(id: Long)
}
```

### 5.3 服务接口契约（跨模块调用的服务）

```kotlin
// 奖励规则引擎（feature-mushroom 内实现，被 feature-checkin / feature-milestone 调用）
interface RewardRuleEngine {
    fun calculate(event: RewardEvent): List<MushroomReward>
}

// 扣分规则引擎（feature-mushroom 内实现）
interface DeductionRuleEngine {
    fun validate(event: DeductionEvent): DeductionValidationResult
}

// 任务生成服务（service-task-generator 实现，被 feature-task 调用）
interface TaskGeneratorService {
    suspend fun generate(input: TaskGeneratorInput): Result<List<TaskTemplate>>
}

// 通知服务（service-notification 实现，被多个 feature 模块调用）
interface NotificationService {
    fun scheduleReminder(task: Task)
    fun notifyRewardEarned(mushroom: Mushroom)
    fun notifyDeduction(record: DeductionRecord)
}

// 家长权限守卫（core-domain 定义，app 层实现，feature 层调用）
interface ParentGateway {
    suspend fun authenticate(): Result<Unit>
    fun isAuthenticated(): Boolean
    fun requireAuth(action: suspend () -> Unit)
}
```

### 5.4 跨模块事件流（Event Bus）

各模块通过 SharedFlow 发布事件，其他模块按需订阅，实现松耦合通知：

```kotlin
// 在 core-domain 中定义全局事件类型
sealed class AppEvent {
    data class TaskCheckedIn(val taskId: Long, val checkInTime: LocalDateTime, val isEarly: Boolean) : AppEvent()
    data class MilestoneScored(val milestoneId: Long, val score: Int) : AppEvent()
    data class MushroomEarned(val mushrooms: List<MushroomReward>) : AppEvent()
    data class MushroomDeducted(val record: DeductionRecord) : AppEvent()
    data class RewardUnlocked(val rewardId: Long) : AppEvent()
    data class KeyDateReached(val keyDateId: Long) : AppEvent()
}

// AppEventBus（core-domain 中定义接口）
interface AppEventBus {
    val events: SharedFlow<AppEvent>
    suspend fun emit(event: AppEvent)
}
```

事件流向示例：
- feature-checkin 打卡 → 发布 `TaskCheckedIn` → feature-mushroom 订阅并计算奖励
- feature-milestone 录入成绩 → 发布 `MilestoneScored` → feature-mushroom 订阅并发放蘑菇
- feature-mushroom 发放蘑菇 → 发布 `MushroomEarned` → feature-reward 订阅并检查拼图进度

---

## 六、数据库总览

### 6.1 数据库名称与版本

- 数据库名：`mushroom_database`
- 初始版本：1
- 迁移策略：Room Migration（追加字段时保留数据，禁止 fallbackToDestructiveMigration）

### 6.2 数据表归属

| 数据表 | 归属模块 | 说明 |
|-------|---------|------|
| tasks | feature-task | 任务表 |
| task_templates | feature-task | 任务模板表 |
| check_ins | feature-checkin | 打卡记录表 |
| mushroom_ledger | feature-mushroom | 蘑菇账本（统一收支记录） |
| mushroom_config | feature-mushroom | 蘑菇等级配置 |
| deduction_config | feature-mushroom | 扣分项配置 |
| deduction_records | feature-mushroom | 扣分记录 |
| rewards | feature-reward | 奖品表 |
| reward_exchanges | feature-reward | 兑换记录 |
| time_reward_usage | feature-reward | 时长型奖品额度追踪 |
| milestones | feature-milestone | 里程碑表 |
| scoring_rules | feature-milestone | 分数段奖励规则 |
| key_dates | feature-mushroom | 关键奖励日期 |

### 6.3 跨模块数据访问原则

- 每个 feature 模块只操作**归属于本模块的数据表**，通过 DAO 访问
- 跨模块的数据需求通过 Repository 接口和事件总线解决，**禁止直接跨模块访问 DAO**

---

## 七、安全设计总览

### 家长权限保护操作清单

```
ParentGateway 保护以下操作（PIN验证，Android Keystore存储）：
  任务模块：创建/删除任务、审核打卡
  蘑菇模块：修改蘑菇等级配置、记录扣分、配置扣分规则、审核申诉
  奖品模块：管理奖品、确认兑换（时长型奖品需二次确认）
  里程碑：录入成绩、删除里程碑
  设置：所有配置项修改
```

---

## 八、V2 扩展设计预留

### 8.1 AI 任务生成扩展点

TaskGeneratorService 接口在 V1 由 ManualTaskGeneratorService 实现，V2 替换为 LLMTaskGeneratorService，Hilt 注入点无需改动。

### 8.2 云同步扩展点

所有 Repository 接口在 V1 只有本地 Room 实现。V2 引入 RemoteDataSource 接口，Repository 实现升级为本地+远端融合模式。

### 8.3 语音输入扩展点

TaskGeneratorInput 数据类预留 audioData 字段，V1 为 null，V2 由 VoiceRecognizer 填充。

---

## 九、开发路线图

### Phase 1（MVP，约4-6周）

1. 项目脚手架搭建（模块结构、DI 配置、导航、EventBus）
2. core-data：数据库所有表创建、DAO、Repository 实现
3. feature-task：任务CRUD、重复任务、截止时间、预设模板
4. feature-checkin：每日打卡、提前完成判断、打卡历史
5. feature-mushroom：奖励引擎（基础+提前完成+模板规则）、账本、余额展示
6. 首页聚合展示

### Phase 2（核心功能完善，约3-4周）

7. feature-reward：奖品管理、拼图系统、时长型奖品额度管理
8. feature-milestone：里程碑配置、成绩录入、分数段奖励
9. feature-mushroom：扣分机制、申诉流程、关键日期奖励
10. 家长模式 + PIN 保护
11. feature-statistics：图表统计

### Phase 3（打磨与优化，约2周）

12. 动画优化（蘑菇收集、拼图解锁、扣分提示）
13. 数据导出备份
14. 全面测试

### Phase 4（V2，未来规划）

15. 语音输入 + 大模型任务生成
16. 云端同步

---

*文档结束*
