package com.mushroom.core.data.mapper

import com.mushroom.core.data.db.entity.*
import com.mushroom.core.domain.entity.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

private val json = Json { ignoreUnknownKeys = true }

object TaskMapper {
    fun toDomain(e: TaskEntity): Task = Task(
        id = e.id,
        title = e.title,
        subject = Subject.valueOf(e.subject),
        estimatedMinutes = e.estimatedMinutes,
        repeatRule = decodeRepeatRule(e.repeatRuleType, e.repeatRuleDays),
        date = LocalDate.parse(e.date),
        deadline = e.deadlineAt?.let { LocalDateTime.parse(it) },
        templateType = e.templateType?.let { TaskTemplateType.valueOf(it) },
        status = TaskStatus.valueOf(e.status)
    )

    fun toDb(d: Task): TaskEntity {
        val (ruleType, ruleDays) = encodeRepeatRule(d.repeatRule)
        return TaskEntity(
            id = d.id,
            title = d.title,
            subject = d.subject.name,
            estimatedMinutes = d.estimatedMinutes,
            repeatRuleType = ruleType,
            repeatRuleDays = ruleDays,
            date = d.date.toString(),
            deadlineAt = d.deadline?.toString(),
            templateType = d.templateType?.name,
            status = d.status.name
        )
    }

    private fun encodeRepeatRule(rule: RepeatRule): Pair<String, String?> = when (rule) {
        is RepeatRule.None -> "NONE" to null
        is RepeatRule.Daily -> "DAILY" to null
        is RepeatRule.Weekdays -> "WEEKDAYS" to null
        is RepeatRule.Custom -> "CUSTOM" to
            Json.encodeToString(rule.daysOfWeek.map { it.value })
    }

    private fun decodeRepeatRule(type: String, days: String?): RepeatRule = when (type) {
        "NONE" -> RepeatRule.None
        "DAILY" -> RepeatRule.Daily
        "WEEKDAYS" -> RepeatRule.Weekdays
        "CUSTOM" -> {
            val dayValues = days?.let {
                Json.decodeFromString<List<Int>>(it).map { v -> DayOfWeek.of(v) }.toSet()
            } ?: emptySet()
            RepeatRule.Custom(dayValues)
        }
        else -> RepeatRule.None
    }
}

object CheckInMapper {
    fun toDomain(e: CheckInEntity): CheckIn = CheckIn(
        id = e.id,
        taskId = e.taskId,
        date = LocalDate.parse(e.date),
        checkedAt = LocalDateTime.parse(e.checkedAt),
        isEarly = e.isEarly,
        earlyMinutes = e.earlyMinutes,
        note = e.note,
        imageUris = Json.decodeFromString(e.imageUris)
    )

    fun toDb(d: CheckIn): CheckInEntity = CheckInEntity(
        id = d.id,
        taskId = d.taskId,
        date = d.date.toString(),
        checkedAt = d.checkedAt.toString(),
        isEarly = d.isEarly,
        earlyMinutes = d.earlyMinutes,
        note = d.note,
        imageUris = Json.encodeToString(d.imageUris)
    )
}

object MushroomLedgerMapper {
    fun toDomain(e: MushroomLedgerEntity): MushroomTransaction = MushroomTransaction(
        id = e.id,
        level = MushroomLevel.valueOf(e.level),
        action = MushroomAction.valueOf(e.action),
        amount = e.amount,
        sourceType = MushroomSource.valueOf(e.sourceType),
        sourceId = e.sourceId,
        note = e.note,
        createdAt = LocalDateTime.parse(e.createdAt)
    )

    fun toDb(d: MushroomTransaction): MushroomLedgerEntity = MushroomLedgerEntity(
        id = d.id,
        level = d.level.name,
        action = d.action.name,
        amount = d.amount,
        sourceType = d.sourceType.name,
        sourceId = d.sourceId,
        note = d.note,
        createdAt = d.createdAt.toString()
    )
}

