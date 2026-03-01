# core-data 模块详细设计

蘑菇大冒险应用 core-data 模块详细设计。

该模块是数据层实现，包含 Room 数据库、所有 DAO、DB 实体定义（与 Domain 实体分离）、Mapper、Repository 实现。

---

## 一、模块职责与边界

**职责**

- 实现 core-domain 定义的所有 Repository 接口
- 管理 Room 数据库、DAO、实体映射
- 提供数据库预置数据初始化（内置模板、扣分配置、蘑菇等级配置）
- 通过 Hilt Module 将 Repository 实现绑定到接口

**边界**

- 不包含任何业务逻辑（业务逻辑在 Domain 层）
- 不直接被 feature 模块依赖（feature 模块通过 core-domain 的接口访问数据）

---

## 二、数据库配置

```kotlin
@Database(
    entities = [
        TaskEntity::class, TaskTemplateEntity::class,
        CheckInEntity::class,
        MushroomLedgerEntity::class, MushroomConfigEntity::class,
        DeductionConfigEntity::class, DeductionRecordEntity::class,
        RewardEntity::class, RewardExchangeEntity::class, TimeRewardUsageEntity::class,
        MilestoneEntity::class, ScoringRuleEntity::class,
        KeyDateEntity::class
    ],
    version = 1
)
abstract class MushroomDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun taskTemplateDao(): TaskTemplateDao
    abstract fun checkInDao(): CheckInDao
    abstract fun mushroomLedgerDao(): MushroomLedgerDao
    abstract fun mushroomConfigDao(): MushroomConfigDao
    abstract fun deductionConfigDao(): DeductionConfigDao
    abstract fun deductionRecordDao(): DeductionRecordDao
    abstract fun rewardDao(): RewardDao
    abstract fun rewardExchangeDao(): RewardExchangeDao
    abstract fun timeRewardUsageDao(): TimeRewardUsageDao
    abstract fun milestoneDao(): MilestoneDao
    abstract fun scoringRuleDao(): ScoringRuleDao
    abstract fun keyDateDao(): KeyDateDao
}
```

---

## 三、完整数据库建表 SQL

共 13 张表，均在 Room 的 `@Database` 注解驱动下自动创建。以下为各表的完整 DDL，供参考和 migration 使用。

### 3.1 tasks

```sql
CREATE TABLE IF NOT EXISTS tasks (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    title             TEXT    NOT NULL,
    subject           TEXT    NOT NULL,
    estimated_minutes INTEGER NOT NULL,
    repeat_rule_type  TEXT    NOT NULL,           -- NONE / DAILY / WEEKDAYS / CUSTOM
    repeat_rule_days  TEXT,                        -- JSON array，仅 CUSTOM 时有值
    date              TEXT    NOT NULL,            -- ISO-8601 LocalDate
    deadline_at       TEXT,                        -- ISO-8601 LocalDateTime，可为 NULL
    template_type     TEXT,                        -- TaskTemplateType，可为 NULL
    status            TEXT    NOT NULL DEFAULT 'PENDING'
);
```

### 3.2 task_templates

```sql
CREATE TABLE IF NOT EXISTS task_templates (
    id                       INTEGER PRIMARY KEY AUTOINCREMENT,
    name                     TEXT    NOT NULL,
    type                     TEXT    NOT NULL,
    subject                  TEXT    NOT NULL,
    estimated_minutes        INTEGER NOT NULL,
    description              TEXT    NOT NULL DEFAULT '',
    default_deadline_offset  INTEGER,              -- 距当天0点的分钟偏移，可为 NULL
    base_reward_level        TEXT    NOT NULL,
    base_reward_amount       INTEGER NOT NULL,
    bonus_reward_level       TEXT,
    bonus_reward_amount      INTEGER,
    bonus_condition_type     TEXT,                 -- WithinMinutesAfterStart / ConsecutiveDays / AllItemsDone
    bonus_condition_value    INTEGER,              -- 对应条件的数值参数，AllItemsDone 时为 NULL
    is_built_in              INTEGER NOT NULL DEFAULT 0
);
```

### 3.3 check_ins

