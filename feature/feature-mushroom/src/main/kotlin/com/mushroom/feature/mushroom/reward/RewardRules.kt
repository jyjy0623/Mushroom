package com.mushroom.feature.mushroom.reward

import com.mushroom.core.domain.entity.MushroomAction
import com.mushroom.core.domain.entity.MushroomLevel
import com.mushroom.core.domain.entity.MushroomRewardConfig
import com.mushroom.core.domain.entity.MushroomSource
import com.mushroom.core.domain.entity.MushroomTransaction
import com.mushroom.core.domain.entity.TaskTemplateType
import com.mushroom.core.domain.event.MushroomReward
import com.mushroom.core.domain.event.RewardEvent
import com.mushroom.core.domain.service.RewardRuleEngine
import java.time.LocalDateTime
import javax.inject.Inject

// ---------------------------------------------------------------------------
// RewardRule interface
// ---------------------------------------------------------------------------

interface RewardRule {
    fun applies(event: RewardEvent): Boolean
    fun calculate(event: RewardEvent): List<MushroomReward>
}

// ---------------------------------------------------------------------------
// Rule 1: 每日任务完成奖励
// ---------------------------------------------------------------------------

class DailyTaskCompleteRule : RewardRule {
    override fun applies(event: RewardEvent) = event is RewardEvent.TaskCompleted

    override fun calculate(event: RewardEvent): List<MushroomReward> {
        val e = event as RewardEvent.TaskCompleted
        val baseConfig = e.task.templateType
            ?.let { null }  // template tasks handled by dedicated rules
            ?: MushroomRewardConfig(MushroomLevel.SMALL, 1)
        return listOf(
            MushroomReward(
                level = MushroomLevel.SMALL,
                amount = 1,
                reason = "完成任务「${e.task.title}」",
                sourceType = MushroomSource.TASK
            )
        )
    }
}

// ---------------------------------------------------------------------------
// Rule 2: 提前完成奖励（tiered by earlyMinutes）
// ---------------------------------------------------------------------------

class EarlyCompletionRule : RewardRule {
    override fun applies(event: RewardEvent) =
        event is RewardEvent.TaskCompleted && event.checkIn.isEarly

    override fun calculate(event: RewardEvent): List<MushroomReward> {
        val e = event as RewardEvent.TaskCompleted
        val earlyMin = e.checkIn.earlyMinutes
        val (level, amount) = when {
            earlyMin > 180 -> MushroomLevel.MEDIUM to 1
            earlyMin >= 60 -> MushroomLevel.SMALL to 2
            else           -> MushroomLevel.SMALL to 1
        }
        return listOf(
            MushroomReward(
                level = level,
                amount = amount,
                reason = "提前 ${earlyMin} 分钟完成「${e.task.title}」",
                sourceType = MushroomSource.EARLY_BONUS
            )
        )
    }
}

// ---------------------------------------------------------------------------
// Rule 3: 晨读模板奖励
// ---------------------------------------------------------------------------

class MorningReadingRule : RewardRule {
    override fun applies(event: RewardEvent) =
        event is RewardEvent.TaskCompleted &&
        event.task.templateType == TaskTemplateType.MORNING_READING

    override fun calculate(event: RewardEvent): List<MushroomReward> {
        val e = event as RewardEvent.TaskCompleted
        return listOf(
            MushroomReward(
                level = MushroomLevel.SMALL,
                amount = 2,
                reason = "完成晨读任务「${e.task.title}」",
                sourceType = MushroomSource.TEMPLATE_BONUS
            )
        )
    }
}

// ---------------------------------------------------------------------------
// Rule 4: 作业备忘录模板奖励
// ---------------------------------------------------------------------------

class HomeworkMemoRule : RewardRule {
    override fun applies(event: RewardEvent) =
        event is RewardEvent.TaskCompleted &&
        event.task.templateType == TaskTemplateType.HOMEWORK_MEMO

    override fun calculate(event: RewardEvent): List<MushroomReward> {
        val e = event as RewardEvent.TaskCompleted
        return listOf(
            MushroomReward(
                level = MushroomLevel.SMALL,
                amount = 1,
                reason = "完成备忘录任务「${e.task.title}」",
                sourceType = MushroomSource.TEMPLATE_BONUS
            )
        )
    }
}

// ---------------------------------------------------------------------------
// Rule 5: 在校完成作业模板奖励
// ---------------------------------------------------------------------------

