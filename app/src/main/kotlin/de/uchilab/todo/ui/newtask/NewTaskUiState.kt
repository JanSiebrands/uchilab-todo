package de.uchilab.todo.ui.newtask

import de.uchilab.todo.domain.TaskContent

enum class NewTaskField { TITLE, NOTE }

/**
 * What the input screen shows. Lengths and limits come from [TaskContent], the screen has no counting of its own.
 * [focusTick] is not saved; it makes a repeated request for the same field visible to the screen.
 */
class NewTaskUiState(
    val open: Boolean = false,
    val title: String = "",
    val note: String = "",
    val titleMissingShown: Boolean = false,
    val saveFailed: Boolean = false,
    val discardDialog: Boolean = false,
    val focus: NewTaskField? = null,
    val focusTick: Int = 0,
) {
    val titleLength: Int = TaskContent.titleLength(title)
    val noteLength: Int = TaskContent.noteLength(note)
    val titleTooLong: Boolean get() = titleLength > TaskContent.MAX_TITLE_LENGTH
    val noteTooLong: Boolean get() = noteLength > TaskContent.MAX_NOTE_LENGTH
    val titleCounterVisible: Boolean get() = titleLength >= TITLE_COUNTER_FROM
    val noteCounterVisible: Boolean get() = noteLength >= NOTE_COUNTER_FROM

    /** Without title and note (security L4, privacy V5). */
    override fun toString(): String = "NewTaskUiState(open=$open)"

    companion object {
        /** 80 % of the limits (design 7 and 8). */
        const val TITLE_COUNTER_FROM = 160
        const val NOTE_COUNTER_FROM = 1600
    }
}

/** What the input screen reports back; implemented by the view model. */
interface NewTaskActions {
    fun onTitleChange(raw: String)
    fun onNoteChange(raw: String)
    fun onFocusChange(field: NewTaskField)
    fun onSave()
    fun onCloseRequest()
    fun onDiscardDismiss()
    fun onDiscardConfirm()
}
