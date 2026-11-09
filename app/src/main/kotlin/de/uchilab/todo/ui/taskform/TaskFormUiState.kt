package de.uchilab.todo.ui.taskform

import de.uchilab.todo.domain.TaskContent
import de.uchilab.todo.domain.TaskId

enum class TaskFormField { TITLE, NOTE }

/**
 * What the input screen shows. Lengths and limits come from [TaskContent], the screen has no counting of its own.
 * [focusTick] is not saved; it makes a repeated request for the same field visible to the screen.
 * [editing] is the id of the task being edited, null for a new task; [original] is its stored content at the time
 * the form was opened (for the "unchanged" comparison). [saving] is only in memory: while true, input is locked.
 */
class TaskFormUiState(
    val open: Boolean = false,
    val title: String = "",
    val note: String = "",
    val titleMissingShown: Boolean = false,
    val saveFailed: Boolean = false,
    val discardDialog: Boolean = false,
    val focus: TaskFormField? = null,
    val focusTick: Int = 0,
    val editing: TaskId? = null,
    val original: TaskContent? = null,
    val saving: Boolean = false,
) {
    val titleLength: Int = TaskContent.titleLength(title)
    val noteLength: Int = TaskContent.noteLength(note)
    val titleTooLong: Boolean get() = titleLength > TaskContent.MAX_TITLE_LENGTH
    val noteTooLong: Boolean get() = noteLength > TaskContent.MAX_NOTE_LENGTH
    val titleCounterVisible: Boolean get() = titleLength >= TITLE_COUNTER_FROM
    val noteCounterVisible: Boolean get() = noteLength >= NOTE_COUNTER_FROM

    /** Without title and note (security L4, privacy V5). */
    override fun toString(): String = "TaskFormUiState(open=$open)"

    companion object {
        /** 80 % of the limits (design 7 and 8). */
        const val TITLE_COUNTER_FROM = 160
        const val NOTE_COUNTER_FROM = 1600
    }
}

/** What is spoken (politely) once after the form closed with a change. */
enum class FormAnnouncement { CREATED, EDITED }

/** What the input screen reports back; implemented by the view model. */
interface TaskFormActions {
    fun onTitleChange(raw: String)
    fun onNoteChange(raw: String)
    fun onFocusChange(field: TaskFormField)
    fun onSave()
    fun onCloseRequest()
    fun onDiscardDismiss()
    fun onDiscardConfirm()
}
