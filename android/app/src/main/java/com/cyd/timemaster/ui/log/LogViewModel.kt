package com.cyd.timemaster.ui.log

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cyd.timemaster.ai.ChatMessage
import com.cyd.timemaster.ai.DeepSeekClient
import com.cyd.timemaster.data.AppGraph
import com.cyd.timemaster.data.db.AgentCall
import com.cyd.timemaster.data.db.DailyLog
import com.cyd.timemaster.data.db.Folder
import com.cyd.timemaster.data.db.FolderFilters
import com.cyd.timemaster.data.db.LogImage
import com.cyd.timemaster.data.remote.ApiClient
import com.cyd.timemaster.data.remote.PairRequest
import com.cyd.timemaster.reminder.ReminderScheduler
import com.cyd.timemaster.util.WallpaperSetter
import com.cyd.timemaster.util.deleteQuietly
import com.cyd.timemaster.util.newId
import com.cyd.timemaster.util.nowIso
import com.cyd.timemaster.util.today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class LogViewModel(private val logUuid: String?, private val initialFolderUuid: String?) : ViewModel() {
    private val logDao = AppGraph.db.dailyLogDao()
    private val imageDao = AppGraph.db.logImageDao()
    private val folderDao = AppGraph.db.folderDao()

    /** 数据库行 uuid：编辑已有日志时等于传入值，新建时立即生成 */
    private val rowUuid: String = logUuid ?: newId()
    private val isNew = logUuid == null

    val logs: StateFlow<List<DailyLog>> = logDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val folders: StateFlow<List<Folder>> = folderDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** logUuid → 该日志的图片列表（列表页缩略图用） */
    val imagesByLog: StateFlow<Map<String, List<LogImage>>> = imageDao.observeAll()
        .map { list -> list.groupBy { it.logUuid } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 当前视图过滤：全部 / 未分类 / 某文件夹（FolderFilters） */
    val filter = MutableStateFlow(FolderFilters.ALL)
    val visibleLogs: StateFlow<List<DailyLog>> = filter
        .flatMapLatest { f ->
            when (f) {
                FolderFilters.ALL -> logDao.observeAll()
                FolderFilters.UNCAT -> logDao.observeUncategorized()
                else -> logDao.observeInFolder(f)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val date = MutableStateFlow(today())
    val content = MutableStateFlow("")
    val folderUuid = MutableStateFlow(initialFolderUuid)
    val images = MutableStateFlow<List<LogImage>>(emptyList())

    init {
        viewModelScope.launch {
            if (isNew) {
                // 先落一行空日志，图片操作才有挂靠；退出时若仍为空会自动清理
                logDao.upsert(
                    DailyLog(uuid = rowUuid, date = today(), content = "", updatedAt = nowIso(), folderUuid = initialFolderUuid)
                )
            } else {
                logDao.byUuid(rowUuid)?.let { log ->
                    date.value = log.date
                    content.value = log.content
                    folderUuid.value = log.folderUuid
                }
                imageDao.observeForLog(rowUuid).collect { images.value = it }
            }
        }
    }

    fun setFilter(f: String) {
        filter.value = f
    }

    fun updateContent(text: String) {
        content.value = text
        save()
    }

    fun updateDate(newDate: String) {
        date.value = newDate
        save()
    }

    fun updateFolder(uuid: String?) {
        folderUuid.value = uuid
        save()
    }

    fun save() {
        AppGraph.scope.launch {
            logDao.upsert(
                DailyLog(
                    uuid = rowUuid, date = date.value, content = content.value,
                    updatedAt = nowIso(), folderUuid = folderUuid.value,
                )
            )
        }
    }

    // ---- 文件夹管理 ----

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        AppGraph.scope.launch { folderDao.upsert(Folder(uuid = newId(), name = trimmed, createdAt = nowIso())) }
    }

    fun renameFolder(folder: Folder, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        AppGraph.scope.launch { folderDao.upsert(folder.copy(name = trimmed)) }
    }

    /** 删除文件夹：里面的日志/待办回到未分类，不删内容 */
    fun deleteFolder(folder: Folder) {
        AppGraph.scope.launch {
            logDao.clearFolder(folder.uuid)
            AppGraph.db.todoDao().clearFolder(folder.uuid)
            folderDao.delete(folder.uuid)
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

    // ---- AI 复习巩固（手机直连 DeepSeek）----

    sealed interface ReviewState {
        data object Idle : ReviewState
        data object Loading : ReviewState
        data class Success(val content: String, val cost: Double) : ReviewState
        data class Error(val message: String) : ReviewState
    }

    val reviewState = MutableStateFlow<ReviewState>(ReviewState.Idle)

    fun requestReview() {
        val text = content.value.trim()
        if (text.isEmpty() || reviewState.value == ReviewState.Loading) return
        val settings = AppGraph.settings
        if (!settings.aiReady) {
            reviewState.value = ReviewState.Error("请先到「我的」页 → AI 直连（DeepSeek）配置 API Key")
            return
        }
        reviewState.value = ReviewState.Loading
        AppGraph.scope.launch {
            try {
                val result = DeepSeekClient.chat(
                    settings.aiKey, settings.aiModel,
                    listOf(
                        ChatMessage(
                            "system",
                            "你是帮助用户复习巩固的中文学习助手。要求：1.输出非常简短，总共不超过150字；" +
                                "2.格式固定：最多3条【要点】回顾 + 2个【自测】问题；3.不要客套话。"
                        ),
                        ChatMessage("user", text.take(4000)),
                    ),
                )
                AppGraph.db.agentCallDao().insert(
                    AgentCall(
                        ts = System.currentTimeMillis(), model = result.model,
                        promptTokens = result.promptTokens, completionTokens = result.completionTokens,
                        cost = result.costYuan, purpose = "daily_review",
                    )
                )
                reviewState.value = ReviewState.Success(result.content, result.costYuan)
            } catch (e: Exception) {
                reviewState.value = ReviewState.Error("AI 调用失败：${e.message ?: "网络错误"}")
            }
        }
    }

    fun dismissReview() {
        reviewState.value = ReviewState.Idle
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

    class Factory(
        private val logUuid: String?,
        private val initialFolderUuid: String? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LogViewModel(logUuid, initialFolderUuid) as T
    }
}
