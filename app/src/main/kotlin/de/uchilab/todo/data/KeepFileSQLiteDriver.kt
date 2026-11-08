package de.uchilab.todo.data

import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteConnection
import java.io.File

/**
 * Reaction of Android to a damaged database file: nothing, on purpose. Android's standard reaction
 * deletes the file and its companions and starts with an empty one, which would silently destroy all
 * tasks. Here the file stays untouched; Android then throws the exception itself and the app shows the
 * read failure. No log (security L2).
 */
internal class KeepFileOnCorruption : DatabaseErrorHandler {
    override fun onCorruption(dbObj: SQLiteDatabase) = Unit
}

/**
 * SQLite driver of the system database that never deletes or replaces an existing file (ADR 0003, UCH-78).
 *
 * An existing file of size 0 counts as damaged, but only if it is found when the process opens the
 * database the first time: while a database is created the file is briefly empty, and Room opens
 * further connections afterwards.
 */
internal class KeepFileSQLiteDriver : SQLiteDriver {
    override val hasConnectionPool: Boolean = false

    private val lock = Any()
    private var emptyFileFound: Boolean? = null

    override fun open(fileName: String): SQLiteConnection {
        if (emptyFileFoundAtFirstOpen(fileName)) throw SQLiteDatabaseCorruptException()
        val db = SQLiteDatabase.openDatabase(fileName, null, OPEN_FLAGS, KeepFileOnCorruption())
        return AndroidSQLiteConnection(db)
    }

    private fun emptyFileFoundAtFirstOpen(fileName: String): Boolean = synchronized(lock) {
        emptyFileFound ?: File(fileName).let { it.exists() && it.length() == 0L }.also { emptyFileFound = it }
    }

    private companion object {
        /** WAL is the mode of the database (Room sets it). Asking for it here stops Android from switching an existing file back and forth, which would write to its header. */
        const val OPEN_FLAGS = SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING
    }
}
