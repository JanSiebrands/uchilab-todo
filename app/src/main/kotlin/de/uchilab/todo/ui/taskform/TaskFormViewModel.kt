package de.uchilab.todo.ui.taskform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.uchilab.todo.domain.Task
import de.uchilab.todo.domain.TaskContent
import de.uchilab.todo.domain.TaskId
import de.uchilab.todo.domain.TaskRepository
import de.uchilab.todo.domain.Validated
import de.uchilab.todo.domain.InvalidReason
import de.uchilab.todo.domain.WriteResult
import kotlinx.coroutines.launch

/**
 * State of the input screen. Everything the user must not lose lives in the [SavedStateHandle] (survives rotation and
 * the end of the process in the background); [state] is written synchronously with every change so the cursor
 * does not jump. The keys are removed when the input closes. Neither the state nor any log shows title or note.
 */
class TaskFormViewModel(
    private val repository: TaskRepository,
    private val savedState: SavedStateHandle,
) : ViewModel(), TaskFormActions {
    var state: TaskFormUiState by mutableStateOf(restore())
        private set

    /** Id of the task just created, until the list has scrolled to it. */
    var createdTaskId: TaskId? by mutableStateOf(null)
        private set

    /** Id of the task just edited, until the list has moved the focus to it. */
    var editedTaskId: TaskId? by mutableStateOf(null)
        private set

    /** Set after a task was created or edited, until the next input is opened (spoken once by the live region). */
    var announcement: FormAnnouncement? by mutableStateOf(null)
        private set

    fun open() {
        announcement = null
        update(TaskFormUiState(open = true, focus = TaskFormField.TITLE, focusTick = state.focusTick + 1))
    }

    /** Opens the form with the stored title and note of [task]; no field has the focus, status is not touched. */
    fun openEdit(task: Task) {
        announcement = null
        update(
            TaskFormUiState(
                open = true,
                title = task.content.title,
                note = task.content.note ?: "",
                focusTick = state.focusTick,
                editing = task.id,
                original = task.content,
            ),
        )
    }

    fun onCreatedHandled() {
        createdTaskId = null
    }

    fun onEditedHandled() {
        editedTaskId = null
    }

    override fun onTitleChange(raw: String) {
        if (state.saving) return
        val title = cutAtProtectionLimit(TaskContent.singleLine(raw), MAX_TITLE_CHARS)
        update(copy(title = title, titleMissingShown = state.titleMissingShown && title.isBlank()))
    }

    override fun onNoteChange(raw: String) {
        if (state.saving) return
        update(copy(note = cutAtProtectionLimit(raw, MAX_NOTE_CHARS)))
    }

    override fun onFocusChange(field: TaskFormField) {
        if (state.focus != field) update(copy(focus = field))
    }

    override fun onSave() {
        if (state.saving || !state.open) return
        val validated = TaskContent.of(state.title, state.note)
        if (validated is Validated.Invalid) {
            val reasons = validated.reasons
            val field = if (InvalidReason.TITLE_EMPTY in reasons || InvalidReason.TITLE_TOO_LONG in reasons) {
                TaskFormField.TITLE
            } else {
                TaskFormField.NOTE
            }
            update(
                copy(
                    titleMissingShown = InvalidReason.TITLE_EMPTY in reasons,
                    saveFailed = false,
                    focus = field,
                    focusTick = state.focusTick + 1,
                ),
            )
            return
        }
        val content = (validated as Validated.Valid).content
        val editing = state.editing
        if (editing != null && content == state.original) return close()
        update(copy(saveFailed = false, saving = true))
        viewModelScope.launch {
            try {
                if (editing == null) saveNew(content) else saveEdit(editing, content)
            } finally {
                if (state.saving) update(copy(saving = false))
            }
        }
    }

    private suspend fun saveNew(content: TaskContent) {
        when (val result = repository.add(content)) {
            is WriteResult.Success -> {
                createdTaskId = result.value.id
                announcement = FormAnnouncement.CREATED
                close()
            }
            is WriteResult.Failure -> update(copy(saveFailed = true, saving = false))
        }
    }

    private suspend fun saveEdit(id: TaskId, content: TaskContent) {
        when (repository.updateContent(id, content)) {
            is WriteResult.Success -> {
                editedTaskId = id
                announcement = FormAnnouncement.EDITED
                close()
            }
            is WriteResult.Failure -> update(copy(saveFailed = true, saving = false))
        }
    }

    override fun onCloseRequest() {
        if (state.saving || !state.open) return
        // Back while the question is open (the dialog normally takes it itself) only closes the question.
        if (state.discardDialog) return onDiscardDismiss()
        if (isUnchanged()) close() else update(copy(discardDialog = true))
    }

    override fun onDiscardDismiss() {
        if (state.discardDialog) update(copy(discardDialog = false))
    }

    override fun onDiscardConfirm() {
        if (state.saving || !state.open) return
        close()
    }

    /** New task: both fields blank. Edit: the cleaned input equals the stored version (same cleaning as saving). */
    private fun isUnchanged(): Boolean {
        val original = state.original ?: return state.title.isBlank() && state.note.isBlank()
        return TaskContent.cleanTitle(state.title) == original.title && TaskContent.cleanNote(state.note) == original.note
    }

    private fun close() = update(TaskFormUiState(focusTick = state.focusTick))

    private fun copy(
        title: String = state.title,
        note: String = state.note,
        titleMissingShown: Boolean = state.titleMissingShown,
        saveFailed: Boolean = state.saveFailed,
        discardDialog: Boolean = state.discardDialog,
        focus: TaskFormField? = state.focus,
        focusTick: Int = state.focusTick,
        saving: Boolean = state.saving,
    ) = TaskFormUiState(
        state.open, title, note, titleMissingShown, saveFailed, discardDialog, focus, focusTick,
        state.editing, state.original, saving,
    )

    /** Takes over the new state and mirrors it into the saved state; a closed input leaves no key behind. */
    private fun update(new: TaskFormUiState) {
        state = new
        if (!new.open) {
            for (key in KEYS) savedState.remove<Any>(key)
            return
        }
        savedState[KEY_OPEN] = true
        savedState[KEY_TITLE] = new.title
        savedState[KEY_NOTE] = new.note
        savedState[KEY_TITLE_MISSING] = new.titleMissingShown
        savedState[KEY_SAVE_FAILED] = new.saveFailed
        savedState[KEY_DISCARD_DIALOG] = new.discardDialog
        val editing = new.editing
        val original = new.original
        if (editing == null || original == null) {
            savedState.remove<Any>(KEY_EDITING)
            savedState.remove<Any>(KEY_ORIGINAL_TITLE)
            savedState.remove<Any>(KEY_ORIGINAL_NOTE)
        } else {
            savedState[KEY_EDITING] = editing.value
            savedState[KEY_ORIGINAL_TITLE] = original.title
            if (original.note == null) savedState.remove<Any>(KEY_ORIGINAL_NOTE) else savedState[KEY_ORIGINAL_NOTE] = original.note
        }
        if (new.focus == null) savedState.remove<Any>(KEY_FOCUS) else savedState[KEY_FOCUS] = new.focus.name
    }

    private fun restore(): TaskFormUiState {
        if (savedState.get<Boolean>(KEY_OPEN) != true) return TaskFormUiState()
        val editingId = savedState.get<Long>(KEY_EDITING)
        val originalTitle = savedState.get<String>(KEY_ORIGINAL_TITLE)
        val original = if (editingId != null && originalTitle != null) {
            (TaskContent.of(originalTitle, savedState.get<String>(KEY_ORIGINAL_NOTE)) as? Validated.Valid)?.content
        } else {
            null
        }
        return TaskFormUiState(
            open = true,
            title = savedState.get<String>(KEY_TITLE) ?: "",
            note = savedState.get<String>(KEY_NOTE) ?: "",
            titleMissingShown = savedState.get<Boolean>(KEY_TITLE_MISSING) ?: false,
            saveFailed = savedState.get<Boolean>(KEY_SAVE_FAILED) ?: false,
            discardDialog = savedState.get<Boolean>(KEY_DISCARD_DIALOG) ?: false,
            focus = savedState.get<String>(KEY_FOCUS)?.let { name -> TaskFormField.entries.firstOrNull { it.name == name } },
            editing = if (original != null) editingId?.let(::TaskId) else null,
            original = original,
        )
    }

    companion object {
        /**
         * Protection limit in UTF-16 characters (plan 2.3): the text sits in the saved state of the activity,
         * a paste of hundreds of KB would crash the app on the next app switch. Far above the limit the hint
         * "too long" is shown anyway, so nothing is lost unnoticed.
         *
         * Twenty times the real limits, not ten as in the plan: 200 emoji family sequences are a valid title
         * (200 characters as the person sees them) but take 11 UTF-16 characters each, i.e. 2 200 of them,
         * and must stay enterable at the limit.
         */
        const val MAX_TITLE_CHARS = 4_000
        const val MAX_NOTE_CHARS = 40_000

        private const val KEY_OPEN = "open"
        private const val KEY_TITLE = "title"
        private const val KEY_NOTE = "note"
        private const val KEY_TITLE_MISSING = "titleMissingShown"
        private const val KEY_SAVE_FAILED = "saveFailed"
        private const val KEY_DISCARD_DIALOG = "discardDialog"
        private const val KEY_FOCUS = "focus"
        private const val KEY_EDITING = "editingId"
        private const val KEY_ORIGINAL_TITLE = "originalTitle"
        private const val KEY_ORIGINAL_NOTE = "originalNote"
        private val KEYS = listOf(
            KEY_OPEN, KEY_TITLE, KEY_NOTE, KEY_TITLE_MISSING, KEY_SAVE_FAILED, KEY_DISCARD_DIALOG, KEY_FOCUS,
            KEY_EDITING, KEY_ORIGINAL_TITLE, KEY_ORIGINAL_NOTE,
        )

        /** Cuts at [max] UTF-16 characters without leaving half of a surrogate pair. */
        internal fun cutAtProtectionLimit(text: String, max: Int): String {
            if (text.length <= max) return text
            val end = if (text[max - 1].isHighSurrogate()) max - 1 else max
            return text.substring(0, end)
        }

        fun factory(repository: TaskRepository) =
            viewModelFactory { initializer { TaskFormViewModel(repository, createSavedStateHandle()) } }
    }
}
