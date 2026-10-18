package com.cyd.timemaster.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.cyd.timemaster.data.AppGraph
import com.cyd.timemaster.util.localTimeToEpochMillisToday
import com.cyd.timemaster.util.parseHm
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 精确闹钟调度：每日三类提醒 + 待办到期提醒；重启后由 BootReceiver 重建 */
object ReminderScheduler {
    const val ACTION_MORNING = "com.cyd.timemaster.reminder.MORNING"
    const val ACTION_NIGHTLY = "com.cyd.timemaster.reminder.NIGHTLY"
    const val ACTION_NO_START = "com.cyd.timemaster.reminder.NO_START"
    const val ACTION_TODO = "com.cyd.timemaster.reminder.TODO"
    const val EXTRA_TODO_UUID = "todo_uuid"

    private const val REQ_MORNING = 3001
    private const val REQ_NIGHTLY = 3002
    private const val REQ_NO_START = 3003
    private const val REQ_TODO_BASE = 4000

    fun scheduleAll(context: Context) {
        val s = AppGraph.settings
        scheduleDaily(context, REQ_MORNING, s.morningSummaryTime, ACTION_MORNING)
        scheduleDaily(context, REQ_NIGHTLY, s.nightlyPlanTime, ACTION_NIGHTLY)
        scheduleDaily(context, REQ_NO_START, s.noStartRemindTime, ACTION_NO_START)
        scheduleTodos(context)
    }

    /** 保存/修改待办后调用：重排所有未来提醒，过期的自然失效 */
    fun scheduleTodos(context: Context) {
        val am = alarmManager(context)
        AppGraph.scope.launch {
            AppGraph.db.todoDao().pending().forEach { todo ->
                val remindAt = todo.remindAt ?: return@forEach
                if (remindAt <= System.currentTimeMillis()) return@forEach
                setExactCompat(am, remindAt, todoPendingIntent(context, todo.uuid))
            }
        }
    }

    private fun scheduleDaily(context: Context, requestCode: Int, hm: String, action: String) {
        val am = alarmManager(context)
        val time = parseHm(hm)
        if (time == null) {
            am.cancel(dailyPendingIntent(context, requestCode, action))
            return
        }
        var trigger = localTimeToEpochMillisToday(time)
        if (trigger <= System.currentTimeMillis()) {
            trigger = LocalDateTime.of(LocalDate.now().plusDays(1), time)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        setExactCompat(am, trigger, dailyPendingIntent(context, requestCode, action))
    }

    private fun setExactCompat(am: AlarmManager, triggerAt: Long, pi: PendingIntent) {
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            // 未授予精确闹钟权限时退化为窗口闹钟，提醒可能延迟几分钟
            am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 60_000L, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun alarmManager(context: Context): AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun dailyPendingIntent(context: Context, requestCode: Int, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, ReminderReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun todoPendingIntent(context: Context, todoUuid: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQ_TODO_BASE + (todoUuid.hashCode() and 0xFFFF),
            Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION_TODO)
                .putExtra(EXTRA_TODO_UUID, todoUuid),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
