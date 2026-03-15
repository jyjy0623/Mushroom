package com.mushroom.service.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import com.mushroom.core.logging.MushroomLogger

private const val TAG = "DeadlineReminderReceiver"
private const val CHANNEL_ID = "task_reminder"

/**
 * 接收 AlarmManager 触发的任务截止提醒，并发送通知。
 */
class DeadlineReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TASK_TITLE = "task_title"
        const val EXTRA_NOTIF_TITLE = "notif_title"
        const val EXTRA_NOTIF_BODY = "notif_body"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "任务"
        val notifTitle = intent.getStringExtra(EXTRA_NOTIF_TITLE)
        val notifBody = intent.getStringExtra(EXTRA_NOTIF_BODY)

        val title = notifTitle ?: "任务快到截止时间了"
        val body = notifBody ?: "「$taskTitle」还有 30 分钟截止，加油！"

        MushroomLogger.i(TAG, "reminder triggered for task $taskId: $taskTitle")

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.notify(taskId.toInt(), notification)
        } catch (e: SecurityException) {
            MushroomLogger.e(TAG, "POST_NOTIFICATIONS permission not granted", e)
        }
    }
}
