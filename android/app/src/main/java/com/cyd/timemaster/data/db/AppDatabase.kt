package com.cyd.timemaster.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Category::class, TimeRecord::class, DailyLog::class, LogImage::class, Todo::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun timeRecordDao(): TimeRecordDao
    abstract fun dailyLogDao(): DailyLogDao
    abstract fun logImageDao(): LogImageDao
    abstract fun todoDao(): TodoDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "timemaster.db"
            ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}
