package com.cyd.timemaster.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY rowid")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE uuid = :uuid")
    suspend fun byUuid(uuid: String): Category?

    @Query("SELECT * FROM categories")
    suspend fun all(): List<Category>

    @Upsert suspend fun upsert(category: Category)

    @Delete suspend fun delete(category: Category)
}

@Dao
interface TimeRecordDao {
    @Query("SELECT * FROM time_records WHERE startAt >= :from AND startAt < :to ORDER BY startAt")
    fun observeBetween(from: Long, to: Long): Flow<List<TimeRecord>>

    @Query("SELECT * FROM time_records WHERE startAt >= :from AND startAt < :to ORDER BY startAt")
    suspend fun between(from: Long, to: Long): List<TimeRecord>

    @Query("SELECT * FROM time_records WHERE endAt IS NULL LIMIT 1")
    fun observeRunning(): Flow<TimeRecord?>

    @Query("SELECT * FROM time_records WHERE endAt IS NULL LIMIT 1")
    suspend fun running(): TimeRecord?

    @Query("SELECT * FROM time_records WHERE uuid = :uuid")
    suspend fun byUuid(uuid: String): TimeRecord?

    @Upsert suspend fun upsert(record: TimeRecord)

    @Query("DELETE FROM time_records WHERE uuid = :uuid")
    suspend fun delete(uuid: String)

    @Query("SELECT * FROM time_records")
    suspend fun all(): List<TimeRecord>
}

@Dao
interface DailyLogDao {
    @Query("SELECT * FROM daily_logs ORDER BY date DESC, updatedAt DESC")
    fun observeAll(): Flow<List<DailyLog>>

    @Query("SELECT * FROM daily_logs WHERE date = :date LIMIT 1")
    fun observeByDate(date: String): Flow<DailyLog?>

    @Query("SELECT * FROM daily_logs WHERE uuid = :uuid")
    suspend fun byUuid(uuid: String): DailyLog?

    @Query("SELECT * FROM daily_logs")
    suspend fun all(): List<DailyLog>

    @Upsert suspend fun upsert(log: DailyLog)

    @Query("DELETE FROM daily_logs WHERE uuid = :uuid")
    suspend fun delete(uuid: String)
}

@Dao
interface LogImageDao {
    @Query("SELECT * FROM log_images WHERE logUuid = :logUuid")
    fun observeForLog(logUuid: String): Flow<List<LogImage>>

    @Query("SELECT * FROM log_images")
    fun observeAll(): Flow<List<LogImage>>

    @Query("SELECT * FROM log_images WHERE logUuid = :logUuid")
    suspend fun forLog(logUuid: String): List<LogImage>

    @Query("SELECT * FROM log_images")
    suspend fun all(): List<LogImage>

    @Upsert suspend fun upsert(image: LogImage)

    @Query("DELETE FROM log_images WHERE uuid = :uuid")
    suspend fun delete(uuid: String)

    @Query("SELECT * FROM log_images WHERE uuid = :uuid")
    suspend fun byUuid(uuid: String): LogImage?
}

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos ORDER BY status, remindAt IS NULL, remindAt, updatedAt DESC")
    fun observeAll(): Flow<List<Todo>>

    @Query("SELECT * FROM todos WHERE status = 'pending'")
    suspend fun pending(): List<Todo>

    @Query("SELECT * FROM todos")
    suspend fun all(): List<Todo>

    @Upsert suspend fun upsert(todo: Todo)

    @Query("DELETE FROM todos WHERE uuid = :uuid")
    suspend fun delete(uuid: String)
}
