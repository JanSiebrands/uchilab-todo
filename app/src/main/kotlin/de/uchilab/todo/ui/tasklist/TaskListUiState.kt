package de.uchilab.todo.ui.tasklist

import de.uchilab.todo.domain.Task

sealed interface TaskListUiState {
    /** Nothing loaded yet; shows no empty state. */
    data object Loading : TaskListUiState

    /** Loaded successfully; both groups empty is the empty state. Each group is newest first. */
    class Loaded(val open: List<Task>, val done: List<Task>) : TaskListUiState {
        override fun toString(): String = "Loaded(open=${open.size}, done=${done.size})"
    }

    /** Reading failed; replaces list and empty state. */
    data object ReadFailed : TaskListUiState
}
