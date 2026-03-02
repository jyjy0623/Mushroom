package com.mushroom.core.data.seed

import com.mushroom.core.data.db.dao.DeductionConfigDao
import com.mushroom.core.data.db.entity.DeductionConfigEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 内置扣分规则 Seed 数据。
 * 应用首次启动时调用 seed()，OnConflictStrategy.IGNORE 保证幂等。
 * 默认全部关闭（is_enabled = false），由家长在设置页面按需启用。
 */
@Singleton
class DeductionConfigSeed @Inject constructor(
    private val dao: DeductionConfigDao
) {
    suspend fun seed() {
        val builtIn = listOf(
            DeductionConfigEntity(
                id = 1L,
                name = "未完成作业",
                mushroomLevel = "SMALL",
                defaultAmount = 2,
                customAmount = 0,
                isEnabled = false,
                isBuiltIn = true,
                maxPerDay = 1
            ),
            DeductionConfigEntity(
                id = 2L,
                name = "未完成打卡任务",
                mushroomLevel = "SMALL",
                defaultAmount = 1,
                customAmount = 0,
                isEnabled = false,
                isBuiltIn = true,
                maxPerDay = 3
            ),
            DeductionConfigEntity(
                id = 3L,
                name = "上课不认真/开小差",
                mushroomLevel = "SMALL",
                defaultAmount = 1,
                customAmount = 0,
                isEnabled = false,
                isBuiltIn = true,
                maxPerDay = 3
            ),
            DeductionConfigEntity(
                id = 4L,
                name = "超时使用电子设备",
                mushroomLevel = "SMALL",
                defaultAmount = 2,
                customAmount = 0,
                isEnabled = false,
                isBuiltIn = true,
                maxPerDay = 2
            ),
            DeductionConfigEntity(
                id = 5L,
                name = "考试成绩退步",
                mushroomLevel = "MEDIUM",
                defaultAmount = 1,
                customAmount = 0,
                isEnabled = false,
                isBuiltIn = true,
                maxPerDay = 1
            ),
            DeductionConfigEntity(
                id = 6L,
                name = "说谎/不诚实",
                mushroomLevel = "MEDIUM",
                defaultAmount = 2,
                customAmount = 0,
                isEnabled = false,
                isBuiltIn = true,
                maxPerDay = 1
            ),
            DeductionConfigEntity(
                id = 7L,
                name = "顶嘴/态度差",
                mushroomLevel = "SMALL",
                defaultAmount = 1,
                customAmount = 0,
                isEnabled = false,
                isBuiltIn = true,
                maxPerDay = 2
            ),
            DeductionConfigEntity(
                id = 8L,
                name = "房间不整理",
                mushroomLevel = "SMALL",
                defaultAmount = 1,
                customAmount = 0,
                isEnabled = false,
                isBuiltIn = true,
                maxPerDay = 1
            )
        )
        builtIn.forEach { dao.insert(it) }
    }
}
