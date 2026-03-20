package com.webvault.browser

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [DownloadEntity::class], version = 1, exportSchema = false)
abstract class WebvaultDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var instance: WebvaultDatabase? = null

        fun get(context: Context): WebvaultDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WebvaultDatabase::class.java,
                    "webvault.db"
                ).build().also { instance = it }
            }
        }
    }
}