```sql
CREATE TABLE IF NOT EXISTS check_ins (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id       INTEGER NOT NULL,
    date          TEXT    NOT NULL,               -- ISO-8601 LocalDate
    checked_at    TEXT    NOT NULL,               -- ISO-8601 LocalDateTime
    is_early      INTEGER NOT NULL DEFAULT 0,
    early_minutes INTEGER NOT NULL DEFAULT 0,
    note          TEXT,
    image_uris    TEXT    NOT NULL DEFAULT '[]',  -- JSON array
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
);
```

### 3.4 mushroom_ledger

```sql
CREATE TABLE IF NOT EXISTS mushroom_ledger (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    level       TEXT    NOT NULL,                 -- MushroomLevel
    action      TEXT    NOT NULL,                 -- EARN / DEDUCT / SPEND
    amount      INTEGER NOT NULL,
    source_type TEXT    NOT NULL,                 -- MushroomSource
    source_id   INTEGER,
    note        TEXT,
    created_at  TEXT    NOT NULL                  -- ISO-8601 LocalDateTime
);
```

### 3.5 mushroom_config

```sql
CREATE TABLE IF NOT EXISTS mushroom_config (
    level          TEXT    PRIMARY KEY,            -- MushroomLevel
    display_name   TEXT    NOT NULL,
    exchange_points INTEGER NOT NULL               -- 该等级蘑菇对应的点数价值
);
```

### 3.6 deduction_config

```sql
CREATE TABLE IF NOT EXISTS deduction_config (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    name           TEXT    NOT NULL,
    mushroom_level TEXT    NOT NULL,
    default_amount INTEGER NOT NULL,
    custom_amount  INTEGER NOT NULL,
    is_enabled     INTEGER NOT NULL DEFAULT 0,    -- 默认关闭
    is_built_in    INTEGER NOT NULL DEFAULT 0,
    max_per_day    INTEGER NOT NULL DEFAULT 1
);
```

### 3.7 deduction_records

```sql
CREATE TABLE IF NOT EXISTS deduction_records (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    config_id      INTEGER NOT NULL,
    mushroom_level TEXT    NOT NULL,
    amount         INTEGER NOT NULL,
    reason         TEXT    NOT NULL,
    recorded_at    TEXT    NOT NULL,              -- ISO-8601 LocalDateTime
    appeal_status  TEXT    NOT NULL DEFAULT 'NONE',
    appeal_note    TEXT,
    FOREIGN KEY (config_id) REFERENCES deduction_config(id)
);
```

### 3.8 rewards

```sql
CREATE TABLE IF NOT EXISTS rewards (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    name              TEXT    NOT NULL,
    image_uri         TEXT    NOT NULL DEFAULT '',
    type              TEXT    NOT NULL,           -- PHYSICAL / TIME_BASED
    required_mushrooms TEXT   NOT NULL,           -- JSON map: {"SMALL":3,"MEDIUM":1}
    puzzle_pieces     INTEGER NOT NULL DEFAULT 1,
    time_limit_config TEXT,                       -- JSON 序列化的 TimeLimitConfig，可为 NULL
    status            TEXT    NOT NULL DEFAULT 'ACTIVE'
);
```

### 3.9 reward_exchanges

```sql
CREATE TABLE IF NOT EXISTS reward_exchanges (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    reward_id             INTEGER NOT NULL,
    mushroom_level        TEXT    NOT NULL,
    mushroom_count        INTEGER NOT NULL,
    puzzle_pieces_unlocked INTEGER NOT NULL DEFAULT 0,
    minutes_gained        INTEGER,               -- 仅 TIME_BASED 类型有值
    created_at            TEXT    NOT NULL,      -- ISO-8601 LocalDateTime
    FOREIGN KEY (reward_id) REFERENCES rewards(id)
);
```

### 3.10 time_reward_usage

```sql
CREATE TABLE IF NOT EXISTS time_reward_usage (
    reward_id    INTEGER NOT NULL,
    period_start TEXT    NOT NULL,               -- ISO-8601 LocalDate，周期起始日
    max_minutes  INTEGER NOT NULL,
    used_minutes INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (reward_id, period_start),
    FOREIGN KEY (reward_id) REFERENCES rewards(id)
);
```

