# 蘑菇打卡 - 学习激励成长应用
## 总体架构设计文档

版本 v2.0，日期 2026-03-01，待审核

变更记录：v1.0初版，v1.1同步需求v1.1~v1.3，v2.0重构为总体架构文档+模块分离

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

- feature 模块只依赖 core-domain，不互相依赖
- core-data 实现 core-domain 中的 Repository 接口
- app 模块负责依赖注入的装配（Hilt Module绑定）
- service 模块被 feature 模块按需依赖

---

## 四、模块清单与对应详细设计文档索引

| 模块 | 职责说明 | 详细设计文档 |
|------|---------|------------|
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
