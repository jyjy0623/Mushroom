# 蘑菇大冒险 - 测试策略

**版本**：v1.0
**日期**：2026-03-01
**状态**：待审核

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
| 集成测试 | JUnit 5 + Room In-Memory DB | 真实 SQLite，仍在 JVM 运行 |
| ST | JUnit 5 + Room In-Memory DB | 多模块联动，完整业务场景 |
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

---

### 2.2 core-domain / core-data：Mapper 测试

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
// TaskMapperTest
@Test
fun `when_repeatRule_is_Custom_with_multiple_days_should_serialize_and_deserialize_correctly`()

@Test
fun `when_deadline_is_null_should_map_to_null_without_exception`()

// MilestoneMapperTest
@Test
fun `when_milestone_has_multiple_scoring_rules_should_preserve_all_rules`()
```

---

### 2.3 feature-task：Use Case 测试

**文件**：`feature-task/src/test/.../usecase/`

#### CreateTaskUseCaseTest
| 用例 | 场景 |
|------|------|
| `when_repeat_none_should_insert_single_task` | 无重复规则，插入一条记录 |
| `when_repeat_daily_should_generate_instances_for_date_range` | 每日重复，验证生成数量 |
| `when_repeat_weekdays_should_skip_weekends` | 工作日重复，验证周六周日被跳过 |
| `when_repeat_custom_with_mon_wed_fri_should_generate_correct_days` | 自定义星期，验证只生成指定日 |
| `when_repeat_generation_called_twice_should_be_idempotent` | 幂等性：重复调用不产生重复记录 |
| `when_repository_throws_should_return_failure` | 数据库异常传播 |

#### DeleteTaskUseCaseTest
| 用例 | 场景 |
|------|------|
| `when_mode_single_should_delete_only_target_task` | 单条删除，不影响同系列其他任务 |
| `when_mode_all_recurring_should_delete_all_in_series` | 全部删除，同模板ID所有记录 |
| `when_task_not_found_should_return_failure` | 目标任务不存在 |

#### ApplyTaskTemplateUseCaseTest
| 用例 | 场景 |
|------|------|
| `when_template_has_deadline_offset_should_calculate_correct_deadline` | 480分钟偏移 → 08:00 |
| `when_template_is_builtin_should_apply_successfully` | 内置模板正常应用 |
| `when_target_date_already_has_task_from_template_should_skip` | 同日同模板，幂等 |

---

### 2.4 feature-checkin：Use Case 测试

**文件**：`feature-checkin/src/test/.../usecase/`

#### CheckInTaskUseCaseTest（最重要）
| 用例 | 场景 |
|------|------|
| `when_task_has_no_deadline_should_mark_on_time` | deadline=null → ON_TIME_DONE，earlyMinutes=0 |
| `when_checkin_before_deadline_should_mark_early` | 提前完成 → EARLY_DONE，earlyMinutes > 0 |
| `when_checkin_exactly_at_deadline_should_mark_on_time` | 恰好在截止时间 → ON_TIME_DONE |
| `when_checkin_after_deadline_should_mark_on_time` | 超时提交 → ON_TIME_DONE |
| `when_early_by_30_min_should_set_earlyMinutes_to_30` | earlyMinutes 计算精度 |
| `when_checkin_succeeds_should_emit_TaskCheckedIn_event` | 事件发布验证（isEarly=true 携带正确 earlyMinutes） |
| `when_task_not_found_should_return_failure` | 任务不存在 |
| `when_already_checked_in_today_should_return_failure` | 重复打卡防护 |

#### GetStreakUseCaseTest
| 用例 | 场景 |
|------|------|
| `when_has_checkin_today_should_count_from_today` | 今日有打卡，streak 从今天算 |
| `when_no_checkin_today_should_count_from_yesterday` | 今日无打卡，streak 从昨天算 |
| `when_streak_broken_by_one_day_should_return_current_streak` | 中断后当前 streak 正确 |
| `when_no_checkins_ever_should_return_zero` | 全新用户 |

#### CheckAllTasksDoneUseCaseTest
| 用例 | 场景 |
|------|------|
| `when_all_tasks_done_should_emit_AllDailyTasksDone_event` | 全部完成，事件发布 |
| `when_some_tasks_pending_should_not_emit_event` | 未全部完成，不发布 |
| `when_no_tasks_for_date_should_not_emit_event` | 当日无任务，不发布（避免误触） |

---

### 2.5 feature-mushroom：Rule Engine 测试（关键）

**文件**：`feature-mushroom/src/test/.../rule/`

#### DailyTaskCompleteRuleTest
| 用例 | 场景 |
|------|------|
| `when_any_task_completed_should_return_SMALL_x1` | 基础奖励 |
| `when_event_is_not_TaskCompleted_should_return_empty` | 非适用事件 |

#### EarlyCompletionRuleTest（分支覆盖 100%）
| 用例 | 场景 |
|------|------|
| `when_isEarly_false_should_return_empty` | 非提前完成，无额外奖励 |
| `when_early_by_59_min_should_return_SMALL_x1` | < 60 min |
| `when_early_by_60_min_should_return_SMALL_x2` | = 60 min（边界） |
| `when_early_by_61_min_should_return_SMALL_x2` | 60-180 min |
| `when_early_by_180_min_should_return_SMALL_x2` | = 180 min（边界） |
| `when_early_by_181_min_should_return_MEDIUM_x1` | > 180 min |

#### MorningReadingRuleTest
| 用例 | 场景 |
|------|------|
| `when_template_type_is_MORNING_READING_should_return_SMALL_x1_base` | 基础奖励 |
| `when_checkin_within_10min_of_deadline_should_add_SMALL_x1_bonus` | 准时额外奖励 |
| `when_checkin_exactly_at_10min_mark_should_add_bonus` | 边界：恰好 10 分钟 |
| `when_checkin_at_11min_should_not_add_bonus` | 超过 10 分钟，无 bonus |
| `when_template_type_not_morning_reading_should_return_empty` | 非晨读任务 |

#### HomeworkMemoRuleTest
| 用例 | 场景 |
|------|------|
| `when_template_is_HOMEWORK_MEMO_should_return_SMALL_x1` | 基础奖励 |
| `when_streak_reaches_5_days_should_add_MEDIUM_x1_bonus` | 连续 5 天奖励（= 5，边界） |
| `when_streak_is_4_days_should_not_add_bonus` | 未满 5 天 |
| `when_streak_exceeds_5_days_should_still_add_bonus` | > 5 天，bonus 持续触发 |

#### AllTasksDoneRuleTest
| 用例 | 场景 |
|------|------|
| `when_all_daily_tasks_done_should_return_MEDIUM_x1` | 全勤奖励 |
| `when_event_is_not_AllDailyTasksDone_should_return_empty` | 非全勤事件 |

#### StreakRuleTest（分支覆盖 100%）
| 用例 | 场景 |
|------|------|
| `when_streak_is_7_days_should_return_LARGE_x1` | 7 天里程碑 |
| `when_streak_is_30_days_should_return_GOLD_x1` | 30 天里程碑 |
| `when_streak_is_8_days_should_return_empty` | 非里程碑天数，无奖励 |
| `when_streak_is_6_days_should_return_empty` | 未达到 7 天 |

#### MilestoneScoreRuleTest
| 用例 | 场景 |
|------|------|
| `when_score_matches_first_rule_should_return_configured_reward` | 第一档 |
| `when_score_matches_last_rule_should_return_configured_reward` | 最后档 |
| `when_score_below_all_rules_should_return_empty` | 不达标，无奖励 |
| `when_score_on_boundary_minScore_should_include` | 边界：= minScore |
| `when_score_on_boundary_maxScore_should_include` | 边界：= maxScore |
| `when_score_one_below_minScore_should_not_match` | 边界外：minScore-1 |

#### DeductionRuleEngineTest
| 用例 | 场景 |
|------|------|
| `when_today_count_is_below_maxPerDay_should_allow` | 未达上限，可扣 |
| `when_today_count_equals_maxPerDay_should_deny` | 恰好达上限（= maxPerDay，边界） |
| `when_today_count_exceeds_maxPerDay_should_deny` | 超过上限 |
| `when_maxPerDay_is_1_and_no_records_today_should_allow` | maxPerDay=1，今日首次 |

---

### 2.6 feature-mushroom：Use Case 测试

**文件**：`feature-mushroom/src/test/.../usecase/`

#### DeductMushroomUseCaseTest
| 用例 | 场景 |
|------|------|
| `when_config_enabled_and_under_daily_limit_and_balance_sufficient_should_deduct` | Happy Path |
| `when_daily_limit_reached_should_return_failure` | 每日上限拦截 |
| `when_balance_less_than_deduct_amount_should_deduct_remaining_balance` | 余额不足时扣到 0 |
| `when_balance_is_zero_should_return_failure_not_deduct` | 余额为 0，不产生记录 |
| `when_config_is_disabled_should_return_failure` | 扣分项未启用 |
| `when_deduction_succeeds_should_emit_MushroomDeducted_event` | 事件发布 |

#### AppealDeductionUseCaseTest
| 用例 | 场景 |
|------|------|
| `when_record_exists_and_status_none_should_set_pending` | 正常申诉 |
| `when_record_already_pending_should_return_failure` | 重复申诉 |
| `when_record_already_approved_should_return_failure` | 已审批，不可再申诉 |

#### ReviewAppealUseCaseTest
| 用例 | 场景 |
|------|------|
| `when_approved_should_refund_mushrooms_and_set_approved` | 批准：余额增加，状态更新 |
| `when_rejected_should_not_refund_and_set_rejected` | 拒绝：余额不变 |
| `when_record_not_pending_should_return_failure` | 非 PENDING 状态，不可审批 |

---

### 2.7 feature-reward：Use Case 测试

**文件**：`feature-reward/src/test/.../usecase/`

#### ExchangeMushroomsUseCaseTest（PHYSICAL）
| 用例 | 场景 |
|------|------|
| `when_balance_sufficient_should_unlock_puzzle_piece` | 正常兑换 |
| `when_only_one_piece_remaining_should_complete_puzzle` | 最后一块，拼图完成 |
| `when_balance_insufficient_should_return_failure` | 余额不足 |
| `when_puzzle_already_complete_should_return_failure` | 拼图已完成，不可再兑换 |
| `when_exchange_succeeds_should_emit_RewardPuzzleUpdated_event` | 事件发布 |

#### ExchangeMushroomsUseCaseTest（TIME_BASED）
| 用例 | 场景 |
|------|------|
| `when_within_weekly_limit_should_succeed` | 每周额度内 |
| `when_exchange_would_exceed_weekly_limit_should_fail` | 超周上限 |
| `when_within_cooldown_period_should_fail` | 冷却期内 |
| `when_cooldown_just_expired_should_succeed` | 冷却期恰好结束（边界） |
| `when_new_week_starts_should_reset_usage` | 周期重置后可再兑换 |
| `when_require_parent_confirm_and_confirmed_should_succeed` | 家长确认通过 |
| `when_require_parent_confirm_and_denied_should_fail` | 家长拒绝 |

#### PuzzleCutterTest
| 用例 | 场景 |
|------|------|
| `when_20_pieces_should_produce_4x5_grid` | 20 块 → 4×5 |
| `when_25_pieces_should_produce_5x5_grid` | 25 块 → 5×5（正方形） |
| `when_12_pieces_should_produce_3x4_grid` | 12 块 → 3×4 |
| `when_1_piece_should_produce_1x1_grid` | 边界：1 块 |

---

### 2.8 feature-milestone：Use Case 测试

**文件**：`feature-milestone/src/test/.../usecase/`

#### RecordMilestoneScoreUseCaseTest
| 用例 | 场景 |
|------|------|
| `when_score_90_and_has_matching_rule_should_emit_MilestoneScored_event` | 90分，触发奖励 |
| `when_score_59_below_all_rules_should_record_score_but_not_emit_event` | 59分，记录但不触发奖励 |
| `when_score_60_exactly_matches_third_tier_should_emit_event` | 边界：60分匹配第三档 |
| `when_score_80_is_on_boundary_of_second_tier_should_match_second_tier` | 边界：80分 |
| `when_score_100_should_match_first_tier` | 满分 |
| `when_score_negative_should_return_failure` | 非法分数（< 0） |
| `when_score_over_100_should_return_failure` | 非法分数（> 100） |
| `when_milestone_already_scored_should_return_failure` | 重复录入防护 |
| `when_parent_not_authenticated_should_return_failure` | 家长权限验证 |

---

## 三、集成测试（Integration Test）

集成测试使用 **Room In-Memory Database**，验证 Repository 实现和 DAO 查询逻辑的正确性。运行在 JVM 上（`robolectric` 或直接 JVM Room），速度比 UT 慢但比仪器测试快。

### 3.1 core-data 集成测试

**文件**：`core-data/src/test/.../integration/`

#### MushroomLedgerIntegrationTest
| 用例 | 场景 |
|------|------|
| `given_earn_and_spend_records_should_return_correct_balance` | 收支合算：EARN 5 - SPEND 2 = 3 |
| `given_deduct_more_than_earned_balance_should_not_go_negative` | 余额保底验证 |
| `given_multiple_levels_should_return_balance_per_level` | 多等级余额各自独立 |
| `given_no_transactions_for_level_should_return_zero_not_null` | 无记录等级返回 0 |

#### DeductionRecordIntegrationTest
| 用例 | 场景 |
|------|------|
| `given_two_records_same_config_same_day_should_count_as_2` | 当日计数 |
| `given_records_across_midnight_should_count_separately` | 跨天计数隔离 |
| `given_maxPerDay_1_and_one_record_today_should_deny_second` | 上限拦截 |

#### CheckInStreakIntegrationTest
| 用例 | 场景 |
|------|------|
| `given_5_consecutive_days_checkin_should_return_streak_5` | 连续天数 |
| `given_checkin_gap_on_day_3_should_return_streak_2` | 中断后重计 |
| `given_no_checkin_today_should_count_from_yesterday` | 今日无打卡从昨日算 |

#### TimeRewardUsageIntegrationTest
| 用例 | 场景 |
|------|------|
| `given_weekly_reward_and_new_week_should_reset_usage` | 周期重置 |
| `given_monthly_reward_and_new_month_should_reset_usage` | 月重置 |
| `given_same_week_multiple_exchanges_should_accumulate_usage` | 同周累加 |

---

### 3.2 模块内 Use Case + Repository 集成测试

**文件**：各 feature 模块 `src/test/.../integration/`

这类测试使用真实 Room In-Memory DB，Use Case 注入真实 Repository 实现，验证完整的数据读写流程。

| 测试文件 | 验证内容 |
|---------|---------|
| `CheckInFlowIntegrationTest` | 打卡 → 数据库记录 → Flow 发射 → streak 计算 |
| `MushroomEarnFlowIntegrationTest` | 触发奖励 → 写入账本 → 余额 Flow 更新 |
| `DeductionFlowIntegrationTest` | 扣分 → 写记录 → 余额扣减 → 申诉 → 退还 |
| `RewardExchangeFlowIntegrationTest` | 兑换蘑菇 → 拼图进度更新 → Flow 发射完成通知 |
| `MilestoneScoreFlowIntegrationTest` | 录入成绩 → 奖励写入 → 里程碑状态更新 |

---

## 四、系统测试（ST）

ST 测试多个模块联动的完整业务场景，使用 In-Memory DB + 真实 EventBus，不 Mock 任何业务组件（只 Mock 外部依赖：ParentGateway、NotificationService、日期时间）。

### 4.1 ST 场景列表

#### ST-001：日常打卡完整链路
```
前置：创建任务（deadline = 今天 20:00），当前时间 19:00
步骤：
  1. 用户点击打卡
  2. CheckInTaskUseCase 执行
  3. 判定 isEarly=true，earlyMinutes=60
  4. 发布 TaskCheckedIn 事件
  5. MushroomRewardEngine 接收事件
  6. 执行规则：DailyTaskCompleteRule + EarlyCompletionRule（60min = SMALL×2）
  7. EarnMushroomUseCase 写入账本
  8. MushroomBalance Flow 更新
