# 蘑菇大冒险 - 开发策略

**版本**：v1.1
**日期**：2026-03-01
**状态**：待审核

**变更记录**：
| 版本 | 日期 | 变更内容 |
|------|------|---------|
| v1.0 | 2026-03-01 | 初版 |
| v1.1 | 2026-03-01 | 新增：开发环境搭建与验证（Sprint 0）；与测试策略的环境检查脚本联动 |

---

## 零、开发环境搭建（Sprint 0）

**所有开发工作在 Sprint 1 之前必须完成本章，并通过环境验证脚本。** 环境搭建只需做一次，但每位新加入的开发者都必须独立完成。

### 0.1 硬件与系统要求

| 项目 | 最低要求 | 推荐 |
|------|---------|------|
| 内存 | 8 GB | 16 GB |
| 磁盘空闲空间 | 20 GB | 40 GB（含 Android SDK、Gradle 缓存、模拟器镜像） |
| 操作系统 | Windows 10 / macOS 12 / Ubuntu 20.04 | 最新稳定版 |
| 网络 | 首次 Gradle 依赖下载需要访问 Maven Central、Google Maven | 建议使用镜像加速（见 0.4） |

### 0.2 工具安装步骤

**Step 1：安装 JDK 17**
```bash
# macOS（使用 Homebrew）
brew install openjdk@17
# Windows：从 https://adoptium.net 下载 Temurin JDK 17 安装包
# Linux
sudo apt install openjdk-17-jdk
```

**Step 2：安装 Android Studio**
- 下载地址：https://developer.android.com/studio（Hedgehog 或更新版本）
- 安装完成后，打开 Android Studio → SDK Manager，安装：
  - **SDK Platform**：Android 14.0 (API Level 34)
  - **SDK Build-Tools**：34.0.0
  - **Android Emulator**（用于手动验收测试，非 UT 必需）

**Step 3：配置环境变量**
```bash
# 追加到 ~/.bashrc 或 ~/.zshrc（macOS/Linux）
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=$HOME/Library/Android/sdk          # macOS
# export ANDROID_HOME=$HOME/Android/Sdk               # Linux
# Windows 通过"系统属性 → 环境变量"设置，值为 C:\Users\<user>\AppData\Local\Android\Sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/tools/bin

# 使配置生效
source ~/.bashrc
```

**Step 4：克隆项目并初始化**
```bash
git clone https://github.com/jyjy0623/Mushroom.git
cd Mushroom

# 授权脚本执行权限（macOS/Linux）
chmod +x scripts/check-env.sh
chmod +x scripts/run-tests.sh

# 首次同步依赖（约 5-10 分钟，需要网络）
./gradlew dependencies --configuration debugRuntimeClasspath
```

### 0.3 环境验证（必须通过）

```bash
# 运行环境检查脚本
./scripts/check-env.sh
```

期望输出（全部 ✅）：
```
========================================
  蘑菇大冒险 - 环境检查
========================================

[ JDK ]
  ✅  JDK 版本 = 17
  ✅  javac 可用

[ Android SDK ]
  ✅  ANDROID_HOME 已设置
  ✅  platform android-34
  ✅  build-tools 34.0.0
  ✅  adb 可用

[ Gradle ]
  ✅  Gradle Wrapper 可用

[ 构建验证 ]
  ✅  assembleDebug 成功

[ 测试环境 ]
  ✅  UT 可运行（core-domain）
  ✅  UT 可运行（core-data）

========================================
  结果：全部通过 ✅  (10 项)
  环境就绪，可以开始开发。
========================================
```

如有 ❌ 项，按错误提示修复后重新运行，**直到全部通过才能进入 Sprint 1**。

### 0.4 国内镜像加速（可选但推荐）

在项目根目录 `settings.gradle.kts` 中添加镜像仓库，避免依赖下载过慢：

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        mavenCentral()
        google()
    }
}
```

### 0.5 IDE 推荐配置

打开 Android Studio 后建议配置：

| 配置项 | 路径 | 推荐值 |
|-------|------|-------|
| Gradle JVM | Settings → Build → Gradle → Gradle JVM | JDK 17 |
| 编码 | Settings → Editor → File Encodings | UTF-8（所有选项） |
| 行尾 | Settings → Editor → Code Style → Line separator | Unix (\n) |
| 自动导入 | Settings → Editor → Auto Import | 全部勾选 |

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
