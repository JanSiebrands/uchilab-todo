package de.uchilab.todo.data

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver

/**
 * The task database. Format version 1 (ADR 0003). No destructive fallback and no query callback:
 * a failure must never silently delete tasks or write their content anywhere.
 */
@Database(entities = [TaskEntity::class], version = 1, exportSchema = true)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        /** File name only, never a path: Room resolves it with `Context.getDatabasePath`, i.e. the `database` backup domain. */
        const val DATABASE_NAME = "tasks.db"

        fun create(context: Context): TaskDatabase =
            Room.databaseBuilder(context.applicationContext, TaskDatabase::class.java, DATABASE_NAME)
                .setDriver(AndroidSQLiteDriver())
                .build()
    }
}