验证：
  - CheckIn 记录 isEarly=true、earlyMinutes=60
  - 账本含 2 条 EARN 记录（TASK + EARLY_BONUS）
  - 余额 +3 个小蘑菇（1+2）
```

#### ST-002：全勤奖励触发链路
```
前置：当日有 3 个任务，已完成 2 个
步骤：
  1. 完成第 3 个任务打卡
  2. CheckAllTasksDoneUseCase 检测全部完成
  3. 发布 AllDailyTasksDone 事件
  4. AllTasksDoneRule 触发：MEDIUM×1
验证：
  - 账本含 CHECKIN_STREAK 来源的 EARN 记录
  - 余额含 +1 中蘑菇
```

#### ST-003：连续打卡里程碑链路
```
前置：已连续打卡 6 天
步骤：
  1. 第 7 天完成打卡
  2. GetStreakUseCase 检测到 streak=7
  3. 发布 StreakReached(7) 事件
  4. StreakRule 触发：LARGE×1
验证：
  - 账本含 CHECKIN_STREAK 来源的 LARGE 等级 EARN 记录
  - 余额 +1 大蘑菇
```

#### ST-004：里程碑成绩录入链路
```
前置：已创建里程碑（MINI_TEST），90-100 → MEDIUM×2
步骤：
  1. 家长录入成绩 92 分
  2. RecordMilestoneScoreUseCase 找到匹配规则
  3. 发布 MilestoneScored(score=92) 事件
  4. MilestoneScoreRule 触发：MEDIUM×2
