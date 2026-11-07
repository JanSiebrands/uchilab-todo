package de.uchilab.todo.ui.newtask

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.uchilab.todo.domain.TaskId
import de.uchilab.todo.domain.TaskRepository

class NewTaskViewModel(
    private val repository: TaskRepository,
    private val savedState: SavedStateHandle,
) : ViewModel(), NewTaskActions {
    var state: NewTaskUiState by mutableStateOf(NewTaskUiState())
        private set

    /** Id of the task just created, until the list has scrolled to it. */
    var createdTaskId: TaskId? by mutableStateOf(null)
        private set

    /** True after a task was created, until the next input is opened (spoken once by the live region). */
    var createdAnnouncement: Boolean by mutableStateOf(false)
        private set

    fun open() = Unit
    fun onCreatedHandled() = Unit
    override fun onTitleChange(raw: String) = Unit
    override fun onNoteChange(raw: String) = Unit
    override fun onFocusChange(field: NewTaskField) = Unit
    override fun onSave() = Unit
    override fun onCloseRequest() = Unit
    override fun onDiscardDismiss() = Unit
    override fun onDiscardConfirm() = Unit

    companion object {
        fun factory(repository: TaskRepository) =
            viewModelFactory { initializer { NewTaskViewModel(repository, createSavedStateHandle()) } }
    }
}
