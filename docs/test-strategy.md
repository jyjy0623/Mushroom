# 蘑菇大冒险 - 测试策略

**版本**：v1.1
**日期**：2026-03-01
**状态**：待审核

**变更记录**：
| 版本 | 日期 | 变更内容 |
|------|------|---------|
| v1.0 | 2026-03-01 | 初版 |
| v1.1 | 2026-03-01 | 新增：构建与测试环境搭建验证；各测试段断言规范（含代码示例）；自动化测试报告规范 |

---

## 零、构建与测试环境搭建

**原则**：所有开发成员在开始第一行代码之前，必须完成本章环境搭建并通过验证脚本，确保"在我机器上能跑"问题在开发期消除。

---

### 0.1 必要工具清单

| 工具 | 版本要求 | 检查命令 | 说明 |
|------|---------|---------|------|
| JDK | 17（LTS） | `java -version` | 输出含 `17.x.x`  |
| Android SDK | API 34（compileSdk） | `sdkmanager --list_installed` | 需含 `platforms;android-34` |
| Android SDK Build-Tools | 34.0.0 | 同上 | 需含 `build-tools;34.0.0` |
| Gradle | 8.x（Wrapper 自动下载） | `./gradlew --version` | 首次运行自动下载 |
| Git | ≥ 2.40 | `git --version` | |
| Android Studio | Hedgehog 或更新 | 启动查看 About | 含 Kotlin 插件 |

---

### 0.2 环境变量配置

```bash
# ~/.bashrc 或 ~/.zshrc 或 Windows 系统环境变量

export JAVA_HOME=/path/to/jdk-17          # Windows: C:\Program Files\Java\jdk-17
export ANDROID_HOME=/path/to/android-sdk  # Windows: C:\Users\<user>\AppData\Local\Android\Sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/tools
```

**验证**：
```bash
echo $JAVA_HOME        # 非空
echo $ANDROID_HOME     # 非空
adb version            # 输出 Android Debug Bridge version x.x.x
```

---

### 0.3 项目初始化步骤

```bash
# 1. 克隆仓库
git clone https://github.com/jyjy0623/Mushroom.git
cd Mushroom

# 2. 下载 Gradle Wrapper 并同步依赖（首次约 5-10 分钟）
./gradlew dependencies --configuration debugRuntimeClasspath

# 3. 触发一次完整构建（验证编译链路）
./gradlew assembleDebug

# 4. 运行环境检查脚本（见 0.4）
./scripts/check-env.sh
```

---

### 0.4 环境检查脚本

在仓库根目录创建 `scripts/check-env.sh`，执行后输出每项检查结果，**全部 PASS 才允许开始开发**：

```bash
#!/usr/bin/env bash
# scripts/check-env.sh
# 蘑菇大冒险 - 构建与测试环境检查脚本
# 用法：chmod +x scripts/check-env.sh && ./scripts/check-env.sh

set -e
PASS=0
FAIL=0

check() {
    local name="$1"
    local cmd="$2"
    local expect="$3"
    if eval "$cmd" 2>&1 | grep -q "$expect"; then
        echo "  ✅  $name"
        PASS=$((PASS+1))
    else
        echo "  ❌  $name  (期望包含: $expect)"
        FAIL=$((FAIL+1))
    fi
}

echo "========================================"
echo "  蘑菇大冒险 - 环境检查"
echo "========================================"

echo ""
echo "[ JDK ]"
check "JDK 版本 = 17"         "java -version 2>&1"         "17\."
check "javac 可用"             "javac -version 2>&1"        "javac 17"

echo ""
echo "[ Android SDK ]"
check "ANDROID_HOME 已设置"   "echo $ANDROID_HOME"         "/Android"
check "platform android-34"   "ls $ANDROID_HOME/platforms" "android-34"
check "build-tools 34.0.0"    "ls $ANDROID_HOME/build-tools" "34.0.0"
check "adb 可用"               "adb version"                "Android Debug Bridge"

echo ""
echo "[ Gradle ]"
check "Gradle Wrapper 可用"   "./gradlew --version"        "Gradle 8"

echo ""
echo "[ 构建验证 ]"
check "assembleDebug 成功"     "./gradlew assembleDebug -q && echo BUILD_OK" "BUILD_OK"

echo ""
echo "[ 测试环境 ]"
check "UT 可运行（core-domain）" \
    "./gradlew :core-domain:test -q && echo UT_OK" "UT_OK"
check "UT 可运行（core-data）" \
    "./gradlew :core-data:test -q && echo UT_OK"   "UT_OK"

echo ""
echo "========================================"
if [ $FAIL -eq 0 ]; then
    echo "  结果：全部通过 ✅  ($PASS 项)"
    echo "  环境就绪，可以开始开发。"
else
    echo "  结果：$FAIL 项未通过 ❌  ($PASS 通过 / $FAIL 失败)"
    echo "  请修复以上问题后重新运行检查。"
    exit 1
fi
echo "========================================"
```

---

### 0.5 本地 Gradle 配置优化

在项目根目录 `gradle.properties` 中追加以下配置，加快构建速度：

```properties
# gradle.properties
org.gradle.jvmargs=-Xmx4g -XX:+UseParallelGC
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
kotlin.incremental=true
android.enableBuildCache=true
```

---

### 0.6 测试专用依赖确认

在根 `build.gradle.kts` 或各模块 `build.gradle.kts` 中确认以下测试依赖存在：

```kotlin
// 所有模块通用
testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
testImplementation("io.mockk:mockk:1.13.9")
testImplementation("app.cash.turbine:turbine:1.1.0")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")

// 需要 Room In-Memory DB 的模块（core-data、集成测试模块）
testImplementation("androidx.room:room-testing:2.6.1")
testImplementation("org.robolectric:robolectric:4.11.1")

// system-test 模块
testImplementation(project(":core-data"))
testImplementation(project(":feature-checkin"))
testImplementation(project(":feature-mushroom"))
testImplementation(project(":feature-reward"))
testImplementation(project(":feature-milestone"))
```

**依赖验证**：
```bash
# 检查所有测试依赖可解析（无 404 / 版本冲突）
./gradlew :core-data:dependencies --configuration testRuntimeClasspath | grep -E "FAILED|conflict"
# 期望输出：空（无 FAILED 和 conflict）
```

---

## 一、测试总览

### 测试层级

```
┌─────────────────────────────────────────────────────────────┐
│  ST（系统测试）                                               │
│  跨模块端到端场景，验证功能闭环                                │
├─────────────────────────────────────────────────────────────┤
│  集成测试（Integration Test）                                 │
│  单模块内部，Use Case + Repository（真实数据库）               │
├─────────────────────────────────────────────────────────────┤
│  单元测试（Unit Test）                                        │
│  Use Case 逻辑、Rule Engine、Mapper（依赖全部 Mock）           │
└─────────────────────────────────────────────────────────────┘
         多       ←── 执行速度 ──→       慢
         少       ←── 编写成本 ──→       高
```

