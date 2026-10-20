package com.cyd.timemaster.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Category::class, TimeRecord::class, DailyLog::class, LogImage::class, Todo::class, AgentCall::class, Folder::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun timeRecordDao(): TimeRecordDao
    abstract fun dailyLogDao(): DailyLogDao
    abstract fun logImageDao(): LogImageDao
    abstract fun todoDao(): TodoDao
    abstract fun agentCallDao(): AgentCallDao
    abstract fun folderDao(): FolderDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /** v1→v2：新增 AI 调用流水表（保留原有数据） */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `agent_calls` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`ts` INTEGER NOT NULL, `model` TEXT NOT NULL, " +
                        "`promptTokens` INTEGER NOT NULL, `completionTokens` INTEGER NOT NULL, " +
                        "`cost` REAL NOT NULL, `purpose` TEXT NOT NULL)"
                )
            }
        }

        /** v2→v3：新增文件夹表，日志/待办挂 folderUuid（保留原有数据） */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `folders` (" +
                        "`uuid` TEXT NOT NULL PRIMARY KEY, " +
                        "`name` TEXT NOT NULL, `createdAt` TEXT NOT NULL)"
                )
                db.execSQL("ALTER TABLE `daily_logs` ADD COLUMN `folderUuid` TEXT")
                db.execSQL("ALTER TABLE `todos` ADD COLUMN `folderUuid` TEXT")
            }
        }

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "timemaster.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
        }
    }
}
