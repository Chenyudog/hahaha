package com.cyd.timemaster.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 计时事项（如"学习数学"） */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val uuid: String,
    val name: String,
    val color: Long = 0xFF607D8BUL,
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

/** 复习日志：按天记录"今天干了什么" */
@Entity(tableName = "daily_logs")
data class DailyLog(
    @PrimaryKey val uuid: String,
    val date: String,          // yyyy-MM-dd
    val content: String,
    val updatedAt: String,
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

/** 待办 */
@Entity(tableName = "todos")
data class Todo(
    @PrimaryKey val uuid: String,
    val content: String,
    val dueAt: Long? = null,
    val remindAt: Long? = null,
    val status: String = "pending",   // pending / done
    val doneAt: Long? = null,
    val updatedAt: String,
)