验证：
  - 里程碑状态 = REWARDED
  - 账本含 MILESTONE 来源 EARN 记录 2 条
  - 余额 +2 中蘑菇
```

#### ST-005：蘑菇兑换拼图链路
```
前置：奖品（需小蘑菇×10，拼图10块），余额小蘑菇 5 个，已解锁 5 块
步骤：
  1. 用户兑换小蘑菇 5 个
  2. ExchangeMushroomsUseCase 验证余额、时长限制（PHYSICAL 无时长）
  3. SpendMushroomUseCase 写 SPEND 记录
  4. 更新 puzzle_pieces_unlocked = 10
  5. 发布 RewardPuzzleUpdated 事件
验证：
  - SPEND 记录存在
  - 余额 -5 小蘑菇
  - PuzzleProgress.isCompleted = true
  - 事件中 isCompleted = true（触发庆祝动画）
```

#### ST-006：扣分 + 申诉 + 退还链路
```
步骤：
  1. 家长记录扣分（忘带作业，SMALL×2）
  2. 验证余额 -2 小蘑菇
  3. 学生发起申诉
  4. 验证申诉状态 = PENDING
  5. 家长批准申诉
  6. 验证余额 +2 小蘑菇（APPEAL_REFUND 来源）
  7. 验证申诉状态 = APPROVED
