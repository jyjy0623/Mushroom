package com.mushroom.service.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mushroom.core.domain.entity.Task
import com.mushroom.core.domain.service.NotificationService
import com.mushroom.core.logging.MushroomLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NotificationService"
private const val CHANNEL_ID = "task_reminder"
private const val CHANNEL_NAME = "任务提醒"
private const val NOTIF_ID_BASE = 10000

/**
 * 通知服务实现。
 *
 * - scheduleDeadlineReminder：在任务截止前 30 分钟发送提醒（通过 AlarmManager）
 * - cancelDeadlineReminder：取消已排期的提醒
 * - sendImmediateNotification：立即发送一条通知
 *
 * 注意：scheduleDeadlineReminder / cancelDeadlineReminder 需要 AlarmManager，
 * 在 minSdk=26 上完全支持。精确闹钟在 Android 12+ 需要 SCHEDULE_EXACT_ALARM 权限，
 * 此处回退到 setWindow（±15min 窗口），无需额外权限。
 */
@Singleton
class NotificationServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : NotificationService {

    init {
        createNotificationChannel()
    }

    override suspend fun scheduleDeadlineReminder(task: Task) {
        val deadline = task.deadline ?: run {
            MushroomLogger.i(TAG, "scheduleDeadlineReminder: task ${task.id} has no deadline, skip")
            return
        }

        val reminderTime = deadline.minusMinutes(30)
        val nowTime = LocalDateTime.now()
        if (reminderTime.isBefore(nowTime)) {
            MushroomLogger.i(TAG, "scheduleDeadlineReminder: reminder time already past for task ${task.id}")
            return
        }

        val triggerMs = reminderTime
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        scheduleAlarm(task.id, task.title, triggerMs)
        MushroomLogger.i(TAG, "scheduled reminder for task '${task.title}' at $reminderTime")
    }

    override suspend fun cancelDeadlineReminder(taskId: Long) {
        cancelAlarm(taskId)
        MushroomLogger.i(TAG, "cancelled reminder for task $taskId")
    }

    override suspend fun sendImmediateNotification(title: String, body: String) {
        val notifId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        sendNotification(notifId, title, body)
        MushroomLogger.i(TAG, "sent immediate notification: $title")
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            val appName = context.applicationInfo.loadLabel(context.packageManager)
            description = "$appName 任务截止前提醒"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun scheduleAlarm(taskId: Long, taskTitle: String, triggerAtMs: Long) {
        val alarmManager = context.getSystemService(android.app.AlarmManager::class.java)
        val pendingIntent = buildPendingIntent(taskId, taskTitle)
        // setWindow: ±15 min tolerance, no exact-alarm permission needed
        alarmManager.setWindow(
            android.app.AlarmManager.RTC_WAKEUP,
            triggerAtMs - 15 * 60 * 1000,
            30 * 60 * 1000,
            pendingIntent
        )
    }

    private fun cancelAlarm(taskId: Long) {
        val alarmManager = context.getSystemService(android.app.AlarmManager::class.java)
        val pendingIntent = buildPendingIntent(taskId, "")
        alarmManager.cancel(pendingIntent)
    }

    private fun buildPendingIntent(taskId: Long, taskTitle: String): android.app.PendingIntent {
        val intent = android.content.Intent(context, DeadlineReminderReceiver::class.java).apply {
            putExtra(DeadlineReminderReceiver.EXTRA_TASK_ID, taskId)
            putExtra(DeadlineReminderReceiver.EXTRA_TASK_TITLE, taskTitle)
        }
        return android.app.PendingIntent.getBroadcast(
            context,
            (NOTIF_ID_BASE + taskId).toInt(),
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun sendNotification(notifId: Int, title: String, body: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (e: SecurityException) {
            MushroomLogger.e(TAG, "POST_NOTIFICATIONS permission not granted", e)
        }
    }
}