### 3.11 milestones

```sql
CREATE TABLE IF NOT EXISTS milestones (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    name           TEXT    NOT NULL,
    type           TEXT    NOT NULL,             -- MilestoneType
    subject        TEXT    NOT NULL,
    scheduled_date TEXT    NOT NULL,             -- ISO-8601 LocalDate
    actual_score   INTEGER,
    status         TEXT    NOT NULL DEFAULT 'PENDING'
);
```

### 3.12 scoring_rules

```sql
CREATE TABLE IF NOT EXISTS scoring_rules (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    milestone_id  INTEGER NOT NULL,
    min_score     INTEGER NOT NULL,
    max_score     INTEGER NOT NULL,
    reward_level  TEXT    NOT NULL,
    reward_amount INTEGER NOT NULL,
    FOREIGN KEY (milestone_id) REFERENCES milestones(id) ON DELETE CASCADE
);
```

### 3.13 key_dates

```sql
CREATE TABLE IF NOT EXISTS key_dates (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    name              TEXT    NOT NULL,
    date              TEXT    NOT NULL,           -- ISO-8601 LocalDate
    condition_type    TEXT    NOT NULL,           -- ConsecutiveCheckinDays / MilestoneScore / ManualTrigger
    condition_value   TEXT,                       -- JSON，条件参数，ManualTrigger 时为 NULL
    reward_level      TEXT    NOT NULL,
    reward_amount     INTEGER NOT NULL
);
```

---

## 四、DB 实体与 Domain 实体的 Mapper 设计

### 4.1 设计原则

DB Entity（带 `@Entity` 注解的数据类）与 Domain Entity（纯 Kotlin data class）严格分离：

- DB Entity 位于 `data/db/entity/` 包，仅关心持久化存储格式（SQLite 兼容类型、列名）
- Domain Entity 位于 `core-domain` 模块，仅关心业务语义
- 两者之间通过 Mapper object 进行显式转换，禁止在 DAO 中直接返回 Domain Entity

### 4.2 Mapper 示例

```kotlin
// 位于 core-data/src/main/java/com/mushroom/data/mapper/TaskMapper.kt
object TaskMapper {
    fun toDomain(entity: TaskEntity): Task = Task(
        id = entity.id,
        title = entity.title,
        subject = Subject.valueOf(entity.subject),
        estimatedMinutes = entity.estimatedMinutes,
        repeatRule = parseRepeatRule(entity.repeatRuleType, entity.repeatRuleDays),
        date = LocalDate.parse(entity.date),
        deadline = entity.deadlineAt?.let { LocalDateTime.parse(it) },
        templateType = entity.templateType?.let { TaskTemplateType.valueOf(it) },
        status = TaskStatus.valueOf(entity.status)
    )

    fun toEntity(domain: Task): TaskEntity = TaskEntity(
        id = domain.id,
        title = domain.title,
        subject = domain.subject.name,
        estimatedMinutes = domain.estimatedMinutes,
        repeatRuleType = domain.repeatRule.typeName(),
        repeatRuleDays = domain.repeatRule.daysJson(),
        date = domain.date.toString(),
        deadlineAt = domain.deadline?.toString(),
        templateType = domain.templateType?.name,
        status = domain.status.name
    )

    private fun parseRepeatRule(type: String, daysJson: String?): RepeatRule = when (type) {
        "NONE"     -> RepeatRule.None
        "DAILY"    -> RepeatRule.Daily
        "WEEKDAYS" -> RepeatRule.Weekdays
        "CUSTOM"   -> RepeatRule.Custom(parseDaysOfWeek(daysJson))
        else       -> RepeatRule.None
    }
}
```

每个模块均提供对应的 Mapper object，命名规则为 `{EntityName}Mapper`：