验证：
  - 账本含 DEDUCT 和 APPEAL_REFUND 两条记录
  - 最终余额与初始相同
```

#### ST-007：扣分每日上限拦截
```
前置：扣分项 maxPerDay=1，今日已扣一次
步骤：
  1. 家长尝试第二次扣分
验证：
  - DeductMushroomUseCase 返回 Failure
  - 账本无新增记录
  - 余额不变
```

#### ST-008：时长型奖品周期重置
```
前置：TIME_BASED 奖品（每周90分钟上限），当周已用 90 分钟
步骤：
  1. 当周再次兑换 → 应失败
  2. 时间推进到下周一
  3. 再次兑换 → 应成功
验证：
  - 当周兑换被拒绝
  - 新周兑换成功，used_minutes 从 0 开始
```

#### ST-009：晨读模板完整奖励链路
```
前置：晨读模板，deadline 08:00，当前时间 07:52（8 分钟内）
步骤：
  1. 打卡
验证：
  - 基础奖励 SMALL×1（templateType=MORNING_READING）
  - 准时额外奖励 SMALL×1（8 < 10 分钟）
  - 账本含 2 条 EARN 记录
```

#### ST-010：数据版本兼容（Migration 测试）
```
步骤：
  1. 建立版本 N 数据库，写入测试数据
  2. 执行 Migration N→N+1
  3. 读取数据，验证原有数据未丢失
  4. 验证新增字段有正确默认值
