package de.uchilab.todo.ui.tasklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.uchilab.todo.domain.Task
import de.uchilab.todo.domain.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal fun Flow<List<Task>>.toUiState(): Flow<TaskListUiState> = map { TaskListUiState.Loaded(it) }

class TaskListViewModel(repository: TaskRepository) : ViewModel() {
    val state: StateFlow<TaskListUiState> = repository.observeTasks().toUiState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskListUiState.Loading)

    companion object {
        fun factory(repository: TaskRepository) = viewModelFactory { initializer { TaskListViewModel(repository) } }
    }
}
