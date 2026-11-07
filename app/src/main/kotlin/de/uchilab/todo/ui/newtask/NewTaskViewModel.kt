package de.uchilab.todo.ui.newtask

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
class NewTaskViewModel(
    private val repository: TaskRepository,
    private val savedState: SavedStateHandle,
) : ViewModel(), NewTaskActions {
    var state: NewTaskUiState by mutableStateOf(restore())
        private set

    /** Id of the task just created, until the list has scrolled to it. */
    var createdTaskId: TaskId? by mutableStateOf(null)
        private set

    /** True after a task was created, until the next input is opened (spoken once by the live region). */
    var createdAnnouncement: Boolean by mutableStateOf(false)
        private set

    /** Only in memory: a save is running. Set on the main thread before the coroutine starts. */
    private var saving = false

    fun open() {
        createdAnnouncement = false
        update(NewTaskUiState(open = true, focus = NewTaskField.TITLE, focusTick = state.focusTick + 1))
    }

    fun onCreatedHandled() {
        createdTaskId = null
    }

    override fun onTitleChange(raw: String) {
        val title = cutAtProtectionLimit(TaskContent.singleLine(raw), MAX_TITLE_CHARS)
        update(copy(title = title, titleMissingShown = state.titleMissingShown && title.isBlank()))
    }

    override fun onNoteChange(raw: String) {
        update(copy(note = cutAtProtectionLimit(raw, MAX_NOTE_CHARS)))
    }

    override fun onFocusChange(field: NewTaskField) {
        if (state.focus != field) update(copy(focus = field))
    }

    override fun onSave() {
        if (saving || !state.open) return
        val validated = TaskContent.of(state.title, state.note)
        if (validated is Validated.Invalid) {
            val reasons = validated.reasons
            val field = if (InvalidReason.TITLE_EMPTY in reasons || InvalidReason.TITLE_TOO_LONG in reasons) {
                NewTaskField.TITLE
            } else {
                NewTaskField.NOTE
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
        saving = true
        update(copy(saveFailed = false))
        viewModelScope.launch {
            try {
                when (val result = repository.add(content)) {
                    is WriteResult.Success -> {
                        createdTaskId = result.value.id
                        createdAnnouncement = true
                        update(NewTaskUiState(focusTick = state.focusTick))
                    }
                    is WriteResult.Failure -> update(copy(saveFailed = true))
                }
            } finally {
                saving = false
            }
        }
    }

    override fun onCloseRequest() {
        if (saving || !state.open) return
        // Back while the question is open (the dialog normally takes it itself) only closes the question.
        if (state.discardDialog) return onDiscardDismiss()
        if (state.title.isBlank() && state.note.isBlank()) close() else update(copy(discardDialog = true))
    }

    override fun onDiscardDismiss() {
        if (state.discardDialog) update(copy(discardDialog = false))
    }

    override fun onDiscardConfirm() {
        if (saving || !state.open) return
        close()
    }

    private fun close() = update(NewTaskUiState(focusTick = state.focusTick))

    private fun copy(
        title: String = state.title,
        note: String = state.note,
        titleMissingShown: Boolean = state.titleMissingShown,
        saveFailed: Boolean = state.saveFailed,
        discardDialog: Boolean = state.discardDialog,
        focus: NewTaskField? = state.focus,
        focusTick: Int = state.focusTick,
    ) = NewTaskUiState(state.open, title, note, titleMissingShown, saveFailed, discardDialog, focus, focusTick)

    /** Takes over the new state and mirrors it into the saved state; a closed input leaves no key behind. */
    private fun update(new: NewTaskUiState) {
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
        if (new.focus == null) savedState.remove<Any>(KEY_FOCUS) else savedState[KEY_FOCUS] = new.focus.name
    }

    private fun restore(): NewTaskUiState {
        if (savedState.get<Boolean>(KEY_OPEN) != true) return NewTaskUiState()
        return NewTaskUiState(
            open = true,
            title = savedState.get<String>(KEY_TITLE) ?: "",
            note = savedState.get<String>(KEY_NOTE) ?: "",
            titleMissingShown = savedState.get<Boolean>(KEY_TITLE_MISSING) ?: false,
            saveFailed = savedState.get<Boolean>(KEY_SAVE_FAILED) ?: false,
            discardDialog = savedState.get<Boolean>(KEY_DISCARD_DIALOG) ?: false,
            focus = savedState.get<String>(KEY_FOCUS)?.let { name -> NewTaskField.entries.firstOrNull { it.name == name } },
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
        private val KEYS = listOf(
            KEY_OPEN, KEY_TITLE, KEY_NOTE, KEY_TITLE_MISSING, KEY_SAVE_FAILED, KEY_DISCARD_DIALOG, KEY_FOCUS,
        )

        /** Cuts at [max] UTF-16 characters without leaving half of a surrogate pair. */
        internal fun cutAtProtectionLimit(text: String, max: Int): String {
            if (text.length <= max) return text
            val end = if (text[max - 1].isHighSurrogate()) max - 1 else max
            return text.substring(0, end)
        }

        fun factory(repository: TaskRepository) =
            viewModelFactory { initializer { NewTaskViewModel(repository, createSavedStateHandle()) } }
    }
}