验证：
  - 每次 Migration 必须有对应测试
  - 重要业务数据（任务、打卡、蘑菇）迁移前后一致
```

---

### 4.2 ST 执行时机

| 时机 | 执行内容 |
|------|---------|
| Phase 1 完成时 | ST-001、ST-002、ST-003 |
| Phase 2 完成时 | ST-001 ~ ST-009 全部 |
| Phase 3 完成时 | ST-001 ~ ST-010（含 Migration）全量回归 |
| 每次合并 develop | ST-001 ~ ST-003 快速回归（约 2 分钟） |

---

## 五、测试与开发集成节奏

### 5.1 模块开发节奏（每个 Sprint）

```
Day 1-2：设计评审 + UT 用例设计
  ↓
Day 3-6：Use Case 开发 + 同步写 UT（TDD 或同步）
  ↓
Day 7：UT 全部通过，Rule Engine 100% 分支覆盖
  ↓
Day 8：Repository 集成测试通过
  ↓
Day 9-10：UI 联调、代码审查
  ↓
模块 DONE：合并 feature 分支
```

### 5.2 合并准入条件

```
feature → develop 条件：
  ✅ 本模块 UT 全部通过
  ✅ Rule Engine 100% 分支覆盖
  ✅ Use Case 核心逻辑 ≥ 80% 行覆盖
  ✅ 无编译 Error 和核心模块 Warning
  ✅ 日志 Tag 使用常量（非字符串字面量）

