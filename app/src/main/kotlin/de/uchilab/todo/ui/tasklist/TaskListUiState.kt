package de.uchilab.todo.ui.tasklist

import de.uchilab.todo.domain.Task

sealed interface TaskListUiState {
    /** Nothing loaded yet; shows no empty state. */
    data object Loading : TaskListUiState

    /** Loaded successfully; an empty list is the empty state. */
    class Loaded(val tasks: List<Task>) : TaskListUiState {
        override fun toString(): String = "Loaded(size=${tasks.size})"
    }

    /** Reading failed; replaces list and empty state. */
    data object ReadFailed : TaskListUiState
}