### 测试框架

| 层级 | 框架 | 说明 |
|------|------|------|
| UT | JUnit 5 + MockK | 纯 JVM，无 Android 依赖，秒级运行 |
| UT（Flow） | Turbine | 测试 Flow 发射序列 |
| 集成测试 | JUnit 5 + Room In-Memory DB + Robolectric | 真实 SQLite，仍在 JVM 运行 |
| ST | JUnit 5 + Room In-Memory DB + 真实 EventBus | 多模块联动，完整业务场景 |
| UI 测试（可选） | Compose UI Test | Phase 3 视情况补充，非门禁条件 |

### 覆盖率要求

| 测试对象 | 覆盖率要求 |
|---------|-----------|
| Rule Engine（业务规则计算） | **100% 分支覆盖** |
| Use Case 核心逻辑 | **≥ 80% 行覆盖** |
| Repository 实现（Mapper） | ≥ 70% |
| ViewModel | ≥ 60%（状态变化验证） |
| 工具类 | ≥ 70% |

---

## 二、单元测试（UT）

### 2.1 测试原则

- 每个 Use Case 对应一个测试文件
- 每个 Rule Engine 对应一个测试文件
- 所有外部依赖（Repository、EventBus、Logger）用 MockK Mock
- 测试命名：`when_<条件>_should_<预期结果>`
- 每个测试文件包含：Happy Path、错误路径、边界条件三类用例

### 2.2 UT 断言规范

所有 UT 使用统一的断言结构，**断言逻辑必须写在测试方法体内**，不得只调用被测方法而不验证结果。

```kotlin
// 标准 UT 结构模板（Arrange / Act / Assert）
@Test
fun `when_checkin_before_deadline_should_mark_early`() {
    // Arrange：准备数据和 Mock
    val deadline = LocalDateTime.of(2026, 3, 1, 20, 0)
    val checkedAt = LocalDateTime.of(2026, 3, 1, 19, 0)   // 提前 60 分钟
    val task = Task(id = 1L, deadline = deadline, /* ... */)
    coEvery { taskRepo.getById(1L) } returns task
    every { clock.now() } returns checkedAt

    // Act：执行被测方法
    val result = useCase(taskId = 1L)

    // Assert：明确验证每个预期结果（每条 assert 对应测试描述中的一项验证）
    assertTrue(result.isSuccess)
    val checkIn = result.getOrThrow()
    assertTrue(checkIn.isEarly)                   // 必须为 true
    assertEquals(60, checkIn.earlyMinutes)        // 精确值断言
    assertEquals(TaskStatus.EARLY_DONE, checkIn.status)

    // 验证事件发布（副作用断言）
    coVerify(exactly = 1) {
        eventBus.emit(match<AppEvent.TaskCheckedIn> {
            it.taskId == 1L && it.isEarly && it.earlyMinutes == 60
        })
    }
    // 验证数据库写入
    coVerify(exactly = 1) { checkInRepo.insert(any()) }
}
```

**断言要求**：

| 类型 | 要求 | 示例 |
|------|------|------|
| 返回值 | 必须验证 isSuccess / isFailure | `assertTrue(result.isSuccess)` |
| 数值字段 | 使用 `assertEquals` 精确比较 | `assertEquals(60, checkIn.earlyMinutes)` |
| 枚举/状态 | 明确断言具体枚举值 | `assertEquals(EARLY_DONE, status)` |
| 集合大小 | 验证数量 + 关键元素 | `assertEquals(2, rewards.size)` |
| 事件发布 | 用 `coVerify` 验证发布次数和参数 | `coVerify(exactly = 1) { eventBus.emit(...) }` |
| 负向路径 | 验证未发生的调用 | `coVerify(exactly = 0) { repo.insert(any()) }` |
| 错误类型 | 验证 Failure 的具体异常类型 | `assertTrue(result.exceptionOrNull() is DailyLimitException)` |

---

### 2.3 core-domain / core-data：Mapper 测试

**文件**：`core-data/src/test/.../mapper/`

| 测试文件 | 测试内容 |
|---------|---------|
| `TaskMapperTest` | DB Entity ↔ Domain 双向转换；RepeatRule JSON 序列化/反序列化；repeatRuleDays 空集合处理 |
| `CheckInMapperTest` | imageUris JSON 数组（空数组、多个 URI）；isEarly/earlyMinutes 对应关系 |
| `MushroomLedgerMapperTest` | 所有 MushroomSource 枚举值；action 枚举映射 |
| `DeductionMapperTest` | AppealStatus 全状态映射；appealNote null 处理 |
| `RewardMapperTest` | required_mushrooms JSON（多等级）；TimeLimitConfig JSON；null puzzle/time 字段 |
| `MilestoneMapperTest` | ScoringRule 列表合并；实际分数 null 处理；status 枚举全映射 |
| `KeyDateMapperTest` | condition_value JSON 各类型（ConsecutiveCheckinDays、MilestoneScore、ManualTrigger） |

**典型用例**：
```kotlin
@Test
fun `when_repeatRule_is_Custom_with_multiple_days_should_roundtrip_correctly`() {
    val domain = Task(repeatRule = RepeatRule.Custom(setOf(MONDAY, WEDNESDAY, FRIDAY)), /* ... */)
    val dbEntity = TaskMapper.toDb(domain)
    val restored = TaskMapper.toDomain(dbEntity)

    assertEquals(domain.repeatRule, restored.repeatRule)   // 完整对比，不只看类型
    assertEquals(setOf(MONDAY, WEDNESDAY, FRIDAY),
        (restored.repeatRule as RepeatRule.Custom).days)
}

@Test
fun `when_deadline_is_null_should_map_to_null_without_exception`() {
    val domain = Task(deadline = null, /* ... */)
    val dbEntity = TaskMapper.toDb(domain)
    assertNull(dbEntity.deadline)                           // DB 字段为 null
    val restored = TaskMapper.toDomain(dbEntity)
    assertNull(restored.deadline)                           // 还原后仍为 null
}
```

---

### 2.4 feature-task：Use Case 测试

**文件**：`feature-task/src/test/.../usecase/`

