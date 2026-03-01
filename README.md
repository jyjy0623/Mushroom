# 蘑菇大冒险

一款面向中小学生的学习激励与成长记录 Android 应用。通过游戏化的「蘑菇奖励」体系，将枯燥的学习任务转化为有趣的成就收集过程，帮助孩子建立良好的学习习惯，同时让家长参与到奖励兑换的互动中。

---

## 功能特性

### 任务管理
- 手动创建每日学习任务，支持按学科分类、设置截止时间和重复周期
- 内置任务模板：晨读打卡、作业备忘录记录、在校完成作业等
- 支持任务复制到其他日期，一键应用模板

### 打卡记录
- 任务完成后一键打卡，自动记录完成时间
- **提前完成奖励**：在截止时间前打卡，按提前幅度自动给予额外蘑菇奖励
- 日历视图展示打卡历史，实时显示连续打卡天数（streak）
- 家长可开启审核模式核验打卡

### 蘑菇奖励体系

| 等级 | 名称 | 参考兑换价值 |
|------|------|------------|
| ⭐ | 小蘑菇 | 1 点 |
| ⭐⭐ | 中蘑菇 | 5 点 |
| ⭐⭐⭐ | 大蘑菇 | 25 点 |
| ⭐⭐⭐⭐ | 金蘑菇 | 100 点 |
| ⭐⭐⭐⭐⭐ | 传说蘑菇 | 500 点 |

- 完成任务、全勤、连续打卡、里程碑达标均可获得蘑菇
- **扣分机制**：家长可记录忘带作业、未订正试卷等不良习惯，从余额中扣除蘑菇（默认关闭，需手动开启）
- 学生可对扣分记录发起申诉，家长审核确认

### 奖品兑换（拼图系统）
- 家长添加奖品（高达模型、游戏时间、外出游玩等），上传奖品图片
- 奖品图片自动切割为 N 块拼图，兑换蘑菇逐步解锁，全部解锁后触发庆祝动画
- **时长型奖品**：看电视、玩游戏等设有每周/每月额度上限，游戏兑换需家长二次确认

### 里程碑
- 支持过程小测验、每周自测、学校测验、期中/期末大考五类里程碑
- 手动录入成绩，系统按配置的分数段自动发放蘑菇奖励
- 成绩录入后不可修改，需家长权限删除

### 数学试点三级奖励方案
- **看电视**：小蘑菇 ×3 换 30 分钟，约 2-3 天可兑换一次，高频正反馈
- **玩游戏**：中/大蘑菇兑换，需约两周全勤积累，每次需家长确认
- **高达模型**：金/传说蘑菇与学期里程碑深度绑定，约一学期完成拼图，强成就感

### 数据统计
- 学习时长趋势（周/月折线图）
- 蘑菇收支账本（加分绿色/扣分红色，账目透明）
- 连续打卡天数记录
- 各科成绩趋势分析

---

## 技术架构

### 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin 2.0+ |
| UI | Jetpack Compose + Material3 |
| 架构 | MVVM + Clean Architecture |
| 依赖注入 | Hilt (Dagger) |
| 数据库 | Room (SQLite) |
| 异步 | Kotlin Coroutines + Flow |
| 导航 | Navigation Compose |
| 图表 | Vico |
| 图片加载 | Coil |

### 模块结构

```
mushroom-app/
├── app/                        # 应用入口、DI 装配、NavGraph
├── core/
│   ├── core-logging/           # 统一日志（MushroomLogger、文件滚动、日志导出）
│   ├── core-domain/            # 共享实体、Repository 接口、服务接口
│   ├── core-data/              # Room 数据库、DAO、Repository 实现
│   └── core-ui/                # 公共 Compose 组件库
├── feature/
│   ├── feature-task/           # 任务管理 + 模板
│   ├── feature-checkin/        # 打卡记录
│   ├── feature-mushroom/       # 蘑菇体系 + 扣分 + 奖励引擎
│   ├── feature-reward/         # 奖品兑换 + 拼图系统
│   ├── feature-milestone/      # 里程碑 + 成绩录入
│   └── feature-statistics/     # 数据统计
└── service/
    ├── service-task-generator/ # 任务生成（V1 手动 / V2 AI 接口预留）
    └── service-notification/   # 通知提醒
```

### 架构原则

