package com.cyd.timemaster

import android.app.Application
import com.cyd.timemaster.data.AppGraph
import com.cyd.timemaster.util.Notifications

class TimeMasterApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        Notifications.createChannels(this)
    }
}