#### CreateTaskUseCaseTest
| 用例 | 场景 | 关键断言 |
|------|------|---------|
| `when_repeat_none_should_insert_single_task` | 无重复规则 | `coVerify(exactly = 1) { repo.insert(any()) }` |
| `when_repeat_daily_should_generate_instances_for_date_range` | 每日重复（7天范围） | `coVerify(exactly = 7) { repo.insert(any()) }` |
| `when_repeat_weekdays_should_skip_weekends` | 工作日重复（含周末） | 验证只有工作日被插入，`coVerify(exactly = 5) { ... }` |
| `when_repeat_custom_with_mon_wed_fri_should_generate_correct_days` | 自定义星期 | 验证日期列表仅含周一三五 |
| `when_repeat_generation_called_twice_should_be_idempotent` | 幂等性 | 第二次调用后总记录数不变 |
| `when_repository_throws_should_return_failure` | 数据库异常 | `assertTrue(result.isFailure)` + `coVerify(exactly = 0) { eventBus.emit(any()) }` |

#### DeleteTaskUseCaseTest
| 用例 | 场景 | 关键断言 |
|------|------|---------|
| `when_mode_single_should_delete_only_target_task` | 单条删除 | `coVerify(exactly = 1) { repo.delete(taskId) }` |
| `when_mode_all_recurring_should_delete_all_in_series` | 全删 | `coVerify(exactly = 1) { repo.deleteAllByTemplateId(templateId) }` |
| `when_task_not_found_should_return_failure` | 目标不存在 | `assertTrue(result.isFailure)` |

#### ApplyTaskTemplateUseCaseTest
| 用例 | 场景 | 关键断言 |
|------|------|---------|
| `when_template_has_deadline_offset_480_should_set_deadline_to_08_00` | 480分钟偏移 | `assertEquals(LocalTime.of(8,0), result.getOrThrow().deadline!!.toLocalTime())` |
| `when_target_date_already_has_task_from_template_should_skip` | 幂等 | `coVerify(exactly = 0) { repo.insert(any()) }` |

---

### 2.5 feature-checkin：Use Case 测试

**文件**：`feature-checkin/src/test/.../usecase/`

#### CheckInTaskUseCaseTest（最重要）
| 用例 | 场景 | 关键断言 |
|------|------|---------|
| `when_task_has_no_deadline_should_mark_on_time` | deadline=null | `assertFalse(result.getOrThrow().isEarly)` + `assertEquals(0, earlyMinutes)` + `assertEquals(ON_TIME_DONE, status)` |
| `when_checkin_before_deadline_should_mark_early` | 提前60分钟 | `assertTrue(isEarly)` + `assertEquals(60, earlyMinutes)` + `assertEquals(EARLY_DONE, status)` |
| `when_checkin_exactly_at_deadline_should_mark_on_time` | 恰好到期 | `assertFalse(isEarly)` + `assertEquals(0, earlyMinutes)` |
| `when_checkin_after_deadline_should_mark_on_time` | 超时 | `assertFalse(isEarly)` + `assertEquals(ON_TIME_DONE, status)` |
| `when_early_by_30_min_should_set_earlyMinutes_to_30` | 精度验证 | `assertEquals(30, earlyMinutes)` |
| `when_checkin_succeeds_should_emit_TaskCheckedIn_event` | 事件验证 | `coVerify { eventBus.emit(match<AppEvent.TaskCheckedIn> { it.isEarly && it.earlyMinutes == 60 }) }` |
| `when_task_not_found_should_return_failure` | 任务不存在 | `assertTrue(result.isFailure)` + `coVerify(exactly = 0) { checkInRepo.insert(any()) }` |
| `when_already_checked_in_today_should_return_failure` | 重复打卡 | `assertTrue(result.isFailure)` + `coVerify(exactly = 0) { checkInRepo.insert(any()) }` |

#### GetStreakUseCaseTest
| 用例 | 场景 | 关键断言 |
|------|------|---------|
| `when_has_checkin_today_should_count_from_today` | 含今日 | `assertEquals(5, streak.first())` |
| `when_no_checkin_today_should_count_from_yesterday` | 不含今日 | `assertEquals(4, streak.first())` |
| `when_streak_broken_by_one_day_should_return_current_streak` | 中断 | `assertEquals(2, currentStreak.first())` |
| `when_no_checkins_ever_should_return_zero` | 全新 | `assertEquals(0, streak.first())` |

#### CheckAllTasksDoneUseCaseTest
| 用例 | 场景 | 关键断言 |
|------|------|---------|
| `when_all_tasks_done_should_emit_AllDailyTasksDone_event` | 全完成 | `coVerify(exactly = 1) { eventBus.emit(ofType<AppEvent.AllDailyTasksDone>()) }` |
| `when_some_tasks_pending_should_not_emit_event` | 未全完成 | `coVerify(exactly = 0) { eventBus.emit(any()) }` |
| `when_no_tasks_for_date_should_not_emit_event` | 当日无任务 | `coVerify(exactly = 0) { eventBus.emit(any()) }` |

---

### 2.6 feature-mushroom：Rule Engine 测试（关键）

**文件**：`feature-mushroom/src/test/.../rule/`

Rule Engine 测试必须 **100% 分支覆盖**，每个分支边界都有独立测试用例。

```kotlin
// EarlyCompletionRule 测试结构示例
class EarlyCompletionRuleTest {
    private val rule = EarlyCompletionRule()

    @Test fun `when_isEarly_false_should_return_empty`() {
        val event = RewardEvent.TaskCompleted(task = mockTask(), checkIn = mockCheckIn(isEarly = false))
        val rewards = rule.calculate(event)
        assertTrue(rewards.isEmpty())                 // 精确验证：返回空列表
    }

    @Test fun `when_early_by_59_min_should_return_SMALL_x1`() {
        val rewards = rule.calculate(earlyEvent(earlyMinutes = 59))
        assertEquals(1, rewards.size)
        assertEquals(MushroomLevel.SMALL, rewards[0].level)
        assertEquals(1, rewards[0].amount)
    }

    @Test fun `when_early_by_60_min_should_return_SMALL_x2`() {    // 边界
        val rewards = rule.calculate(earlyEvent(earlyMinutes = 60))
        assertEquals(1, rewards.size)
        assertEquals(MushroomLevel.SMALL, rewards[0].level)
        assertEquals(2, rewards[0].amount)
    }

    @Test fun `when_early_by_180_min_should_return_SMALL_x2`() {   // 边界
        val rewards = rule.calculate(earlyEvent(earlyMinutes = 180))
        assertEquals(MushroomLevel.SMALL, rewards[0].level)
        assertEquals(2, rewards[0].amount)
    }

    @Test fun `when_early_by_181_min_should_return_MEDIUM_x1`() {  // 边界+1
        val rewards = rule.calculate(earlyEvent(earlyMinutes = 181))
        assertEquals(MushroomLevel.MEDIUM, rewards[0].level)
        assertEquals(1, rewards[0].amount)
    }
}
```

#### 各 Rule 测试用例（含关键断言）

