package com.cyd.timemaster.data

import android.content.Context

/** 轻量设置存储：服务器地址、配对 token、各提醒时刻、自动同步开关等。 */
class SettingsStore(context: Context) {
    private val sp = context.getSharedPreferences("timemaster_settings", Context.MODE_PRIVATE)

    var serverBase: String
        get() = sp.getString(KEY_SERVER, "").orEmpty().trimEnd('/')
        set(value) = sp.edit().putString(KEY_SERVER, value.trimEnd('/')).apply()

    var token: String
        get() = sp.getString(KEY_TOKEN, "").orEmpty()
        set(value) = sp.edit().putString(KEY_TOKEN, value).apply()

    val isPaired: Boolean get() = serverBase.isNotEmpty() && token.isNotEmpty()

    /** 计时超时提醒阈值（分钟），0 = 关闭 */
    var timerOverMinutes: Int
        get() = sp.getInt(KEY_TIMER_OVER, 120)
        set(value) = sp.edit().putInt(KEY_TIMER_OVER, value).apply()

    /** 当天到此时刻仍未开始计时的提醒，"HH:mm"，空串 = 关闭 */
    var noStartRemindTime: String
        get() = sp.getString(KEY_NO_START, "10:00").orEmpty()
        set(value) = sp.edit().putString(KEY_NO_START, value).apply()

    /** 晨间待办汇总时刻 */
    var morningSummaryTime: String
        get() = sp.getString(KEY_MORNING, "08:30").orEmpty()
        set(value) = sp.edit().putString(KEY_MORNING, value).apply()

    /** 晚间写计划提醒时刻 */
    var nightlyPlanTime: String
        get() = sp.getString(KEY_NIGHTLY, "22:00").orEmpty()
        set(value) = sp.edit().putString(KEY_NIGHTLY, value).apply()

    /** 自动同步开关（App 启动 + 每小时） */
    var autoSync: Boolean
        get() = sp.getBoolean(KEY_AUTO_SYNC, true)
        set(value) = sp.edit().putBoolean(KEY_AUTO_SYNC, value).apply()

    fun pair(base: String, token: String) {
        serverBase = base
        this.token = token
    }

    private companion object {
        const val KEY_SERVER = "server_base"
        const val KEY_TOKEN = "token"
        const val KEY_TIMER_OVER = "timer_over_minutes"
        const val KEY_NO_START = "no_start_remind_time"
        const val KEY_MORNING = "morning_summary_time"
        const val KEY_NIGHTLY = "nightly_plan_time"
        const val KEY_AUTO_SYNC = "auto_sync"
    }
}
