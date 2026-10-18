package com.cyd.timemaster

import android.app.Application
import com.cyd.timemaster.data.AppGraph
import com.cyd.timemaster.sync.SyncManager
import com.cyd.timemaster.sync.SyncWorker
import com.cyd.timemaster.util.Notifications
import kotlinx.coroutines.launch

class TimeMasterApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        Notifications.createChannels(this)
        SyncWorker.schedulePeriodic(this)
        // 启动时若已配对且开启了自动同步，立即同步一次（失败静默，下个整点重试）
        if (AppGraph.settings.autoSync && AppGraph.settings.isPaired) {
            AppGraph.scope.launch { SyncManager.backupNow(this@TimeMasterApp) }
        }
    }
}
