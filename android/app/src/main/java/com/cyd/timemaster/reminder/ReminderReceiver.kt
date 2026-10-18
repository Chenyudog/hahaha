package com.cyd.timemaster.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cyd.timemaster.data.AppGraph
import com.cyd.timemaster.util.Notifications
import com.cyd.timemaster.util.endOfDay
import com.cyd.timemaster.util.startOfDay
import kotlinx.coroutines.launch

/** 闹钟触发：按 action 发对应通知，并把每日提醒排到下一天 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        AppGraph.scope.launch {
            try {
                when (intent.action) {
                    ReminderScheduler.ACTION_MORNING -> {
                        val pending = AppGraph.db.todoDao().pending()
                        val overdue = pending.count { (it.dueAt ?: Long.MAX_VALUE) < System.currentTimeMillis() }
                        Notifications.postReminder(
                            context, Notifications.ID_REMINDER_BASE + 10,
                            "今日待办",
                            if (pending.isEmpty()) "今天没有待办，轻松一天！"
                            else "今日待办 ${pending.size} 件" + if (overdue > 0) "，其中 $overdue 件已过期，抓紧处理吧" else "",
                        )
                        ReminderScheduler.scheduleAll(context)
                    }
                    ReminderScheduler.ACTION_NIGHTLY -> {
                        Notifications.postReminder(
                            context, Notifications.ID_REMINDER_BASE + 11,
                            "写明天的计划",
                            "花 5 分钟写一下明天的计划吧，打开 App → 日志 → 明天的日期",
                        )
                        ReminderScheduler.scheduleAll(context)
                    }
                    ReminderScheduler.ACTION_NO_START -> {
                        val records = AppGraph.db.timeRecordDao().between(startOfDay(), endOfDay())
                        if (records.isEmpty()) {
                            Notifications.postReminder(
                                context, Notifications.ID_REMINDER_BASE + 12,
                                "时间记录",
                                "今天还没开始记录时间哦，选择一个事项开始计时吧",
                            )
                        }
                        ReminderScheduler.scheduleAll(context)
                    }
                    ReminderScheduler.ACTION_TODO -> {
                        val uuid = intent.getStringExtra(ReminderScheduler.EXTRA_TODO_UUID) ?: return@launch
                        AppGraph.db.todoDao().all().firstOrNull { it.uuid == uuid && it.status == "pending" }
                            ?.let {
                                Notifications.postReminder(
                                    context, Notifications.ID_REMINDER_BASE + 20 + (uuid.hashCode() and 0xFF),
                                    "待办提醒", it.content,
                                )
                            }
                    }
                }
            } finally {
                result.finish()
            }
        }
    }
}