| Mapper | 转换对象 |
|---|---|
| `TaskMapper` | `TaskEntity` ↔ `Task` |
| `TaskTemplateMapper` | `TaskTemplateEntity` ↔ `TaskTemplate` |
| `CheckInMapper` | `CheckInEntity` ↔ `CheckIn` |
| `MushroomLedgerMapper` | `MushroomLedgerEntity` ↔ `MushroomTransaction` |
| `DeductionConfigMapper` | `DeductionConfigEntity` ↔ `DeductionConfig` |
| `DeductionRecordMapper` | `DeductionRecordEntity` ↔ `DeductionRecord` |
| `RewardMapper` | `RewardEntity` ↔ `Reward` |
| `RewardExchangeMapper` | `RewardExchangeEntity` ↔ `RewardExchange` |
| `MilestoneMapper` | `MilestoneEntity` + `List<ScoringRuleEntity>` ↔ `Milestone` |
| `KeyDateMapper` | `KeyDateEntity` ↔ `KeyDate` |

---

## 五、DAO 接口设计

### 5.1 TaskDao

```kotlin
@Dao
interface TaskDao {
    /** 查询指定日期的所有任务，实时更新 */
    @Query("SELECT * FROM tasks WHERE date = :date ORDER BY status, subject")
    fun getByDate(date: String): Flow<List<TaskEntity>>

    /** 查询指定日期范围内的所有任务，实时更新 */
    @Query("SELECT * FROM tasks WHERE date BETWEEN :from AND :to ORDER BY date, subject")
    fun getByDateRange(from: String, to: String): Flow<List<TaskEntity>>

    /** 根据 ID 查询单个任务 */
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    /** 插入任务，返回新生成的 rowId */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(task: TaskEntity): Long

    /** 更新任务 */
    @Update
    suspend fun update(task: TaskEntity)

    /** 根据 ID 删除任务 */
    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)
}
```

### 5.2 CheckInDao

```kotlin
@Dao
interface CheckInDao {
    /** 查询指定日期范围内的打卡记录 */
    @Query("SELECT * FROM check_ins WHERE date BETWEEN :from AND :to ORDER BY checked_at DESC")
    fun getByDateRange(from: String, to: String): Flow<List<CheckInEntity>>

    /** 查询指定任务的最近一次打卡 */
    @Query("SELECT * FROM check_ins WHERE task_id = :taskId ORDER BY checked_at DESC LIMIT 1")
    suspend fun getLatestForTask(taskId: Long): CheckInEntity?

    /** 插入打卡记录 */
    @Insert
    suspend fun insert(checkIn: CheckInEntity): Long

    /**
     * 计算连续打卡天数：查询从 until 日期向前连续有打卡记录的天数。
     * 实现为子查询 + 窗口函数或应用层递归，具体见 CheckInRepositoryImpl。
     */
    @Query("SELECT DISTINCT date FROM check_ins WHERE date <= :until ORDER BY date DESC")
    suspend fun getDistinctDatesUntil(until: String): List<String>
}
```

### 5.3 MushroomLedgerDao

```kotlin
@Dao
interface MushroomLedgerDao {
    /**
     * 按等级聚合余额：EARN 累加，DEDUCT 和 SPEND 累减。
     * 结果为 Map<level, netAmount>，由 Repository 层封装为 MushroomBalance。
     */
    @Query("""
        SELECT level,
               SUM(CASE WHEN action = 'EARN' THEN amount ELSE -amount END) AS net
        FROM mushroom_ledger
        GROUP BY level
    """)
    fun getBalanceByLevel(): Flow<List<LevelBalanceRow>>

    /** 获取账本流水，按时间倒序 */
    @Query("SELECT * FROM mushroom_ledger ORDER BY created_at DESC LIMIT :limit")
    fun getLedger(limit: Int): Flow<List<MushroomLedgerEntity>>

    /** 插入单笔流水 */
    @Insert
    suspend fun insert(entity: MushroomLedgerEntity): Long

    /** 批量插入流水（原子事务） */
    @Insert
    suspend fun insertAll(entities: List<MushroomLedgerEntity>)
}

data class LevelBalanceRow(
    @ColumnInfo(name = "level") val level: String,
    @ColumnInfo(name = "net") val net: Int
)
```

### 5.4 DeductionConfigDao