| 测试文件 | 用例 | 关键断言 |
|---------|------|---------|
| `DailyTaskCompleteRuleTest` | `when_any_task_completed_should_return_SMALL_x1` | `assertEquals(1, rewards.size)` + `assertEquals(SMALL, rewards[0].level)` + `assertEquals(1, rewards[0].amount)` |
| `DailyTaskCompleteRuleTest` | `when_event_not_TaskCompleted_should_return_empty` | `assertTrue(rewards.isEmpty())` |
| `EarlyCompletionRuleTest` | 6个分支覆盖用例（见上方代码示例） | 精确验证 level + amount |
| `MorningReadingRuleTest` | `when_within_10min_should_return_SMALL_x2_total` | `assertEquals(2, rewards.sumOf { it.amount })` |
| `MorningReadingRuleTest` | `when_exactly_10min_should_add_bonus` | `assertEquals(2, rewards.size)` |
| `MorningReadingRuleTest` | `when_11min_should_not_add_bonus` | `assertEquals(1, rewards.size)` + `assertEquals(SMALL, rewards[0].level)` |
| `HomeworkMemoRuleTest` | `when_streak_4_days_should_not_add_bonus` | `assertEquals(1, rewards.size)` |
| `HomeworkMemoRuleTest` | `when_streak_5_days_should_add_MEDIUM_x1_bonus` | `assertEquals(2, rewards.size)` + 验证含 MEDIUM level 的 reward |
| `AllTasksDoneRuleTest` | `when_all_tasks_done_should_return_MEDIUM_x1` | `assertEquals(MEDIUM, rewards[0].level)` + `assertEquals(1, rewards[0].amount)` |
| `StreakRuleTest` | `when_streak_7_days_should_return_LARGE_x1` | `assertEquals(LARGE, rewards[0].level)` |
| `StreakRuleTest` | `when_streak_6_days_should_return_empty` | `assertTrue(rewards.isEmpty())` |
| `StreakRuleTest` | `when_streak_30_days_should_return_GOLD_x1` | `assertEquals(GOLD, rewards[0].level)` |
| `MilestoneScoreRuleTest` | `when_score_on_boundary_minScore_should_match` | 精确匹配该档奖励 |
| `MilestoneScoreRuleTest` | `when_score_one_below_minScore_should_not_match` | `assertTrue(rewards.isEmpty())` |
| `DeductionRuleEngineTest` | `when_count_equals_maxPerDay_should_deny` | `assertFalse(engine.canDeduct(configId, today))` |
| `DeductionRuleEngineTest` | `when_count_below_maxPerDay_should_allow` | `assertTrue(engine.canDeduct(configId, today))` |

---

### 2.7 feature-mushroom：Use Case 测试

**文件**：`feature-mushroom/src/test/.../usecase/`

#### DeductMushroomUseCaseTest
| 用例 | 关键断言 |
|------|---------|
| Happy Path | `assertTrue(result.isSuccess)` + `coVerify { mushroomRepo.recordTransaction(match { it.action == DEDUCT }) }` + `coVerify { deductionRepo.insertRecord(any()) }` |
| 每日上限 | `assertTrue(result.isFailure)` + `coVerify(exactly = 0) { mushroomRepo.recordTransaction(any()) }` |
| 余额不足扣到0 | 验证实际扣除 = 当前余额（非配置数量） |
| 余额为0 | `assertTrue(result.isFailure)` + `coVerify(exactly = 0) { deductionRepo.insertRecord(any()) }` |
| 事件发布 | `coVerify { eventBus.emit(ofType<AppEvent.MushroomDeducted>()) }` |

#### AppealDeductionUseCaseTest / ReviewAppealUseCaseTest
| 用例 | 关键断言 |
|------|---------|
| 正常申诉 | `coVerify { deductionRepo.updateAppealStatus(recordId, PENDING, any()) }` |
| 重复申诉 | `assertTrue(result.isFailure)` + `coVerify(exactly = 0) { deductionRepo.updateAppealStatus(any(), any(), any()) }` |
| 批准申诉 | `coVerify { mushroomRepo.recordTransaction(match { it.action == EARN && it.source == APPEAL_REFUND }) }` + `coVerify { deductionRepo.updateAppealStatus(recordId, APPROVED, any()) }` |
| 拒绝申诉 | `coVerify(exactly = 0) { mushroomRepo.recordTransaction(any()) }` + `coVerify { deductionRepo.updateAppealStatus(recordId, REJECTED, any()) }` |

---

### 2.8 feature-reward：Use Case 测试

**文件**：`feature-reward/src/test/.../usecase/`

#### ExchangeMushroomsUseCaseTest

```kotlin
// PHYSICAL 奖品 - 最后一块完成拼图断言示例
@Test
fun `when_only_one_piece_remaining_should_complete_puzzle`() {
    // Arrange
    val reward = Reward(type = PHYSICAL, puzzlePieces = 10, /* ... */)
    every { rewardRepo.getPuzzleProgress(rewardId) } returns
        PuzzleProgress(totalPieces = 10, unlockedPieces = 9)   // 还差1块
    every { mushroomRepo.getBalance() } returns mapOf(SMALL to 5)

    // Act
    val result = useCase(rewardId = rewardId, level = SMALL, amount = 1)

    // Assert
    assertTrue(result.isSuccess)
    val progress = result.getOrThrow() as PuzzleProgress
    assertEquals(10, progress.unlockedPieces)
    assertTrue(progress.isCompleted)              // 必须完成
    // 事件中 isCompleted = true，触发庆祝动画
    coVerify {
        eventBus.emit(match<AppEvent.RewardPuzzleUpdated> { it.isCompleted })
    }
}
```

| 用例 | 关键断言 |
|------|---------|
| 余额不足 | `assertTrue(result.isFailure)` + `coVerify(exactly = 0) { mushroomRepo.recordTransaction(any()) }` |
| 超周上限（TIME_BASED） | `assertTrue(result.isFailure)` + 验证 Failure 类型为 `PeriodLimitExceededException` |
| 冷却期内 | `assertTrue(result.isFailure)` + 验证 Failure 类型为 `CooldownException` |
| 冷却期恰好结束 | `assertTrue(result.isSuccess)` |
| 家长拒绝确认 | `assertTrue(result.isFailure)` + `coVerify(exactly = 0) { mushroomRepo.recordTransaction(any()) }` |

#### PuzzleCutterTest
```kotlin
@Test fun `when_20_pieces_should_produce_4x5_grid`() {
    val grid = PuzzleCutter.calculateGrid(20)
    assertEquals(4, grid.rows)
    assertEquals(5, grid.cols)
    assertEquals(20, grid.rows * grid.cols)   // 总块数对
}
```

---

### 2.9 feature-milestone：Use Case 测试

