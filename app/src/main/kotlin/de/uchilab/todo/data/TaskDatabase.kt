package de.uchilab.todo.data

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase

/**
 * The task database. Format version 1 (ADR 0003). No destructive fallback and no query callback:
 * a failure must never silently delete tasks or write their content anywhere.
 * The driver never deletes or replaces an existing file, also not a damaged one.
 */
@Database(entities = [TaskEntity::class], version = 1, exportSchema = true)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        /** File name only, never a path: Room resolves it with `Context.getDatabasePath`, i.e. the `database` backup domain. */
        const val DATABASE_NAME = "tasks.db"

        fun create(context: Context): TaskDatabase =
            Room.databaseBuilder(context.applicationContext, TaskDatabase::class.java, DATABASE_NAME)
                .setDriver(KeepFileSQLiteDriver())
                // Must match OPEN_FLAGS of the driver: with AUTOMATIC, Room picks TRUNCATE on low-memory devices and the
                // driver would switch the file back to WAL on every start, writing its header each time.
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
