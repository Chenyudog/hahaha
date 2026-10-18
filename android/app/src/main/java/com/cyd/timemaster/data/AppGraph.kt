package com.cyd.timemaster.data

import android.content.Context
import com.cyd.timemaster.data.db.AppDatabase
import com.cyd.timemaster.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 全局单例：数据库、协程作用域、设置存储。在 TimeMasterApp.onCreate 里初始化。 */
object AppGraph {
    lateinit var db: AppDatabase
        private set
    lateinit var scope: CoroutineScope
        private set
    lateinit var settings: SettingsStore
        private set

    fun init(context: Context) {
        if (::db.isInitialized) return
        db = AppDatabase.get(context)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        settings = SettingsStore(context)
    }
}