object DeductionMapper {
    fun toConfigDomain(e: DeductionConfigEntity): DeductionConfig = DeductionConfig(
        id = e.id,
        name = e.name,
        mushroomLevel = MushroomLevel.valueOf(e.mushroomLevel),
        defaultAmount = e.defaultAmount,
        customAmount = e.customAmount,
        isEnabled = e.isEnabled,
        isBuiltIn = e.isBuiltIn,
        maxPerDay = e.maxPerDay
    )

    fun toConfigDb(d: DeductionConfig): DeductionConfigEntity = DeductionConfigEntity(
        id = d.id,
        name = d.name,
        mushroomLevel = d.mushroomLevel.name,
        defaultAmount = d.defaultAmount,
        customAmount = d.customAmount,
        isEnabled = d.isEnabled,
        isBuiltIn = d.isBuiltIn,
        maxPerDay = d.maxPerDay
    )

    fun toRecordDomain(e: DeductionRecordEntity): DeductionRecord = DeductionRecord(
        id = e.id,
        configId = e.configId,
        mushroomLevel = MushroomLevel.valueOf(e.mushroomLevel),
        amount = e.amount,
        reason = e.reason,
        recordedAt = LocalDateTime.parse(e.recordedAt),
        appealStatus = AppealStatus.valueOf(e.appealStatus),
        appealNote = e.appealNote
    )

    fun toRecordDb(d: DeductionRecord): DeductionRecordEntity = DeductionRecordEntity(
        id = d.id,
        configId = d.configId,
        mushroomLevel = d.mushroomLevel.name,
        amount = d.amount,
        reason = d.reason,
        recordedAt = d.recordedAt.toString(),
        appealStatus = d.appealStatus.name,
        appealNote = d.appealNote
    )
}

object RewardMapper {
    fun toDomain(e: RewardEntity): Reward = Reward(
        id = e.id,
        name = e.name,
        imageUri = e.imageUri,
        type = RewardType.valueOf(e.type),
        requiredMushrooms = decodeRequiredMushrooms(e.requiredMushrooms),
        puzzlePieces = e.puzzlePieces,
        timeLimitConfig = e.timeLimitConfig?.let { decodeTimeLimitConfig(it) },
        status = RewardStatus.valueOf(e.status)
    )

    fun toDb(d: Reward): RewardEntity = RewardEntity(
        id = d.id,
        name = d.name,
        imageUri = d.imageUri,
        type = d.type.name,
        requiredMushrooms = encodeRequiredMushrooms(d.requiredMushrooms),
        puzzlePieces = d.puzzlePieces,
        timeLimitConfig = d.timeLimitConfig?.let { encodeTimeLimitConfig(it) },
        status = d.status.name
    )

    private fun encodeRequiredMushrooms(map: Map<MushroomLevel, Int>): String =
        Json.encodeToString(map.map { (k, v) -> k.name to v }.toMap())

    private fun decodeRequiredMushrooms(s: String): Map<MushroomLevel, Int> =
        Json.decodeFromString<Map<String, Int>>(s)
            .mapKeys { MushroomLevel.valueOf(it.key) }

    private fun encodeTimeLimitConfig(c: TimeLimitConfig): String =
        buildJsonObject {
            put("unitMinutes", c.unitMinutes)
            put("periodType", c.periodType.name)
            put("maxMinutesPerPeriod", c.maxMinutesPerPeriod)
            put("cooldownDays", c.cooldownDays)
            put("requireParentConfirm", c.requireParentConfirm)
        }.toString()

