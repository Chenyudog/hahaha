package com.cyd.timemaster.sync

import android.content.Context
import com.cyd.timemaster.data.AppGraph
import com.cyd.timemaster.data.remote.ApiClient
import com.cyd.timemaster.data.remote.BackupAck
import com.cyd.timemaster.data.remote.CategoryDto
import com.cyd.timemaster.data.remote.DailyLogDto
import com.cyd.timemaster.data.remote.LogImageDto
import com.cyd.timemaster.data.remote.Snapshot
import com.cyd.timemaster.data.remote.TimeRecordDto
import com.cyd.timemaster.data.remote.TodoDto
import com.cyd.timemaster.data.db.Category
import com.cyd.timemaster.data.db.DailyLog
import com.cyd.timemaster.data.db.LogImage
import com.cyd.timemaster.data.db.TimeRecord
import com.cyd.timemaster.data.db.Todo
import com.cyd.timemaster.reminder.ReminderScheduler
import com.cyd.timemaster.util.nowIso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/** 云端备份与恢复：手机端数据 ⇄ 电脑服务器 */
object SyncManager {

    sealed interface Outcome {
        data class Ok(val message: String) : Outcome
        data class Fail(val message: String) : Outcome
    }

    private fun api() = AppGraph.settings.let {
        ApiClient.api(it.serverBase, it.token)
    }

    private fun imageDir(context: Context): File =
        File(context.filesDir, "log_images").apply { mkdirs() }

    /** 把本地图片中还没有 remoteId 的先上传，然后整包 sync + 全量备份 */
    suspend fun backupNow(context: Context): Outcome {
        val settings = AppGraph.settings
        if (!settings.isPaired) return Outcome.Fail("请先在「我的」页完成服务器配对")
        return withContext(Dispatchers.IO) {
            try {
                val client = api()
                // 1. 上传未同步的图片
                val images = AppGraph.db.logImageDao().all()
                images.filter { it.remoteId == null && File(it.localPath).exists() }.forEach { image ->
                    val file = File(image.localPath)
                    val mime = when (file.extension.lowercase()) {
                        "png" -> "image/png"
                        "webp" -> "image/webp"
                        "gif" -> "image/gif"
                        else -> "image/jpeg"
                    }
                    val part = MultipartBody.Part.createFormData(
                        "file", file.name, file.asRequestBody(mime.toMediaTypeOrNull())
                    )
                    val resp = client.uploadImage(part)
                    AppGraph.db.logImageDao().upsert(image.copy(remoteId = resp.id, updatedAt = nowIso()))
                }
                // 2. 数据 + 全量备份快照
                val snapshot = buildSnapshot()
                client.sync(snapshot)
                val ack: BackupAck = client.backupFull(snapshot)
                Outcome.Ok("备份完成（${ack.ts.ifEmpty { "成功" }}）")
            } catch (e: Exception) {
                Outcome.Fail("备份失败：${e.message ?: "网络错误"}")
            }
        }
    }

    /** 从服务器拉最新快照覆盖本地，并回填图片文件 */
    suspend fun restore(context: Context): Outcome {
        val settings = AppGraph.settings
        if (!settings.isPaired) return Outcome.Fail("请先在「我的」页完成服务器配对")
        return withContext(Dispatchers.IO) {
            try {
                val client = api()
                val resp = client.restore()
                val snap = resp.data
                val db = AppGraph.db
                db.clearAllTables()

                snap.categories.forEach {
                    db.categoryDao().upsert(
                        Category(it.uuid, it.name, it.color?.toLongOrNull() ?: 0xFF607D8BUL, it.updatedAt ?: nowIso())
                    )
                }
                snap.timeRecords.forEach {
                    db.timeRecordDao().upsert(
                        TimeRecord(
                            it.uuid, it.categoryUuid ?: "", it.startAt.toLongOrNull() ?: 0L,
                            it.endAt?.toLongOrNull(), it.note.orEmpty(), it.updatedAt ?: nowIso(),
                        )
                    )
                }
                snap.dailyLogs.forEach {
                    db.dailyLogDao().upsert(DailyLog(it.uuid, it.date, it.content, it.updatedAt ?: nowIso()))
                }
                snap.todos.forEach {
                    db.todoDao().upsert(
                        Todo(
                            it.uuid, it.content, it.dueAt?.toLongOrNull(), it.remindAt?.toLongOrNull(),
                            it.status, it.doneAt?.toLongOrNull(), it.updatedAt ?: nowIso(),
                        )
                    )
                }
                val dir = imageDir(context)
                snap.logImages.forEach { dto ->
                    // 本地已有同名文件则直接复用，否则从服务器下载
                    val existing = dir.listFiles { f -> f.nameWithoutExtension == dto.uuid }?.firstOrNull()
                    val path = existing?.absolutePath
                        ?: dto.remoteId?.let { remoteId -> downloadImage(client, dir, remoteId, dto.uuid) }
                    db.logImageDao().upsert(
                        LogImage(dto.uuid, dto.logUuid ?: "", path.orEmpty(), dto.remoteId, dto.updatedAt ?: nowIso())
                    )
                }
                ReminderScheduler.scheduleAll(context)
                Outcome.Ok("恢复完成（快照 ${resp.file}）")
            } catch (e: Exception) {
                Outcome.Fail("恢复失败：${e.message ?: "网络错误"}")
            }
        }
    }

    private suspend fun downloadImage(
        client: com.cyd.timemaster.data.remote.TimeApi,
        dir: File, remoteId: String, uuid: String,
    ): String? = try {
        val body = client.fetchImage(remoteId)
        val ext = when (body.contentType()?.subtype) {
            "png" -> "png"
            "webp" -> "webp"
            "gif" -> "gif"
            else -> "jpg"
        }
        val file = File(dir, "$uuid.$ext")
        file.writeBytes(body.bytes())
        file.absolutePath
    } catch (_: Exception) {
        null
    }

    suspend fun buildSnapshot(): Snapshot = withContext(Dispatchers.IO) {
        val db = AppGraph.db
        Snapshot(
            categories = db.categoryDao().all().map {
                CategoryDto(it.uuid, it.name, it.color.toString(), it.updatedAt)
            },
            timeRecords = db.timeRecordDao().all().map {
                TimeRecordDto(it.uuid, it.categoryUuid, it.startAt.toString(), it.endAt?.toString(), it.note, it.updatedAt)
            },
            dailyLogs = db.dailyLogDao().all().map {
                DailyLogDto(it.uuid, it.date, it.content, it.updatedAt)
            },
            logImages = db.logImageDao().all().map {
                LogImageDto(it.uuid, it.logUuid, it.remoteId, it.updatedAt)
            },
            todos = db.todoDao().all().map {
                TodoDto(
                    it.uuid, it.content, it.dueAt?.toString(), it.remindAt?.toString(),
                    it.status, it.doneAt?.toString(), it.updatedAt,
                )
            },
        )
    }
}