- **Clean Architecture 三层**：Presentation → Domain ← Data，UI 不直接访问数据库
- **离线优先**：所有数据存储于本地 Room，无网络时功能完整可用
- **数据版本兼容**：严格 Room Migration 策略，禁止 `fallbackToDestructiveMigration()`，每次版本升级只增量变更，不破坏用户数据
- **事件总线解耦**：各 feature 模块通过 `AppEventBus (SharedFlow)` 通信，不互相直接依赖
- **可配置性优先**：蘑菇等级、奖励规则、扣分配置均存储在数据库，支持运行时修改，不硬编码业务参数
- **V2 扩展预留**：`TaskGeneratorService` 接口 V1 手动实现，V2 直接替换为 AI 实现，调用方不变

---

## 日志与诊断

应用内置结构化日志系统（`core-logging`），支持用户将日志导出为 ZIP 包，**可直接输入 Claude Code CLI 自动分析**。

### 导出包结构

```
mushroom_diagnostics_20260301.zip
├── CLAUDE_ANALYSIS_BRIEF.md   # 分析入口：Tag 索引、推荐分析策略（Claude 首先读取）
├── diagnostic_summary.txt     # 设备信息、应用版本、蘑菇余额等状态摘要
├── error_index.txt            # 预提取所有 ERROR/WARN 行（含文件名:行号）
└── logs/
    ├── mushroom_log_20260228.txt
    └── mushroom_log_20260301.txt
```

### 使用方式

解压 ZIP 包后，在终端执行：

```bash
claude "请分析 CLAUDE_ANALYSIS_BRIEF.md 和 error_index.txt，告诉我应用发生了什么问题"
```

Claude Code CLI 读取 `CLAUDE_ANALYSIS_BRIEF.md` 后即可了解日志结构和 Tag 含义，无需用户额外解释，直接输出问题定位结论。

### 日志策略

| 级别 | Debug 构建 | Release 构建 |
|------|-----------|------------|
| VERBOSE / DEBUG | Logcat | 不输出 |
| INFO | Logcat + 文件 | 不输出 |
| WARN | Logcat + 文件 | 文件 |
| ERROR | Logcat + 文件 | Logcat + 文件 |

Release 版本只保留 WARN 和 ERROR，总日志文件上限 512 KB，保留最近 2 天。

---

## 文档索引

| 文档 | 说明 |
|------|------|
| [需求文档](docs/requirements.md) | 完整功能需求（v1.3，已审核通过） |
| [总体架构设计](docs/architecture.md) | 架构原则、技术选型、模块划分、接口定义 |
| [开发策略](docs/dev-strategy.md) | 分 Phase 开发计划、Sprint 划分、分支策略、代码规范 |
| [测试策略](docs/test-strategy.md) | UT/集成测试/ST 用例设计、覆盖率要求、测试与开发集成节奏 |
| [core-logging 模块设计](docs/modules/00-core-logging.md) | 日志系统详细设计 |
| [core-domain 模块设计](docs/modules/01-core-domain.md) | 领域实体与接口定义 |
| [core-data 模块设计](docs/modules/02-core-data.md) | 数据库与 DAO 设计 |
| [core-ui 模块设计](docs/modules/03-core-ui.md) | 公共 UI 组件 |
| [feature-task 模块设计](docs/modules/04-feature-task.md) | 任务管理 |
| [feature-checkin 模块设计](docs/modules/05-feature-checkin.md) | 打卡记录 |
| [feature-mushroom 模块设计](docs/modules/06-feature-mushroom.md) | 蘑菇奖励体系 |
| [feature-reward 模块设计](docs/modules/07-feature-reward.md) | 奖品兑换与拼图 |
| [feature-milestone 模块设计](docs/modules/08-feature-milestone.md) | 里程碑与成绩 |
| [feature-statistics 模块设计](docs/modules/09-feature-statistics.md) | 数据统计 |

---

## 开发路线图

### Phase 1 — MVP
项目脚手架 → 数据库所有表 → 任务 CRUD → 每日打卡 → 蘑菇奖励引擎 → 首页聚合

### Phase 2 — 核心功能完善
拼图兑换系统 → 里程碑成绩录入 → 扣分与申诉 → 家长 PIN 保护 → 统计图表

### Phase 3 — 打磨
动画优化 → 数据导出备份 → 全面测试

### Phase 4 — V2（规划中）
语音输入 + 大模型任务生成 → 云端多设备同步

---

## 目标用户

| 角色 | 描述 |
|------|------|
| 主用户（学生） | 中小学生，完成每日任务、收集蘑菇、兑换奖品 |
| 辅助用户（家长） | 设定任务、审核打卡、管理奖品、录入成绩、记录扣分 |