    private fun decodeTimeLimitConfig(s: String): TimeLimitConfig {
        val obj = Json.parseToJsonElement(s).jsonObject
        return TimeLimitConfig(
            unitMinutes = obj["unitMinutes"]!!.jsonPrimitive.int,
            periodType = PeriodType.valueOf(obj["periodType"]!!.jsonPrimitive.content),
            maxMinutesPerPeriod = obj["maxMinutesPerPeriod"]!!.jsonPrimitive.int,
            cooldownDays = obj["cooldownDays"]!!.jsonPrimitive.int,
            requireParentConfirm = obj["requireParentConfirm"]!!.jsonPrimitive.boolean
        )
    }
}

object MilestoneMapper {
    fun toDomain(e: MilestoneEntity, rules: List<ScoringRuleEntity>): Milestone = Milestone(
        id = e.id,
        name = e.name,
        type = MilestoneType.valueOf(e.type),
        subject = Subject.valueOf(e.subject),
        scheduledDate = LocalDate.parse(e.scheduledDate),
        scoringRules = rules.map { r ->
            ScoringRule(
                minScore = r.minScore,
                maxScore = r.maxScore,
                rewardConfig = MushroomRewardConfig(MushroomLevel.valueOf(r.rewardLevel), r.rewardAmount)
            )
        },
        actualScore = e.actualScore,
        status = MilestoneStatus.valueOf(e.status)
    )

    fun toDb(d: Milestone): MilestoneEntity = MilestoneEntity(
        id = d.id,
        name = d.name,
        type = d.type.name,
        subject = d.subject.name,
        scheduledDate = d.scheduledDate.toString(),
        actualScore = d.actualScore,
        status = d.status.name
    )

    fun rulesToDb(milestoneId: Long, rules: List<ScoringRule>): List<ScoringRuleEntity> =
        rules.map { r ->
            ScoringRuleEntity(
                milestoneId = milestoneId,
                minScore = r.minScore,
                maxScore = r.maxScore,
                rewardLevel = r.rewardConfig.level.name,
                rewardAmount = r.rewardConfig.amount
            )
        }
}

object KeyDateMapper {
    fun toDomain(e: KeyDateEntity): KeyDate = KeyDate(
        id = e.id,
        name = e.name,
        date = LocalDate.parse(e.date),
        condition = decodeCondition(e.conditionType, e.conditionValue),
        rewardConfig = MushroomRewardConfig(MushroomLevel.valueOf(e.rewardLevel), e.rewardAmount)
    )

    fun toDb(d: KeyDate): KeyDateEntity = KeyDateEntity(
        id = d.id,
        name = d.name,
        date = d.date.toString(),
        conditionType = conditionType(d.condition),
        conditionValue = conditionValue(d.condition),
        rewardLevel = d.rewardConfig.level.name,
        rewardAmount = d.rewardConfig.amount
    )

    private fun conditionType(c: KeyDateCondition): String = when (c) {
        is KeyDateCondition.ConsecutiveCheckinDays -> "ConsecutiveCheckinDays"
        is KeyDateCondition.MilestoneScore -> "MilestoneScore"
        is KeyDateCondition.ManualTrigger -> "ManualTrigger"
    }

    private fun conditionValue(c: KeyDateCondition): String? = when (c) {
        is KeyDateCondition.ConsecutiveCheckinDays -> c.days.toString()
        is KeyDateCondition.MilestoneScore ->
            Json.encodeToString(buildJsonObject {
                put("milestoneId", c.milestoneId)
                put("minScore", c.minScore)
            })
        is KeyDateCondition.ManualTrigger -> null
    }

    private fun decodeCondition(type: String, value: String?): KeyDateCondition = when (type) {
        "ConsecutiveCheckinDays" -> KeyDateCondition.ConsecutiveCheckinDays(value!!.toInt())
        "MilestoneScore" -> {
            val obj = Json.parseToJsonElement(value!!).jsonObject
            KeyDateCondition.MilestoneScore(
                milestoneId = obj["milestoneId"]!!.jsonPrimitive.long,
                minScore = obj["minScore"]!!.jsonPrimitive.int
            )
        }
        else -> KeyDateCondition.ManualTrigger
    }
}
