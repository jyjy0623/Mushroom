package com.mushroom.core.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mushroom.core.data.db.MushroomDatabase
import com.mushroom.core.data.db.entity.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import org.robolectric.junit5.RobolectricExtension

/**
 * Room In-Memory 数据库 TaskDao 集成测试。
 * 使用 Robolectric 在 JVM 上运行。
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class TaskDaoTest {

    private lateinit var db: MushroomDatabase

    @BeforeEach
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MushroomDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    @Test
    fun `when_database_created_should_not_throw`() {
        assertNotNull(db)
    }

    @Test
    fun `when_insert_task_should_return_generated_id`() = runTest {
        val id = db.taskDao().insert(buildTaskEntity())
        assertTrue(id > 0)
    }

    @Test
    fun `when_insert_and_query_by_date_should_return_task`() = runTest {
        val entity = buildTaskEntity(date = "2026-03-01")
        db.taskDao().insert(entity)

        val tasks = db.taskDao().getTasksByDate("2026-03-01").first()
        assertEquals(1, tasks.size)
        assertEquals("做数学作业", tasks[0].title)
    }

    @Test
    fun `when_delete_task_should_not_appear_in_query`() = runTest {
        val id = db.taskDao().insert(buildTaskEntity(date = "2026-03-01"))
        db.taskDao().deleteById(id)

        val tasks = db.taskDao().getTasksByDate("2026-03-01").first()
        assertTrue(tasks.isEmpty())
    }

    @Test
    fun `when_update_task_status_should_persist`() = runTest {
        val id = db.taskDao().insert(buildTaskEntity(date = "2026-03-01"))
        val original = db.taskDao().getTaskById(id)!!
        db.taskDao().update(original.copy(status = "EARLY_DONE"))

        val updated = db.taskDao().getTaskById(id)!!
        assertEquals("EARLY_DONE", updated.status)
    }

    @Test
    fun `when_query_date_range_should_return_tasks_within_range`() = runTest {
        db.taskDao().insert(buildTaskEntity(date = "2026-03-01"))
        db.taskDao().insert(buildTaskEntity(date = "2026-03-02", title = "任务2"))
        db.taskDao().insert(buildTaskEntity(date = "2026-03-05", title = "超范围任务"))

        val tasks = db.taskDao()
            .getTasksByDateRange("2026-03-01", "2026-03-03")
            .first()
        assertEquals(2, tasks.size)
    }

    private fun buildTaskEntity(
        date: String = "2026-03-01",
        title: String = "做数学作业"
    ) = TaskEntity(
        title = title,
        subject = "MATH",
        estimatedMinutes = 60,
        repeatRuleType = "NONE",
        repeatRuleDays = null,
        date = date,
        deadlineAt = null,
        templateType = null,
        status = "PENDING"
    )
}
