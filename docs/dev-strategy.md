# 蘑菇大冒险 - 开发策略

**版本**：v1.0
**日期**：2026-03-01
**状态**：待审核

---

## 一、总体开发思路

采用**纵向切片（Vertical Slice）** 的开发方式：每个 Phase 交付一个可独立运行的功能闭环，而不是横向地先搭完所有基础设施再做功能。这样的好处是每个 Phase 结束时都能看到可运行的 APP，问题发现得更早。

```
Phase 1 → Phase 2 → Phase 3 → Phase 4
  可运行     核心完整    打磨稳定    V2扩展
```

开发与测试**同步推进**，不等到功能完成才写测试。每个模块开发过程中同步完成 UT，模块交付时 UT 全部通过，再进入集成阶段。详细的测试节奏见《测试策略》文档。

---

## 二、分 Phase 开发计划

### Phase 1：可运行的 MVP（约 4-6 周）

**目标**：完成核心功能闭环——创建任务、完成打卡、获得蘑菇、看到余额。

#### Sprint 1（第 1-2 周）：基础设施
| 任务 | 产出 | 备注 |
|------|------|------|
| 项目脚手架 | Gradle 模块结构、基础依赖配置 | 建立 core-* / feature-* / service-* 目录结构 |
| core-logging 实现 | MushroomLogger、LogFileWriter、Debug/ReleaseLogWriter | 其余所有模块依赖此模块，优先实现 |
| core-domain 实现 | 所有实体类、Repository 接口、AppEventBus | 纯 Kotlin，无 Android 依赖 |
| core-data 骨架 | Room 数据库、13 张表创建、DAO 接口、Mapper | 不含复杂业务逻辑 |
| DI 框架搭建 | Hilt Module，LoggingModule、DatabaseModule | 确保注入链路通畅 |
| 导航框架 | Navigation Compose、AppDestination 定义、空屏占位 | |

**交付标准**：APP 可启动，Hilt 注入无报错，数据库创建成功，日志写入可见。

#### Sprint 2（第 3-4 周）：任务模块
| 任务 | 产出 |
|------|------|
| feature-task 全部 Use Case | GetDailyTasksUseCase、CreateTaskUseCase、DeleteTaskUseCase、CopyTasksUseCase、ApplyTaskTemplateUseCase |
| TaskRepository 实现 | TaskRepositoryImpl + TaskMapper |
| DailyTaskListScreen | 任务列表、添加、删除、切换日期 |
| TaskEditScreen | 任务表单、学科选择、截止时间、重复规则 |
| TaskTemplateScreen | 内置模板列表、一键应用 |
| 预设数据 Seed | 3 个内置任务模板写入 |

**交付标准**：可以创建、编辑、删除任务；重复任务可以展开；模板可以应用到某天。

#### Sprint 3（第 5-6 周）：打卡 + 蘑菇基础
| 任务 | 产出 |
|------|------|
| feature-checkin 全部 Use Case | CheckInTaskUseCase、GetCheckInHistoryUseCase、GetStreakUseCase、CheckAllTasksDoneUseCase |
| CheckInRepository 实现 | |
| feature-mushroom 奖励引擎 | RewardRuleEngine、全部 9 条规则、EarnMushroomUseCase |
| MushroomRepository 实现 | |
| 首页聚合 | 今日任务列表、打卡按钮、蘑菇余额展示 |
| MushroomLedgerScreen | 余额 + 账本列表 |

**交付标准**：完成任务打卡后蘑菇自动增加，账本有记录；提前完成有额外奖励；连续打卡 streak 正确计算。

---

### Phase 2：核心功能完善（约 3-4 周）

**目标**：补完奖品、里程碑、扣分、家长权限全部功能。

#### Sprint 4（第 7-8 周）：奖品兑换
| 任务 | 产出 |
|------|------|
| feature-reward 全部 Use Case | ExchangeMushroomsUseCase、GetPuzzleProgressUseCase、GetTimeRewardBalanceUseCase、ClaimRewardUseCase |
| PuzzleCutter 算法实现 | 图片切割、网格计算 |
| RewardRepository 实现 | |
| RewardListScreen | 奖品网格、进度覆盖层 |
| RewardDetailScreen | 拼图视图（PHYSICAL）/ 时长视图（TIME_BASED） |

#### Sprint 5（第 9-10 周）：里程碑 + 扣分 + 家长模式
| 任务 | 产出 |
|------|------|
| feature-milestone 全部 Use Case | CreateMilestoneUseCase、RecordMilestoneScoreUseCase、GetMilestonesUseCase |
| MilestoneRepository 实现 | |
| MilestoneListScreen、MilestoneEditScreen | |
| 扣分机制 | DeductMushroomUseCase、AppealDeductionUseCase、ReviewAppealUseCase |
| DeductionRepository 实现 | |
| DeductionRecordScreen、DeductionHistoryScreen、DeductionConfigScreen | |
| ParentGateway 实现 | PIN 设置、Android Keystore 存储、PIN 验证 |
| 家长权限守卫接入 | 所有需要家长权限的 Use Case 接入 ParentGateway |