class HomeworkAtSchoolRule : RewardRule {
    override fun applies(event: RewardEvent) =
        event is RewardEvent.TaskCompleted &&
        event.task.templateType == TaskTemplateType.HOMEWORK_AT_SCHOOL

    override fun calculate(event: RewardEvent): List<MushroomReward> {
        val e = event as RewardEvent.TaskCompleted
        return listOf(
            MushroomReward(
                level = MushroomLevel.SMALL,
                amount = 2,
                reason = "在校完成作业「${e.task.title}」",
                sourceType = MushroomSource.TEMPLATE_BONUS
            )
        )
    }
}

// ---------------------------------------------------------------------------
// Rule 6: 当日全部任务完成奖励
// ---------------------------------------------------------------------------

class AllTasksDoneRule : RewardRule {
    override fun applies(event: RewardEvent) = event is RewardEvent.AllDailyTasksDone

    override fun calculate(event: RewardEvent): List<MushroomReward> {
        val e = event as RewardEvent.AllDailyTasksDone
        return listOf(
            MushroomReward(
                level = MushroomLevel.MEDIUM,
                amount = 1,
                reason = "${e.date} 全部任务完成奖励",
                sourceType = MushroomSource.TASK
            )
        )
    }
}

// ---------------------------------------------------------------------------
// Rule 7: 连续打卡里程碑奖励（7/30/100天）
// ---------------------------------------------------------------------------

class StreakRule : RewardRule {
    private val milestones = setOf(7, 30, 100)

    override fun applies(event: RewardEvent) =
        event is RewardEvent.StreakReached && event.days in milestones

    override fun calculate(event: RewardEvent): List<MushroomReward> {
        val e = event as RewardEvent.StreakReached
        val (level, amount) = when (e.days) {
            100  -> MushroomLevel.GOLD to 1
            30   -> MushroomLevel.LARGE to 1
            else -> MushroomLevel.MEDIUM to 1   // 7天
        }
        return listOf(
            MushroomReward(
                level = level,
                amount = amount,
                reason = "连续打卡 ${e.days} 天里程碑",
                sourceType = MushroomSource.CHECKIN_STREAK
            )
        )
    }
}

// ---------------------------------------------------------------------------
// Rule 8: 里程碑考试成绩奖励
// ---------------------------------------------------------------------------

class MilestoneScoreRule : RewardRule {
    override fun applies(event: RewardEvent) = event is RewardEvent.MilestoneAchieved

    override fun calculate(event: RewardEvent): List<MushroomReward> {
        val e = event as RewardEvent.MilestoneAchieved
        val score = e.milestone.actualScore ?: return emptyList()
        val rule = e.milestone.scoringRules.firstOrNull {
            score in it.minScore..it.maxScore
        } ?: return emptyList()
        return listOf(
            MushroomReward(
                level = rule.rewardConfig.level,
                amount = rule.rewardConfig.amount,
                reason = "里程碑「${e.milestone.name}」得分 $score",
                sourceType = MushroomSource.MILESTONE
            )
        )
    }
}

// ---------------------------------------------------------------------------
// Rule 9: 关键日期奖励
// ---------------------------------------------------------------------------

class KeyDateRule : RewardRule {
    override fun applies(event: RewardEvent) = event is RewardEvent.KeyDateAchieved

    override fun calculate(event: RewardEvent): List<MushroomReward> {
        val e = event as RewardEvent.KeyDateAchieved
        return listOf(
            MushroomReward(
                level = e.keyDate.rewardConfig.level,
                amount = e.keyDate.rewardConfig.amount,
                reason = "关键日期「${e.keyDate.name}」达成",
                sourceType = MushroomSource.KEY_DATE
            )
        )
    }
}

// ---------------------------------------------------------------------------
// RewardRuleChain — implements RewardRuleEngine
// ---------------------------------------------------------------------------

class RewardRuleChain @Inject constructor() : RewardRuleEngine {

    private val rules: List<RewardRule> = listOf(
        DailyTaskCompleteRule(),
        EarlyCompletionRule(),
        MorningReadingRule(),
        HomeworkMemoRule(),
        HomeworkAtSchoolRule(),
        AllTasksDoneRule(),
        StreakRule(),
        MilestoneScoreRule(),
        KeyDateRule()
    )

    override fun calculate(event: RewardEvent): List<MushroomReward> =
        rules.filter { it.applies(event) }.flatMap { it.calculate(event) }
}