develop → release 条件：
  ✅ 所有模块 UT 通过
  ✅ Phase 对应 ST 场景全部通过
  ✅ 无 P0/P1 bug
```

### 5.3 CI 检查项（本地执行，推送前）

```bash
# 运行所有 UT
./gradlew test

# 运行指定模块 UT
./gradlew :feature-checkin:test

# 运行覆盖率报告
./gradlew jacocoTestReport

# 运行集成测试
./gradlew integrationTest

# 运行 Lint
./gradlew lint
```

---

## 六、缺陷管理

### 缺陷级别定义

| 级别 | 定义 | 要求 |
|------|------|------|
| P0 | 应用崩溃、数据丢失、蘑菇余额错误 | 立即修复，不可合并 |
| P1 | 核心功能不可用（打卡失败、奖励不触发） | 当前 Sprint 修复 |
| P2 | 功能异常但有 workaround | 下个 Sprint 修复 |
| P3 | UI 问题、文案错误 | Phase 3 打磨时修复 |

### 缺陷发现后处理

1. 在对应模块增加复现该缺陷的 UT（防止回归）
2. 修复代码
3. 验证 UT 和相关 ST 通过
4. 提交时 commit message 注明 `fix: <描述>`

---

## 七、测试文件结构

```
各模块测试文件结构（以 feature-checkin 为例）：

feature-checkin/
└── src/
    └── test/java/com/mushroom/checkin/
        ├── usecase/
        │   ├── CheckInTaskUseCaseTest.kt
        │   ├── GetCheckInHistoryUseCaseTest.kt
        │   ├── GetStreakUseCaseTest.kt
        │   └── CheckAllTasksDoneUseCaseTest.kt
        └── integration/
            └── CheckInFlowIntegrationTest.kt

feature-mushroom/
└── src/
    └── test/java/com/mushroom/mushroom/
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
```

---

*文档结束*