#### Sprint 6（第 11 周）：统计模块
| 任务 | 产出 |
|------|------|
| feature-statistics 全部 Use Case | GetCheckInStatisticsUseCase、GetMushroomStatisticsUseCase、GetScoreStatisticsUseCase |
| StatisticsScreen | 三个 Tab（打卡、蘑菇、成绩）|
| Vico 图表接入 | 折线图、热力图 |

---

### Phase 3：打磨与稳定（约 2 周）

| 任务 | 内容 |
|------|------|
| 动画优化 | 蘑菇收集飞入动画、拼图解锁动画（400ms/块）、庆祝动画（3s）、扣分闪红 |
| 数据导出备份 | BackupPayload JSON 序列化、导出/导入流程 |
| 日志导出完善 | LogExporter 完整实现（CLAUDE_ANALYSIS_BRIEF.md 生成、error_index.txt）|
| Settings 页面 | 诊断与帮助入口、家长配置入口 |
| 性能检查 | 首屏 < 2s、页面切换 60fps、DB 查询 < 100ms |
| 全量回归测试 | 运行全部 UT + ST |

---

### Phase 4：V2 扩展（未来规划）

- 语音输入 + 大模型任务生成（替换 `TaskGeneratorService` 实现，调用方不变）
- 云端多设备同步（升级 Repository 实现，增加 RemoteDataSource）

---

## 三、分支与版本管理策略

### 分支模型

```
main              ← 发布分支，只接受 release/* 合并，保持可发布状态
develop           ← 集成分支，所有 feature 合并至此
feature/<name>    ← 功能分支，从 develop 切出，完成后 PR 合并回 develop
release/v*        ← 发布准备分支，从 develop 切出，修复 bug 后合并 main + develop
```

### 合并条件

**feature → develop** 需满足：
- 本模块全部 UT 通过（`./gradlew :<module>:test`）
- 无编译警告（核心模块）
- PR 经过 code review（至少自审 checklist）

**develop → release** 需满足：
- 所有模块 UT 通过
- 集成测试（ST）指定场景通过
- 无已知 P0/P1 bug

### Commit 规范

```
<type>(<scope>): <subject>

type:
  feat     新功能
  fix      bug 修复
  test     测试代码
  refactor 重构（无功能变化）
  docs     文档
  chore    构建/依赖变更

scope: 模块名（core-data, feature-checkin, feature-mushroom 等）

示例：
feat(feature-checkin): implement CheckInTaskUseCase with early completion logic
test(feature-mushroom): add unit tests for EarlyCompletionRule tiering
fix(core-data): handle null balance for new mushroom levels
```

---

## 四、代码质量标准

### 必须满足（门禁条件）

| 指标 | 标准 |
|------|------|
| UT 覆盖率（核心 Use Case） | ≥ 80% 行覆盖 |
| UT 覆盖率（Rule Engine） | 100% 分支覆盖 |
| 编译警告 | 核心模块 0 警告 |
| Lint 检查 | 无 Error 级别问题 |

### 代码规范要点

- **Use Case**：统一 `runCatching { } .onFailure { MushroomLogger.e(...) }` 模板
- **Repository**：读操作返回 `Flow<T>`，写操作返回 `suspend fun Result<T>`
- **ViewModel**：单一 `UiState` data class + `Event` sealed class，不直接暴露 MutableState
- **Tag 规范**：每个模块使用固定 Tag 常量（见 core-logging 文档），不使用字符串字面量
- **不允许**：直接调用 `android.util.Log`，直接跨模块访问其他模块的 DAO

---

## 五、开发与测试集成节奏

详见《测试策略》文档。简要节奏如下：

```
代码开发  ────────────────────────────────────
            ↑ 同步         ↑ 模块完成时
UT 编写   ──┘              │
                           ↓
模块 UT 全部通过  →  进入下一 Sprint
                           ↓
Phase 完成  →  运行 ST 回归
                           ↓
ST 通过  →  合并 develop  →  下一 Phase
```

---

## 六、依赖关系与开发顺序约束

```
core-logging        ← 第一个实现（所有模块依赖）
    ↓
core-domain         ← 第二个（定义所有接口和实体）
    ↓
core-data           ← 第三个（实现 Repository）
    ↓
feature-task        ← 无跨模块事件依赖，可独立开发
feature-checkin     ← 依赖 feature-task 产生的任务数据
feature-mushroom    ← 依赖 checkin 事件（TaskCheckedIn）
feature-reward      ← 依赖 mushroom（SpendMushroomUseCase）
feature-milestone   ← 依赖 mushroom（奖励发放）
feature-statistics  ← 依赖所有模块的 Repository（只读）
```

---

*文档结束*