```kotlin
@Dao
interface DeductionConfigDao {
    /** 获取所有扣分配置 */
    @Query("SELECT * FROM deduction_config ORDER BY is_built_in DESC, id ASC")
    fun getAll(): Flow<List<DeductionConfigEntity>>

    /** 获取所有启用的扣分配置 */
    @Query("SELECT * FROM deduction_config WHERE is_enabled = 1 ORDER BY is_built_in DESC, id ASC")
    fun getEnabled(): Flow<List<DeductionConfigEntity>>

    /** 更新配置 */
    @Update
    suspend fun updateConfig(config: DeductionConfigEntity)

    /** 初始化时批量插入预置配置 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(configs: List<DeductionConfigEntity>)
}
```

### 5.5 DeductionRecordDao

```kotlin
@Dao
interface DeductionRecordDao {
    /** 获取所有扣分记录，按时间倒序 */
    @Query("SELECT * FROM deduction_records ORDER BY recorded_at DESC")
    fun getAll(): Flow<List<DeductionRecordEntity>>

    /** 插入扣分记录 */
    @Insert
    suspend fun insert(record: DeductionRecordEntity): Long

    /** 更新申诉状态 */
    @Query("UPDATE deduction_records SET appeal_status = :status, appeal_note = :note WHERE id = :id")
    suspend fun updateAppealStatus(id: Long, status: String, note: String?)

    /**
     * 查询今日指定配置已扣次数（用于 maxPerDay 校验）。
     * :today 格式为 ISO-8601 LocalDate（如 "2026-03-01"）。
     */
    @Query("""
        SELECT COUNT(*) FROM deduction_records
        WHERE config_id = :configId
          AND recorded_at >= :today || 'T00:00:00'
          AND recorded_at <  :today || 'T23:59:59'
    """)
    suspend fun getTodayCountByConfigId(configId: Long, today: String): Int
}
```

### 5.6 RewardDao

```kotlin
@Dao
interface RewardDao {
    /** 获取所有激活状态的奖励 */
    @Query("SELECT * FROM rewards WHERE status = 'ACTIVE'")
    fun getActive(): Flow<List<RewardEntity>>

    /** 根据 ID 查询奖励 */
    @Query("SELECT * FROM rewards WHERE id = :id")
    suspend fun getById(id: Long): RewardEntity?

    /** 插入奖励 */
    @Insert
    suspend fun insert(reward: RewardEntity): Long

    /** 更新奖励 */
    @Update
    suspend fun update(reward: RewardEntity)
}
```

### 5.7 RewardExchangeDao

```kotlin
@Dao
interface RewardExchangeDao {
    /** 插入兑换记录 */
    @Insert
    suspend fun insert(exchange: RewardExchangeEntity): Long

    /**
     * 查询指定奖励已解锁拼图总数（puzzle_pieces_unlocked 求和）。
     * 用于实时计算 PuzzleProgress。
     */
    @Query("SELECT SUM(puzzle_pieces_unlocked) FROM reward_exchanges WHERE reward_id = :rewardId")
    fun getSumPiecesByRewardId(rewardId: Long): Flow<Int?>
}
```

### 5.8 MilestoneDao

```kotlin
@Dao
interface MilestoneDao {
    /** 获取所有里程碑，按 scheduledDate 倒序 */
    @Query("SELECT * FROM milestones ORDER BY scheduled_date DESC")
    fun getAll(): Flow<List<MilestoneEntity>>

    /** 按科目筛选里程碑 */
    @Query("SELECT * FROM milestones WHERE subject = :subject ORDER BY scheduled_date DESC")
    fun getBySubject(subject: String): Flow<List<MilestoneEntity>>

    /** 插入里程碑 */
    @Insert
    suspend fun insert(milestone: MilestoneEntity): Long

    /** 更新得分及状态 */
    @Query("UPDATE milestones SET actual_score = :score, status = :status WHERE id = :id")
    suspend fun updateScore(id: Long, score: Int, status: String)
}
```

---

## 六、预置数据

在 `RoomDatabase.Callback.onCreate` 时插入以下预置数据。

### 6.1 三个预设任务模板

