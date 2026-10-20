package com.cyd.timemaster.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import com.cyd.timemaster.data.AppGraph
import com.cyd.timemaster.data.db.Category
import com.cyd.timemaster.data.db.TimeRecord
import com.cyd.timemaster.util.Notifications
import com.cyd.timemaster.util.newId
import com.cyd.timemaster.util.nowIso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 正在计时的状态，UI 通过 [TimerService.running] 全局观察 */
data class RunningTimer(
    val recordUuid: String,
    val categoryUuid: String,
    val categoryName: String,
    val categoryColor: Long,
    val startAt: Long,
)

class TimerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var overNotified = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTimer(intent)
            ACTION_STOP -> stopTimer()
            else -> resumeIfNeeded()   // START_STICKY 被系统拉起时 intent 为 null
        }
        return START_STICKY
    }

    private fun startTimer(intent: Intent) {
        val uuid = intent.getStringExtra(EXTRA_CATEGORY_UUID)
        val name = intent.getStringExtra(EXTRA_CATEGORY_NAME).orEmpty()
        val color = intent.getLongExtra(EXTRA_CATEGORY_COLOR, 0xFF607D8BL)
        if (uuid == null) {
            stopSelf()
            return
        }
        val startAt = System.currentTimeMillis()
        overNotified = false
        AppGraph.scope.launch {
            val recordUuid = withContext(Dispatchers.IO) {
                val dao = AppGraph.db.timeRecordDao()
                // 同一时刻只允许一条计时：自动结束上一条
                dao.running()?.let { prev ->
                    dao.upsert(prev.copy(endAt = startAt - 1, updatedAt = nowIso()))
                }
                val record = TimeRecord(
                    uuid = newId(), categoryUuid = uuid,
                    startAt = startAt, endAt = null, updatedAt = nowIso(),
                )
                dao.upsert(record)
                record.uuid
            }
            withContext(Dispatchers.Main) {
                running.value = RunningTimer(recordUuid, uuid, name, color, startAt)
                beginForeground(name, startAt)
                scheduleOverReminder()
            }
        }
    }

    /** 进程被杀后 START_STICKY 拉起：从数据库恢复进行中的记录与常驻通知 */
    private fun resumeIfNeeded() {
        AppGraph.scope.launch {
            val record = AppGraph.db.timeRecordDao().running()
            if (record == null) {
                stopSelf()
                return@launch
            }
            val category = AppGraph.db.categoryDao().byUuid(record.categoryUuid)
            withContext(Dispatchers.Main) {
                running.value = RunningTimer(
                    record.uuid, record.categoryUuid,
                    category?.name ?: "(已删除事项)", category?.color ?: 0xFF607D8BL,
                    record.startAt,
                )
                beginForeground(category?.name ?: "(已删除事项)", record.startAt)
                scheduleOverReminder()
            }
        }
    }

    private fun stopTimer() {
        val current = running.value
        val endAt = System.currentTimeMillis()
        running.value = null
        handler.removeCallbacksAndMessages(null)
        AppGraph.scope.launch {
            val dao = AppGraph.db.timeRecordDao()
            val record = current?.let { dao.byUuid(it.recordUuid) } ?: dao.running()
            record?.let { dao.upsert(it.copy(endAt = endAt, updatedAt = nowIso())) }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun beginForeground(categoryName: String, startAt: Long) {
        val notification = Notifications.timerNotification(this, categoryName, startAt)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                Notifications.ID_TIMER, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(Notifications.ID_TIMER, notification)
        }
    }

    /** 单条计时超过阈值（默认 2 小时）提醒一次 */
    private fun scheduleOverReminder() {
        handler.removeCallbacksAndMessages(null)
        val minutes = AppGraph.settings.timerOverMinutes
        if (minutes <= 0) return
        handler.postDelayed({
            val r = running.value ?: return@postDelayed
            if (!overNotified) {
                overNotified = true
                Notifications.postReminder(
                    this, Notifications.ID_REMINDER_BASE + 1,
                    "计时超时提醒",
                    "「${r.categoryName}」已计时超过 $minutes 分钟，确认还在继续吗？",
                )
            }
        }, minutes * 60_000L)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.cyd.timemaster.timer.START"
        const val ACTION_STOP = "com.cyd.timemaster.timer.STOP"
        private const val EXTRA_CATEGORY_UUID = "uuid"
        private const val EXTRA_CATEGORY_NAME = "name"
        private const val EXTRA_CATEGORY_COLOR = "color"

        /** UI 观察这个流即可获知当前是否在计时 */
        val running = MutableStateFlow<RunningTimer?>(null)

        fun start(context: Context, category: Category) {
            val intent = Intent(context, TimerService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_CATEGORY_UUID, category.uuid)
                .putExtra(EXTRA_CATEGORY_NAME, category.name)
                .putExtra(EXTRA_CATEGORY_COLOR, category.color)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, TimerService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
