package com.cyd.timemaster.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 计时事项（如"学习数学"） */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val uuid: String,
    val name: String,
    val color: Long = 0xFF607D8BL,
    val updatedAt: String,
)

/** 一条计时记录；endAt 为 null 表示正在进行 */
@Entity(tableName = "time_records")
data class TimeRecord(
    @PrimaryKey val uuid: String,
    val categoryUuid: String,
    val startAt: Long,
    val endAt: Long?,
    val note: String = "",
    val updatedAt: String,
)

/** 文件夹：日志与待办共用的分组（如按日期命名"2026-10-20"） */
@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey val uuid: String,
    val name: String,
    val createdAt: String,
)

/** 复习日志：按天记录"今天干了什么"；folderUuid 为 null 表示未分类 */
@Entity(tableName = "daily_logs")
data class DailyLog(
    @PrimaryKey val uuid: String,
    val date: String,          // yyyy-MM-dd
    val content: String,
    val updatedAt: String,
    val folderUuid: String? = null,
)

/** 日志图片：localPath 指向应用私有目录中的文件 */
@Entity(tableName = "log_images")
data class LogImage(
    @PrimaryKey val uuid: String,
    val logUuid: String,
    val localPath: String,
    val remoteId: String? = null,   // 上传到服务器后的 id
    val updatedAt: String,
)

/** 待办；folderUuid 为 null 表示未分类 */
@Entity(tableName = "todos")
data class Todo(
    @PrimaryKey val uuid: String,
    val content: String,
    val dueAt: Long? = null,
    val remindAt: Long? = null,
    val status: String = "pending",   // pending / done
    val doneAt: Long? = null,
    val updatedAt: String,
    val folderUuid: String? = null,
)

/** AI 直连调用流水：手机本地记账（v0.8.0 起 AI 不再经电脑中转） */
@Entity(tableName = "agent_calls")
data class AgentCall(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val model: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val cost: Double,
    val purpose: String,
)
