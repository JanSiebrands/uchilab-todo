package de.uchilab.todo.domain

import kotlinx.coroutines.flow.Flow

enum class WriteError { NOT_FOUND, STORAGE_FAILURE }

/** Result of a write. Carries no exception and no message text (security L3). */
sealed interface WriteResult<out T> {
    class Success<T>(val value: T) : WriteResult<T> {
        override fun toString(): String = "Success"
    }

    class Failure(val error: WriteError) : WriteResult<Nothing> {
        override fun toString(): String = "Failure(error=$error)"
    }
}

interface TaskRepository {
    /**
     * Newest task first. The first emission is the loaded state, later ones follow every change.
     * If reading fails (storage error or damaged stored values) the flow ends with a [TaskReadException],
     * never with an exception of the storage layer.
     */
    fun observeTasks(): Flow<List<Task>>

    /** Stores a new open task with the current time. */
    suspend fun add(content: TaskContent): WriteResult<Task>

    suspend fun updateContent(id: TaskId, content: TaskContent): WriteResult<Unit>

    suspend fun setStatus(id: TaskId, status: TaskStatus): WriteResult<Unit>

    /** Removes the row physically (privacy V4). */
    suspend fun delete(id: TaskId): WriteResult<Unit>

    /**
     * Inserts [task] again with its old id, status and creation time (undo after [delete]). If the id exists
     * already, nothing is written and the result is a storage failure, so there is never a duplicate.
     */
    suspend fun restore(task: Task): WriteResult<Unit>
}
