package de.uchilab.todo.domain

import kotlinx.coroutines.flow.Flow

/** Settings of the task list. Holds no task content; a single truth value so far (security S7-3). */
interface ListSettings {
    /**
     * Whether done tasks are hidden. The first emission is the stored value, `false` if nothing is stored or
     * the stored value cannot be read. A new value is reported only after it was stored successfully.
     */
    val hideDone: Flow<Boolean>

    /** Stores the target value. On [WriteResult.Failure] the reported value stays as it was. */
    suspend fun setHideDone(hide: Boolean): WriteResult<Unit>
}