**文件**：`feature-milestone/src/test/.../usecase/`

#### RecordMilestoneScoreUseCaseTest
| 用例 | 关键断言 |
|------|---------|
| score=92，匹配第一档 | `assertTrue(result.isSuccess)` + `assertEquals(REWARDED, result.getOrThrow().status)` + `coVerify { eventBus.emit(match<AppEvent.MilestoneScored> { it.score == 92 }) }` |
| score=59，无奖励 | `assertTrue(result.isSuccess)` + `assertEquals(SCORED, result.getOrThrow().status)` + `coVerify(exactly = 0) { eventBus.emit(any()) }` |
| score=-1 | `assertTrue(result.isFailure)` + `coVerify(exactly = 0) { milestoneRepo.updateScore(any(), any()) }` |
| 重复录入 | `assertTrue(result.isFailure)` + `coVerify(exactly = 0) { milestoneRepo.updateScore(any(), any()) }` |
| 家长未认证 | `assertTrue(result.isFailure)` + 第一个调用就被 ParentGateway 拦截 |

---

## 三、集成测试（Integration Test）

集成测试使用 **Room In-Memory Database**，验证 Repository 实现和 DAO 查询逻辑的正确性。

### 3.1 集成测试基类

所有集成测试继承以下基类，确保每个测试方法使用干净的数据库：

```kotlin
// core-data/src/test/.../IntegrationTestBase.kt
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
abstract class IntegrationTestBase {

    protected lateinit var db: MushroomDatabase

    @BeforeEach
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MushroomDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }
}
```

### 3.2 core-data 集成测试

**文件**：`core-data/src/test/.../integration/`

#### MushroomLedgerIntegrationTest
| 用例 | 断言逻辑 |
|------|---------|
| `given_earn_5_and_spend_2_should_return_balance_3` | `assertEquals(3, db.mushroomLedgerDao().getBalance()[SMALL])` |
| `given_no_transactions_for_LARGE_should_return_zero_not_null` | `assertEquals(0, balance[LARGE] ?: 0)` |
| `given_multiple_levels_should_return_balance_per_level` | 逐级验证：SMALL=5, MEDIUM=2, LARGE=0 |

#### DeductionRecordIntegrationTest
```kotlin
@Test
fun `given_maxPerDay_1_and_one_record_today_should_deny_second`() {
    // 插入第一条记录
    db.deductionRecordDao().insert(DeductionRecordEntity(configId = 1L, recordedAt = today, /* ... */))

    // 验证今日计数 = 1
    val count = db.deductionRecordDao().getTodayCount(configId = 1L, date = today)
    assertEquals(1, count)      // 精确值断言

    // 验证上限判断
    val config = DeductionConfig(maxPerDay = 1, /* ... */)
    assertFalse(DeductionRuleEngineImpl().canDeduct(config, count))
}
```

#### CheckInStreakIntegrationTest
```kotlin
@Test
fun `given_5_consecutive_days_checkin_should_return_streak_5`() {
    val today = LocalDate.of(2026, 3, 1)
    // 插入连续5天打卡
    (0..4).forEach { offset ->
        db.checkInDao().insert(CheckInEntity(date = today.minusDays(offset.toLong()), /* ... */))
    }
    val streak = db.checkInDao().getConsecutiveDaysUntil(today)
    assertEquals(5, streak)
}

@Test
fun `given_checkin_gap_on_day_3_should_return_streak_2`() {
    // 今天、昨天有打卡，前天没有，大前天有
    db.checkInDao().insert(CheckInEntity(date = today, /* ... */))
    db.checkInDao().insert(CheckInEntity(date = today.minusDays(1), /* ... */))
    // day -2 跳过
    db.checkInDao().insert(CheckInEntity(date = today.minusDays(3), /* ... */))

    val streak = db.checkInDao().getConsecutiveDaysUntil(today)
    assertEquals(2, streak)      // 只数连续的，不跨越断点
}
```

#### TimeRewardUsageIntegrationTest
| 用例 | 断言逻辑 |
|------|---------|
| 同周多次累加 | 两次各用30分钟后 `assertEquals(60, usage.usedMinutes)` |
| 新周重置 | 插入上周记录，推进日期到新周，`assertEquals(0, usage.usedMinutes)` |

---

### 3.3 模块内 Use Case + Repository 集成测试

| 测试文件 | 关键断言 |
|---------|---------|
| `CheckInFlowIntegrationTest` | 打卡后 `checkInRepo.getByDate(today).first()` 返回正确记录；streak Flow 发射正确值 |
| `MushroomEarnFlowIntegrationTest` | earn 后 `mushroomRepo.getBalance().first()[SMALL]` 与预期一致 |
| `DeductionFlowIntegrationTest` | 扣分→申诉→批准后，余额 Flow 最终值与初始值一致 |
| `RewardExchangeFlowIntegrationTest` | 兑换后 `rewardRepo.getPuzzleProgress(id).first().unlockedPieces` 增加 |
| `MilestoneScoreFlowIntegrationTest` | 录入成绩后 `milestoneRepo.getAll().first()` 中对应里程碑 status = REWARDED |

---

## 四、系统测试（ST）

ST 测试多个模块联动的完整业务场景，使用 In-Memory DB + 真实 EventBus，不 Mock 任何业务组件（只 Mock 外部依赖：ParentGateway、NotificationService、时钟）。

### 4.1 ST 基类

```kotlin
// system-test/src/test/.../STBase.kt
abstract class STBase : IntegrationTestBase() {

    // 真实组件（非 Mock）
    protected lateinit var eventBus: AppEventBusImpl
    protected lateinit var taskRepo: TaskRepositoryImpl
    protected lateinit var checkInRepo: CheckInRepositoryImpl
    protected lateinit var mushroomRepo: MushroomRepositoryImpl
    protected lateinit var deductionRepo: DeductionRepositoryImpl
    protected lateinit var rewardRepo: RewardRepositoryImpl
    protected lateinit var milestoneRepo: MilestoneRepositoryImpl

    // 可控时钟（替换 LocalDateTime.now()）
    protected val clock = TestClock(initial = LocalDateTime.of(2026, 3, 1, 9, 0))

    // Mock 外部依赖
    protected val parentGateway: ParentGateway = mockk {
        coEvery { authenticate() } returns Result.success(Unit)
        every { isAuthenticated() } returns true
    }
    protected val notificationService: NotificationService = mockk(relaxed = true)

    @BeforeEach
    override fun setUp() {
        super.setUp()   // 初始化 In-Memory DB
        eventBus = AppEventBusImpl()
        taskRepo = TaskRepositoryImpl(db.taskDao())
        checkInRepo = CheckInRepositoryImpl(db.checkInDao())
        mushroomRepo = MushroomRepositoryImpl(db.mushroomLedgerDao())
        deductionRepo = DeductionRepositoryImpl(db.deductionConfigDao(), db.deductionRecordDao())
        rewardRepo = RewardRepositoryImpl(db.rewardDao())
        milestoneRepo = MilestoneRepositoryImpl(db.milestoneDao())
        // 启动 MushroomRewardEngine（订阅 EventBus）
        MushroomRewardEngine(eventBus, mushroomRepo, clock).startListening(testScope)
    }

    // 便捷方法：读取当前余额
    protected suspend fun balanceOf(level: MushroomLevel): Int =
        mushroomRepo.getBalance().first()[level] ?: 0
}
```

