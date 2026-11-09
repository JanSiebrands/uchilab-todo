package de.uchilab.todo.ui.tasklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Open tasks first, then done tasks; the order inside each group is kept (newest first as delivered). */
internal fun List<Task>.groupByStatus(): TaskListUiState.Loaded {
    val (done, open) = partition { it.status == TaskStatus.DONE }
    return TaskListUiState.Loaded(open, done)
}

/** A [TaskReadException] becomes [TaskListUiState.ReadFailed]; every other exception, also cancellation, passes through. */
internal fun Flow<List<Task>>.toUiState(): Flow<TaskListUiState> =
    map<List<Task>, TaskListUiState> { it.groupByStatus() }
        .catch { if (it is TaskReadException) emit(TaskListUiState.ReadFailed) else throw it }

/** A saved status change, for the spoken announcement. Carries no title (security L4). */
data class StatusChange(val id: TaskId, val status: TaskStatus)

class TaskListViewModel(private val repository: TaskRepository) : ViewModel() {
    // The shown status always comes from the store; there is no local copy that could differ from it.
    val state: StateFlow<TaskListUiState> = repository.observeTasks().toUiState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskListUiState.Loading)

    private val _statusSaveFailed = MutableStateFlow(false)

    /** True until the failure notice was shown. */
    val statusSaveFailed: StateFlow<Boolean> = _statusSaveFailed.asStateFlow()

    private val _statusAnnouncement = MutableStateFlow<StatusChange?>(null)

    /** The last saved change; the screen turns it into a spoken text. */
    val statusAnnouncement: StateFlow<StatusChange?> = _statusAnnouncement.asStateFlow()

    /** Writes the target [status]; the list follows from the store. */
    fun onStatusChange(id: TaskId, status: TaskStatus) {
        viewModelScope.launch {
            when (repository.setStatus(id, status)) {
                is WriteResult.Success -> _statusAnnouncement.value = StatusChange(id, status)
                is WriteResult.Failure -> _statusSaveFailed.value = true
            }
        }
    }

    fun onStatusSaveFailedShown() {
        _statusSaveFailed.value = false
    }

    companion object {
        fun factory(repository: TaskRepository) = viewModelFactory { initializer { TaskListViewModel(repository) } }
    }
}
