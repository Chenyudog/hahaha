package com.cyd.timemaster.ui.me

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.cyd.timemaster.data.AppGraph
import com.cyd.timemaster.data.remote.ApiClient
import com.cyd.timemaster.data.remote.PairRequest
import com.cyd.timemaster.reminder.ReminderScheduler
import com.cyd.timemaster.util.WallpaperSetter
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {
    private val settings = AppGraph.settings

    var serverBase by mutableStateOf(settings.serverBase)
        private set
    var pairCode by mutableStateOf("")
        private set
    var pairingMessage by mutableStateOf<String?>(null)
        private set
    var paired by mutableStateOf(settings.isPaired)
        private set

    var morningTime by mutableStateOf(settings.morningSummaryTime)
        private set
    var nightlyTime by mutableStateOf(settings.nightlyPlanTime)
        private set
    var noStartTime by mutableStateOf(settings.noStartRemindTime)
        private set
    var timerOverMinutes by mutableStateOf(settings.timerOverMinutes.toString())
        private set
    var autoSync by mutableStateOf(settings.autoSync)
        private set

    var wallpaperMessage by mutableStateOf<String?>(null)
        private set

    fun updateServerBase(v: String) { serverBase = v; pairingMessage = null }
    fun updatePairCode(v: String) { pairCode = v; pairingMessage = null }

    /** 用配对码换长期 token */
    fun pair() {
        val base = serverBase.trim()
        if (!base.startsWith("http")) {
            pairingMessage = "地址要以 http:// 开头，例如 http://192.168.3.222:8000"
            return
        }
        pairingMessage = "配对中…"
        AppGraph.scope.launch {
            try {
                val resp = ApiClient.api(base).pair(PairRequest(pairCode.trim()))
                AppGraph.scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    settings.pair(base, resp.token)
                    paired = true
                    pairingMessage = "配对成功（服务器 v${resp.serverVersion}）"
                }
            } catch (e: Exception) {
                AppGraph.scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    paired = false
                    pairingMessage = "配对失败：${e.message ?: "网络错误"}（检查电脑和手机是否在同一网络、服务器是否启动）"
                }
            }
        }
    }

    fun updateMorningTime(v: String, context: Context) {
        morningTime = v; settings.morningSummaryTime = v; reschedule(context)
    }

    fun updateNightlyTime(v: String, context: Context) {
        nightlyTime = v; settings.nightlyPlanTime = v; reschedule(context)
    }

    fun updateNoStartTime(v: String, context: Context) {
        noStartTime = v; settings.noStartRemindTime = v; reschedule(context)
    }

    fun updateTimerOverMinutes(v: String) {
        timerOverMinutes = v
        settings.timerOverMinutes = v.toIntOrNull()?.coerceIn(0, 24 * 60) ?: 120
    }

    fun updateAutoSync(v: Boolean) {
        autoSync = v; settings.autoSync = v
    }

    private fun reschedule(context: Context) {
        ReminderScheduler.scheduleAll(context.applicationContext)
    }

    fun setWallpaper(uri: Uri, alsoLock: Boolean, context: Context) {
        wallpaperMessage = "设置中…"
        AppGraph.scope.launch {
            val error = WallpaperSetter.setFromUri(context.applicationContext, uri, alsoLock)
            AppGraph.scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                wallpaperMessage = if (error == null) "壁纸已设置" else "设置失败：$error"
            }
        }
    }
}
