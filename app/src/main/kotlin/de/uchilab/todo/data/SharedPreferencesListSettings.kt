package de.uchilab.todo.data

import android.annotation.SuppressLint
import android.content.Context
import de.uchilab.todo.domain.ListSettings
import de.uchilab.todo.domain.WriteError
import de.uchilab.todo.domain.WriteResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The one place that stores a setting: a platform SharedPreferences file with a fixed name, app-private,
 * one `Boolean` (security S7-1 a, S7-3, S7-4). No logging, no value in any message.
 */
class SharedPreferencesListSettings(context: Context) : ListSettings {
    private val appContext = context.applicationContext
    private val shown = MutableStateFlow<Boolean?>(null)

    // Reading and writing happen one after the other, so the order of writes equals the order of reported values.
    private val lock = Mutex()

    override val hideDone: Flow<Boolean> = shown.onSubscription { loadOnce() }.filterNotNull()

    private suspend fun loadOnce() {
        lock.withLock {
            if (shown.value == null) shown.value = withContext(Dispatchers.IO) { readStored() }
        }
    }

    private fun readStored(): Boolean =
        try {
            preferences().getBoolean(KEY_HIDE_DONE, false)
        } catch (_: ClassCastException) {
            // A value of another type is unreadable for us: show the done tasks, as if nothing was stored.
            false
        }

    // The KTX `edit(commit = true)` hides the result of `commit()`; a failed write must become a Failure.
    @SuppressLint("UseKtx")
    override suspend fun setHideDone(hide: Boolean): WriteResult<Unit> =
        lock.withLock {
            val stored = withContext(Dispatchers.IO) { preferences().edit().putBoolean(KEY_HIDE_DONE, hide).commit() }
            if (stored) {
                shown.value = hide
                WriteResult.Success(Unit)
            } else {
                WriteResult.Failure(WriteError.STORAGE_FAILURE)
            }
        }

    private fun preferences() = appContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    companion object {
        const val FILE_NAME = "list_settings"
        const val KEY_HIDE_DONE = "hide_done"
    }
}