---

### 4.2 ST 场景（含完整断言）

#### ST-001：日常打卡完整链路

```kotlin
@Test
fun `ST001_daily_checkin_with_early_completion_should_earn_correct_mushrooms`() = runTest {
    // Arrange
    clock.set(LocalDateTime.of(2026, 3, 1, 19, 0))   // 当前时间 19:00
    val taskId = taskRepo.insert(Task(deadline = LocalDateTime.of(2026, 3, 1, 20, 0), /* ... */))
    val initialBalance = balanceOf(SMALL)

    // Act
    val result = CheckInTaskUseCase(taskRepo, checkInRepo, eventBus, clock)(taskId)
    testScheduler.advanceUntilIdle()   // 等待 EventBus 异步处理完成

    // Assert - 打卡记录
    assertTrue(result.isSuccess)
    val checkIn = result.getOrThrow()
    assertTrue(checkIn.isEarly)
    assertEquals(60, checkIn.earlyMinutes)
    assertEquals(TaskStatus.EARLY_DONE, checkIn.status)

    // Assert - 蘑菇奖励（基础1 + 提前60min=2 → 共3个小蘑菇）
    val newBalance = balanceOf(SMALL)
    assertEquals(initialBalance + 3, newBalance)

    // Assert - 账本记录（2条 EARN：TASK 来源 + EARLY_BONUS 来源）
    val ledger = mushroomRepo.getLedger().first()
    assertEquals(2, ledger.filter { it.action == EARN }.size)
    assertTrue(ledger.any { it.source == MushroomSource.TASK })
    assertTrue(ledger.any { it.source == MushroomSource.EARLY_BONUS })
}
```

#### ST-002：全勤奖励触发链路

```kotlin
@Test
fun `ST002_completing_all_daily_tasks_should_earn_medium_mushroom`() = runTest {
    // Arrange：创建3个任务，打卡前2个
    val task1 = taskRepo.insert(Task(date = today, /* ... */))
    val task2 = taskRepo.insert(Task(date = today, /* ... */))
    val task3 = taskRepo.insert(Task(date = today, /* ... */))
    CheckInTaskUseCase(/* ... */)(task1)
    CheckInTaskUseCase(/* ... */)(task2)
    val mediumBefore = balanceOf(MEDIUM)

    // Act：打卡第3个任务，触发全勤
    CheckInTaskUseCase(/* ... */)(task3)
    CheckAllTasksDoneUseCase(taskRepo, checkInRepo, eventBus, clock)(today)
    testScheduler.advanceUntilIdle()

    // Assert
    assertEquals(mediumBefore + 1, balanceOf(MEDIUM))
    val ledger = mushroomRepo.getLedger().first()
    assertTrue(ledger.any { it.source == MushroomSource.CHECKIN_STREAK && it.level == MEDIUM })
}
```

#### ST-003：连续打卡里程碑链路

```kotlin
@Test
fun `ST003_7_consecutive_days_should_earn_large_mushroom`() = runTest {
    // Arrange：模拟连续6天打卡
    (6 downTo 1).forEach { daysAgo ->
        clock.set(LocalDateTime.now().minusDays(daysAgo.toLong()))
        val taskId = taskRepo.insert(Task(date = clock.today(), /* ... */))
        CheckInTaskUseCase(/* ... */)(taskId)
    }
    val largeBefore = balanceOf(LARGE)

    // Act：第7天打卡
    clock.set(LocalDateTime.now())
    val taskId = taskRepo.insert(Task(date = clock.today(), /* ... */))
    CheckInTaskUseCase(/* ... */)(taskId)
    testScheduler.advanceUntilIdle()

    // Assert
    assertEquals(largeBefore + 1, balanceOf(LARGE))
}
```

#### ST-004：里程碑成绩录入链路

```kotlin
@Test
fun `ST004_recording_score_90_on_mini_test_should_earn_medium_x2`() = runTest {
    val milestoneId = milestoneRepo.insert(Milestone(
        type = MINI_TEST,
        scoringRules = listOf(ScoringRule(90, 100, MEDIUM, 2), ScoringRule(80, 89, MEDIUM, 1))
    ))
    val mediumBefore = balanceOf(MEDIUM)

    val result = RecordMilestoneScoreUseCase(milestoneRepo, eventBus, parentGateway)(milestoneId, score = 92)
    testScheduler.advanceUntilIdle()

    assertTrue(result.isSuccess)
    assertEquals(REWARDED, result.getOrThrow().status)
    assertEquals(mediumBefore + 2, balanceOf(MEDIUM))
    val ledger = mushroomRepo.getLedger().first()
    assertEquals(2, ledger.filter { it.source == MILESTONE && it.action == EARN }.size)
}
```

#### ST-005：蘑菇兑换拼图完成链路

```kotlin
@Test
fun `ST005_exchanging_last_puzzle_piece_should_complete_reward`() = runTest {
    // Arrange
    val rewardId = rewardRepo.insert(Reward(type = PHYSICAL, puzzlePieces = 10, /* ... */))
    // 预先解锁9块
    rewardRepo.updatePuzzleProgress(rewardId, unlockedPieces = 9)
    mushroomRepo.recordTransaction(MushroomTransaction(action = EARN, level = SMALL, amount = 5, /* ... */))

    // Act
    val result = ExchangeMushroomsUseCase(/* ... */)(rewardId, SMALL, amount = 1)
    testScheduler.advanceUntilIdle()

    // Assert
    assertTrue(result.isSuccess)
    val progress = rewardRepo.getPuzzleProgress(rewardId).first()
    assertEquals(10, progress.unlockedPieces)
    assertTrue(progress.isCompleted)
    assertEquals(4, balanceOf(SMALL))    // 5 - 1 = 4
}
```

#### ST-006：扣分 + 申诉 + 退还完整链路

