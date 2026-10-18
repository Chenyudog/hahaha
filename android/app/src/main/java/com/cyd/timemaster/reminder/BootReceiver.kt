package com.cyd.timemaster.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机自启：重建所有提醒闹钟 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.scheduleAll(context)
        }
    }
}
