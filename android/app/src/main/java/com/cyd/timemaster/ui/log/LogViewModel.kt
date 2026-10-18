package com.cyd.timemaster.ui.log

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cyd.timemaster.data.AppGraph
import com.cyd.timemaster.data.db.DailyLog
import com.cyd.timemaster.data.db.LogImage
import com.cyd.timemaster.util.deleteQuietly
import com.cyd.timemaster.util.newId
import com.cyd.timemaster.util.nowIso
import com.cyd.timemaster.util.today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LogViewModel(private val logUuid: String?) : ViewModel() {
    private val logDao = AppGraph.db.dailyLogDao()
    private val imageDao = AppGraph.db.logImageDao()

    /** 数据库行 uuid：编辑已有日志时等于传入值，新建时立即生成 */
    private val rowUuid: String = logUuid ?: newId()
    private val isNew = logUuid == null

    val logs: StateFlow<List<DailyLog>> = logDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** logUuid → 该日志的图片列表（列表页缩略图用） */
    val imagesByLog: StateFlow<Map<String, List<LogImage>>> = imageDao.observeAll()
        .map { list -> list.groupBy { it.logUuid } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val date = MutableStateFlow(today())
    val content = MutableStateFlow("")
    val images = MutableStateFlow<List<LogImage>>(emptyList())

    init {
        viewModelScope.launch {
            if (isNew) {
                // 先落一行空日志，图片操作才有挂靠；退出时若仍为空会自动清理
                logDao.upsert(DailyLog(uuid = rowUuid, date = today(), content = "", updatedAt = nowIso()))
            } else {
                logDao.byUuid(logUuid)?.let { log ->
                    date.value = log.date
                    content.value = log.content
                }
                imageDao.observeForLog(logUuid).collect { images.value = it }
            }
        }
    }

    fun updateContent(text: String) {
        content.value = text
        save()
    }

    fun updateDate(newDate: String) {
        date.value = newDate
        save()
    }

    fun save() {
        AppGraph.scope.launch {
            logDao.upsert(
                DailyLog(uuid = rowUuid, date = date.value, content = content.value, updatedAt = nowIso())
            )
        }
    }

    /** 从相册选择的图片复制进应用私有目录并入库 */
    fun addImages(uris: List<Uri>, context: Context) {
        AppGraph.scope.launch {
            val dir = File(context.filesDir, "log_images").apply { mkdirs() }
            uris.forEach { uri ->
                val ext = when (context.contentResolver.getType(uri)) {
                    "image/png" -> ".png"
                    "image/webp" -> ".webp"
                    "image/gif" -> ".gif"
                    else -> ".jpg"
                }
                val imageId = newId()
                val file = File(dir, "$imageId$ext")
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                imageDao.upsert(
                    LogImage(uuid = imageId, logUuid = rowUuid, localPath = file.absolutePath, updatedAt = nowIso())
                )
            }
        }
    }

    fun deleteImage(image: LogImage) {
        AppGraph.scope.launch {
            imageDao.delete(image.uuid)
            withContext(Dispatchers.IO) { deleteQuietly(File(image.localPath)) }
        }
    }

    /** 退出编辑：新建的空日志（无内容且无图片）自动清理 */
    fun onClose() {
        if (!isNew) return
        AppGraph.scope.launch {
            if (content.value.isBlank() && imageDao.forLog(rowUuid).isEmpty()) {
                logDao.delete(rowUuid)
            }
        }
    }

    fun deleteLog(log: DailyLog) {
        AppGraph.scope.launch {
            imageDao.forLog(log.uuid).forEach { image ->
                imageDao.delete(image.uuid)
                withContext(Dispatchers.IO) { deleteQuietly(File(image.localPath)) }
            }
            logDao.delete(log.uuid)
        }
    }

    class Factory(private val logUuid: String?) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LogViewModel(logUuid) as T
    }
}