```kotlin
@Test
fun `ST006_deduction_appeal_approved_should_restore_balance`() = runTest {
    // 初始余额
    mushroomRepo.recordTransaction(MushroomTransaction(action = EARN, level = SMALL, amount = 10, /* ... */))
    val initialBalance = balanceOf(SMALL)    // = 10

    // Step 1：扣分
    val deductResult = DeductMushroomUseCase(/* ... */)(configId = 1L)   // SMALL×2
    assertTrue(deductResult.isSuccess)
    assertEquals(initialBalance - 2, balanceOf(SMALL))   // = 8

    // Step 2：申诉
    val recordId = deductResult.getOrThrow().id
    AppealDeductionUseCase(deductionRepo)(recordId, note = "我没有忘记")
    val record = deductionRepo.getRecords().first().find { it.id == recordId }!!
    assertEquals(AppealStatus.PENDING, record.appealStatus)

    // Step 3：家长批准
    ReviewAppealUseCase(deductionRepo, mushroomRepo, eventBus)(recordId, approved = true)
    testScheduler.advanceUntilIdle()

    // Assert：余额恢复
    assertEquals(initialBalance, balanceOf(SMALL))   // = 10，与初始一致
    val updatedRecord = deductionRepo.getRecords().first().find { it.id == recordId }!!
    assertEquals(AppealStatus.APPROVED, updatedRecord.appealStatus)

    // 账本：含 DEDUCT + APPEAL_REFUND 各一条
    val ledger = mushroomRepo.getLedger().first()
    assertTrue(ledger.any { it.action == DEDUCT })
    assertTrue(ledger.any { it.source == MushroomSource.APPEAL_REFUND && it.action == EARN })
}
```

#### ST-007 ~ ST-010（结构同上，省略代码，验证要点）

| ST | 前置 | 核心验证 |
|----|------|---------|
| ST-007 扣分上限 | maxPerDay=1，今日已扣1次 | `assertTrue(result.isFailure)` + `assertEquals(ledgerSize, newLedgerSize)` + `assertEquals(before, balanceOf(SMALL))` |
| ST-008 时长奖品重置 | 当周已用90分钟 | 当周兑换 `isFailure`；推进到周一后兑换 `isSuccess` + `assertEquals(30, usage.usedMinutes)` |
| ST-009 晨读模板 | deadline=08:00，checkedAt=07:52 | `assertEquals(2, ledger.filter{it.action==EARN}.size)` + 两条来源分别是 TEMPLATE_BONUS 和基础 TASK |
| ST-010 Migration | 写入v1数据，执行Migration | 迁移后任务/打卡/蘑菇记录数不变，新增字段有正确默认值 |

---

### 4.3 ST 执行时机

| 时机 | 执行范围 | 期望时长 |
|------|---------|---------|
| Phase 1 完成时 | ST-001、ST-002、ST-003 | < 1 分钟 |
| Phase 2 完成时 | ST-001 ~ ST-009 | < 3 分钟 |
| Phase 3 完成时 | ST-001 ~ ST-010（全量） | < 5 分钟 |
| 每次合并 develop | ST-001 ~ ST-003（快速回归） | < 1 分钟 |

---

## 五、测试报告

### 5.1 报告生成方式

每个测试阶段完成后，通过 Gradle 任务自动生成 HTML + XML 格式报告，XML 用于 CI 解析，HTML 用于人工审阅。

```bash
# UT 报告（各模块）
./gradlew test jacocoTestReport
# 报告位置：<module>/build/reports/tests/test/index.html
# 覆盖率位置：<module>/build/reports/jacoco/test/html/index.html

# 集成测试报告
./gradlew integrationTest jacocoIntegrationTestReport
# 报告位置：<module>/build/reports/tests/integrationTest/index.html

# ST 报告
./gradlew :system-test:test
# 报告位置：system-test/build/reports/tests/test/index.html

# 全量聚合报告（所有模块合并覆盖率）
./gradlew jacocoMergedReport
# 报告位置：build/reports/jacoco/merged/html/index.html
```

---

### 5.2 报告生成脚本

在仓库根目录创建 `scripts/run-tests.sh`，一键执行指定测试阶段并生成报告：

```bash
#!/usr/bin/env bash
# scripts/run-tests.sh
# 用法：./scripts/run-tests.sh [phase1|phase2|phase3|ut|it|st|all]

set -e
PHASE=${1:-"ut"}
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
REPORT_DIR="build/test-reports/${TIMESTAMP}_${PHASE}"
mkdir -p "$REPORT_DIR"

echo "========================================"
echo "  蘑菇大冒险 - 测试执行  [${PHASE}]"
echo "  时间：$(date)"
echo "========================================"

run_ut() {
    echo ""
    echo "[ 执行 UT ]"
    ./gradlew test jacocoTestReport --continue
    cp -r build/reports/tests "$REPORT_DIR/ut-results"
    cp -r build/reports/jacoco "$REPORT_DIR/ut-coverage"
}

run_it() {
    echo ""
    echo "[ 执行集成测试 ]"
    ./gradlew integrationTest --continue
    cp -r build/reports/tests/integrationTest "$REPORT_DIR/it-results" 2>/dev/null || true
}

run_st() {
    echo ""
    echo "[ 执行 ST ]"
    ./gradlew :system-test:test --continue
    cp -r system-test/build/reports/tests "$REPORT_DIR/st-results"
}

generate_summary() {
    echo ""
    echo "[ 生成汇总报告 ]"
    ./gradlew jacocoMergedReport
    cp -r build/reports/jacoco/merged "$REPORT_DIR/coverage-merged"

    # 生成文字摘要
    cat > "$REPORT_DIR/SUMMARY.md" << EOF
# 测试报告摘要

**阶段**：${PHASE}
**生成时间**：$(date)
**报告目录**：${REPORT_DIR}

## 执行结果

\`\`\`
$(./gradlew test --dry-run 2>/dev/null | grep -E "PASS|FAIL|SKIP" | head -50 || echo "详见各子目录 HTML 报告")
\`\`\`

## 报告文件

- UT 结果：ut-results/test/index.html
- 覆盖率：ut-coverage/test/html/index.html
- 集成测试：it-results/index.html
- ST 结果：st-results/test/index.html
- 全量覆盖率：coverage-merged/html/index.html
EOF
    echo "  报告已生成：${REPORT_DIR}/SUMMARY.md"
}

case "$PHASE" in
    ut)       run_ut; generate_summary ;;
    it)       run_it; generate_summary ;;
    st)       run_st; generate_summary ;;
    phase1)   run_ut; run_it; run_st; generate_summary ;;
    phase2)   run_ut; run_it; run_st; generate_summary ;;
    phase3)   run_ut; run_it; run_st; generate_summary ;;
    all)      run_ut; run_it; run_st; generate_summary ;;
    *)        echo "用法: $0 [ut|it|st|phase1|phase2|phase3|all]"; exit 1 ;;
esac

echo ""
echo "========================================"
echo "  测试完成。报告：${REPORT_DIR}"
echo "========================================"
```

