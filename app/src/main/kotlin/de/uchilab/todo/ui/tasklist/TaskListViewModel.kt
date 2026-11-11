package de.uchilab.todo.ui.tasklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.uchilab.todo.domain.ListSettings
import de.uchilab.todo.domain.Task
import de.uchilab.todo.domain.TaskId
import de.uchilab.todo.domain.TaskReadException
import de.uchilab.todo.domain.TaskRepository
import de.uchilab.todo.domain.TaskStatus
import de.uchilab.todo.domain.WriteResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Open tasks first, then done tasks; the order inside each group is kept (newest first as delivered). */
internal fun List<Task>.groupByStatus(hideDone: Boolean = false): TaskListUiState.Loaded {
    val (done, open) = partition { it.status == TaskStatus.DONE }
    return TaskListUiState.Loaded(open, done, hideDone)
}

/** A [TaskReadException] becomes [TaskListUiState.ReadFailed]; every other exception, also cancellation, passes through. */
internal fun Flow<List<Task>>.toUiState(hideDone: Boolean): Flow<TaskListUiState> =
    map<List<Task>, TaskListUiState> { it.groupByStatus(hideDone) }
        .catch { if (it is TaskReadException) emit(TaskListUiState.ReadFailed) else throw it }

/** What the screen says politely after a saved change. Carries no title (security L4); the screen looks it up in the list. */
sealed interface ListAnnouncement {
    data class StatusChanged(val id: TaskId, val status: TaskStatus) : ListAnnouncement

    /** A deleted task was brought back by undo. */
    data class Restored(val id: TaskId) : ListAnnouncement
}

/**
 * Where a deleted [task] stood: the entry of its group that moved up into its place, else the last of the group,
 * else `null`. Done tasks that are hidden have no entry. The focus goes there when the undo offer ends (design B2).
 */
internal fun TaskListUiState.Loaded.entryAtPlaceOf(task: Task): TaskId? {
    val isDone = task.status == TaskStatus.DONE
    if (isDone && hideDone) return null
    val group = if (isDone) done else open
    return (group.firstOrNull { it.id.value < task.id.value } ?: group.lastOrNull())?.id
}

/**
 * A hint for the user that the screen shows once. Carries no title and no value in its text form (security L4).
 * [Deleted] and [RestoreFailed] hold the only copy of a deleted task (privacy V4b): in memory of this view model,
 * never in a saved state. When the hint ends or a newer one replaces it, the copy is gone.
 */
sealed interface ListNotice {
    data object StatusSaveFailed : ListNotice

    data object SettingSaveFailed : ListNotice

    /** A task was checked off while done tasks are hidden; the screen looks the title up in the list. */
    class DoneHidden(val id: TaskId) : ListNotice {
        override fun toString(): String = "DoneHidden"
    }

    /** A task was deleted; the offer to undo it. */
    class Deleted(val task: Task) : ListNotice {
        override fun toString(): String = "Deleted"
    }

    /** Undo failed; the offer to try again keeps the copy. */
    class RestoreFailed(val task: Task) : ListNotice {
        override fun toString(): String = "RestoreFailed"
    }
}

class TaskListViewModel(
    private val repository: TaskRepository,
    private val listSettings: ListSettings,
) : ViewModel() {
    // The shown status and the shown setting always come from the stores; there is no local copy that could differ.
    // Loading until the setting has arrived and the tasks have been read, so done tasks never flash up at the start.
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TaskListUiState> = listSettings.hideDone
        .flatMapLatest { hide -> repository.observeTasks().toUiState(hide) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskListUiState.Loading)

    private val _notice = MutableStateFlow<ListNotice?>(null)

    /** The hint to show now; a newer one replaces the older one at once. */
    val notice: StateFlow<ListNotice?> = _notice.asStateFlow()

    private val _announcement = MutableStateFlow<ListAnnouncement?>(null)

    /** The last saved change; the screen turns it into a spoken text. */
    val announcement: StateFlow<ListAnnouncement?> = _announcement.asStateFlow()

    /** Writes the target [status]; the list follows from the store. */
    fun onStatusChange(id: TaskId, status: TaskStatus) {
        viewModelScope.launch {
            when (repository.setStatus(id, status)) {
                is WriteResult.Success ->
                    if (status == TaskStatus.DONE && listSettings.hideDone.first()) {
                        // The notice replaces the spoken announcement.
                        _notice.value = ListNotice.DoneHidden(id)
                    } else {
                        _announcement.value = ListAnnouncement.StatusChanged(id, status)
                    }
                is WriteResult.Failure -> _notice.value = ListNotice.StatusSaveFailed
            }
        }
    }

    /** Writes the target value [hide]; the list follows from the store. */
    fun onHideDoneChange(hide: Boolean) {
        viewModelScope.launch {
            if (listSettings.setHideDone(hide) is WriteResult.Failure) _notice.value = ListNotice.SettingSaveFailed
        }
    }

    /** The form deleted [task] from the store; the offer to undo replaces every older hint and its copy. */
    fun onTaskDeleted(task: Task) {
        _notice.value = ListNotice.Deleted(task)
    }

    /**
     * Puts the task of [offer] back. Only the offer that is on screen counts: taking it out first makes a second tap,
     * an ended or a replaced offer do nothing.
     */
    fun onUndo(offer: ListNotice) {
        val task = when (offer) {
            is ListNotice.Deleted -> offer.task
            is ListNotice.RestoreFailed -> offer.task
            else -> return
        }
        if (!_notice.compareAndSet(offer, null)) return
        viewModelScope.launch {
            when (repository.restore(task)) {
                is WriteResult.Success -> _announcement.value = ListAnnouncement.Restored(task.id)
                is WriteResult.Failure -> _notice.value = ListNotice.RestoreFailed(task)
            }
        }
    }

    /** Clears [shown] unless a newer hint has replaced it meanwhile. */
    fun onNoticeShown(shown: ListNotice) {
        _notice.compareAndSet(shown, null)
    }

    companion object {
        fun factory(repository: TaskRepository, listSettings: ListSettings) =
            viewModelFactory { initializer { TaskListViewModel(repository, listSettings) } }
    }
}