| 字段 | 晨读 | 备忘录 | 在校完成作业 |
|---|---|---|---|
| name | 晨读 | 作业备忘录 | 在校完成作业 |
| type | MORNING_READING | HOMEWORK_MEMO | HOMEWORK_AT_SCHOOL |
| subject | OTHER | OTHER | OTHER |
| estimatedMinutes | 20 | 5 | 60 |
| defaultDeadlineOffset | 480（08:00） | 1320（22:00） | 1020（17:00） |
| baseReward | SMALL × 1 | SMALL × 1 | MEDIUM × 1 |
| bonusConditionType | WithinMinutesAfterStart(30) | — | AllItemsDone |
| bonusReward | SMALL × 1 | — | SMALL × 2 |
| isBuiltIn | true | true | true |

### 6.2 五个预设扣分配置项

所有预置扣分配置默认 `is_enabled = false`（关闭），由家长手动开启。

| name | mushroomLevel | defaultAmount | maxPerDay |
|---|---|---|---|
| 忘记带作业 | SMALL | 2 | 1 |
| 没有订正试卷 | SMALL | 3 | 1 |
| 作业未记录 | SMALL | 1 | 3 |
| 作业未完成就玩耍 | MEDIUM | 1 | 1 |
| 上课迟到 | SMALL | 2 | 1 |

### 6.3 五个默认蘑菇等级配置

```kotlin
listOf(
    MushroomConfigEntity(level = "SMALL",  displayName = "小蘑菇",   exchangePoints = 1),
    MushroomConfigEntity(level = "MEDIUM", displayName = "中蘑菇",   exchangePoints = 5),
    MushroomConfigEntity(level = "LARGE",  displayName = "大蘑菇",   exchangePoints = 20),
    MushroomConfigEntity(level = "GOLD",   displayName = "金蘑菇",   exchangePoints = 100),
    MushroomConfigEntity(level = "LEGEND", displayName = "传说蘑菇", exchangePoints = 500),
)
```

---

## 七、包结构

```
core-data/src/main/java/com/mushroom/data/
├── db/
│   ├── MushroomDatabase.kt
│   ├── entity/                      # 所有 @Entity 数据类
│   │   ├── TaskEntity.kt
│   │   ├── TaskTemplateEntity.kt
│   │   ├── CheckInEntity.kt
│   │   ├── MushroomLedgerEntity.kt
│   │   ├── MushroomConfigEntity.kt
│   │   ├── DeductionConfigEntity.kt
│   │   ├── DeductionRecordEntity.kt
│   │   ├── RewardEntity.kt
│   │   ├── RewardExchangeEntity.kt
│   │   ├── TimeRewardUsageEntity.kt
│   │   ├── MilestoneEntity.kt
│   │   ├── ScoringRuleEntity.kt
│   │   └── KeyDateEntity.kt
│   └── dao/                         # 所有 DAO 接口
│       ├── TaskDao.kt
│       ├── TaskTemplateDao.kt
│       ├── CheckInDao.kt
│       ├── MushroomLedgerDao.kt
│       ├── MushroomConfigDao.kt
│       ├── DeductionConfigDao.kt
│       ├── DeductionRecordDao.kt
│       ├── RewardDao.kt
│       ├── RewardExchangeDao.kt
│       ├── TimeRewardUsageDao.kt
│       ├── MilestoneDao.kt
│       ├── ScoringRuleDao.kt
│       └── KeyDateDao.kt
├── mapper/                          # Domain ↔ DB Entity 转换
│   ├── TaskMapper.kt
│   ├── TaskTemplateMapper.kt
│   ├── CheckInMapper.kt
│   ├── MushroomLedgerMapper.kt
│   ├── DeductionConfigMapper.kt
│   ├── DeductionRecordMapper.kt
│   ├── RewardMapper.kt
│   ├── RewardExchangeMapper.kt
│   ├── MilestoneMapper.kt
│   └── KeyDateMapper.kt
├── repository/                      # Repository 接口实现
│   ├── TaskRepositoryImpl.kt
│   ├── TaskTemplateRepositoryImpl.kt
│   ├── CheckInRepositoryImpl.kt
│   ├── MushroomRepositoryImpl.kt
│   ├── DeductionRepositoryImpl.kt
│   ├── RewardRepositoryImpl.kt
│   ├── MilestoneRepositoryImpl.kt
│   └── KeyDateRepositoryImpl.kt
└── di/                              # Hilt Module（绑定 Repository 实现）
    └── DataModule.kt
```