---

### 5.3 报告内容规范

每次测试阶段结束后，`SUMMARY.md` 必须包含以下内容，便于审阅和归档：

```markdown
# 测试报告 - [Phase X / UT / ST]

**版本**：1.0.0-dev
**执行时间**：2026-03-15 14:32:00
**执行人**：（开发者名）
**报告目录**：build/test-reports/20260315_143200_phase1/

## 执行结果汇总

| 测试类型 | 总用例数 | 通过 | 失败 | 跳过 | 结论 |
|---------|---------|------|------|------|------|
| UT      | 87      | 87   | 0    | 0    | ✅ PASS |
| 集成测试 | 18      | 18   | 0    | 0    | ✅ PASS |
| ST      | 3       | 3    | 0    | 0    | ✅ PASS |

## 覆盖率

| 模块 | 行覆盖率 | 分支覆盖率 | 是否达标 |
|------|---------|----------|---------|
| feature-mushroom (Rule) | 100% | 100% | ✅ |
| feature-checkin  | 85%  | 82%  | ✅ |
| feature-task     | 81%  | 78%  | ✅ |
| core-data        | 72%  | 70%  | ✅ |

## 失败用例（如有）

（无）

## 遗留问题

（无）

## 报告文件

- UT 结果：ut-results/test/index.html
- 覆盖率：ut-coverage/test/html/index.html
- ST 结果：st-results/test/index.html
```

---

### 5.4 各 Phase 报告门禁

| Phase | 门禁条件（测试报告必须满足） |
|-------|--------------------------|
| Phase 1 完成 | UT 全通过；Rule Engine 100% 分支；Use Case ≥ 80% 行覆盖；ST-001/002/003 通过 |
| Phase 2 完成 | Phase 1 条件 + ST-001~009 全通过；无 P0/P1 缺陷 |
| Phase 3 完成 | Phase 2 条件 + ST-010（Migration）通过；全量覆盖率满足各模块要求 |
| 合并 develop  | 本模块 UT 全通过；无覆盖率下降（与上次报告比较） |

---

## 六、测试与开发集成节奏

### 6.1 模块开发节奏（每个 Sprint）

```
Day 1-2：设计评审 + UT 用例设计（先写测试用例列表，评审后再写代码）
  ↓
Day 3-6：Use Case 开发 + 同步写 UT（TDD 或并行）
  ↓
Day 7：./scripts/run-tests.sh ut → 全部通过，Rule Engine 100% 分支
  ↓
Day 8：Repository 集成测试通过 → ./scripts/run-tests.sh it
  ↓
Day 9-10：UI 联调、代码审查、运行 ST 回归
  ↓
模块 DONE：合并 feature 分支，归档当次测试报告
```

### 6.2 合并准入条件

```
feature → develop：
  ✅ ./scripts/run-tests.sh ut  全部通过
  ✅ Rule Engine 100% 分支覆盖
  ✅ Use Case ≥ 80% 行覆盖
  ✅ 无编译 Error，核心模块 0 Warning
  ✅ 日志 Tag 使用常量

develop → release：
  ✅ ./scripts/run-tests.sh phase<N>  全部通过
  ✅ SUMMARY.md 已生成并无失败用例
  ✅ 无 P0/P1 bug
```

### 6.3 本地快捷命令

```bash
# 开发中快速验证（仅当前模块 UT）
./gradlew :feature-checkin:test

# 提交前完整检查
./scripts/run-tests.sh ut

# Phase 完成后全量验证
./scripts/run-tests.sh phase1   # 或 phase2 / phase3

# 查看最新测试报告
open build/test-reports/$(ls -t build/test-reports | head -1)/SUMMARY.md
```

---

## 七、缺陷管理

### 缺陷级别定义

| 级别 | 定义 | 要求 |
|------|------|------|
| P0 | 应用崩溃、数据丢失、蘑菇余额计算错误 | 立即修复，不可合并 |
| P1 | 核心功能不可用（打卡失败、奖励不触发） | 当前 Sprint 内修复 |
| P2 | 功能异常但有 workaround | 下个 Sprint 修复 |
| P3 | UI 问题、文案错误、性能轻微下降 | Phase 3 打磨时修复 |

### 缺陷处理流程

1. 在对应模块**先增加复现该缺陷的 UT**（防止回归）
2. 修复代码，确认 UT 通过
3. 运行相关 ST 确认无回归
4. Commit message 注明 `fix(<scope>): <描述>`
5. 重新生成测试报告，报告中记录本次修复

---

## 八、测试文件结构

```
各模块测试文件结构（以 feature-checkin 为例）：

feature-checkin/
└── src/test/java/com/mushroom/checkin/
    ├── usecase/
    │   ├── CheckInTaskUseCaseTest.kt
    │   ├── GetCheckInHistoryUseCaseTest.kt
    │   ├── GetStreakUseCaseTest.kt
    │   └── CheckAllTasksDoneUseCaseTest.kt
    └── integration/
        └── CheckInFlowIntegrationTest.kt

feature-mushroom/
└── src/test/java/com/mushroom/mushroom/
    ├── rule/
    │   ├── DailyTaskCompleteRuleTest.kt
    │   ├── EarlyCompletionRuleTest.kt
    │   ├── MorningReadingRuleTest.kt
    │   ├── HomeworkMemoRuleTest.kt
    │   ├── HomeworkAtSchoolRuleTest.kt
    │   ├── AllTasksDoneRuleTest.kt
    │   ├── StreakRuleTest.kt
    │   ├── MilestoneScoreRuleTest.kt
    │   └── DeductionRuleEngineTest.kt
    ├── usecase/
    │   ├── DeductMushroomUseCaseTest.kt
    │   ├── AppealDeductionUseCaseTest.kt
    │   └── ReviewAppealUseCaseTest.kt
    └── integration/
        └── MushroomEarnFlowIntegrationTest.kt

system-test/
└── src/test/java/com/mushroom/st/
    ├── STBase.kt
    ├── ST001_DailyCheckinChainTest.kt
    ├── ST002_AllTasksDoneChainTest.kt
    ├── ST003_StreakMilestoneChainTest.kt
    ├── ST004_MilestoneScoreChainTest.kt
    ├── ST005_RewardExchangeChainTest.kt
    ├── ST006_DeductionAppealChainTest.kt
    ├── ST007_DeductionDailyLimitTest.kt
    ├── ST008_TimeRewardPeriodResetTest.kt
    ├── ST009_MorningReadingTemplateTest.kt
    └── ST010_DatabaseMigrationTest.kt

scripts/
├── check-env.sh      ← 环境验证脚本（开发前必跑）
└── run-tests.sh      ← 测试执行与报告生成脚本
```

---

*文档结束*
