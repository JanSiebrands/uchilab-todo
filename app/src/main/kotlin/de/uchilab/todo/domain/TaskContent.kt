package de.uchilab.todo.domain

import java.text.BreakIterator

/** Why a title or note was rejected. A fixed type, never text containing the input (security L3). */
enum class InvalidReason { TITLE_EMPTY, TITLE_TOO_LONG, NOTE_TOO_LONG }

sealed interface Validated {
    class Valid(val content: TaskContent) : Validated {
        override fun toString(): String = "Valid"
    }

    class Invalid(val reasons: Set<InvalidReason>) : Validated {
        override fun toString(): String = "Invalid(reasons=$reasons)"
    }
}

/**
 * Title and note of a task. The only way to a valid instance is [of], so the repository
 * can never store values outside the limits (security D5).
 */
class TaskContent private constructor(val title: String, val note: String?) {
    override fun equals(other: Any?): Boolean = other is TaskContent && title == other.title && note == other.note

    override fun hashCode(): Int = title.hashCode() * 31 + (note?.hashCode() ?: 0)

    override fun toString(): String = "TaskContent"

    companion object {
        const val MAX_TITLE_LENGTH = 200
        const val MAX_NOTE_LENGTH = 2000

        // \r\n counts as one line break; then \n, \r, NEL (U+0085), LINE SEPARATOR, PARAGRAPH SEPARATOR.
        private val LINE_BREAK = Regex("\r\n|[\n\r\u0085\u2028\u2029]")

        /**
         * Title: each line break becomes one space, then trim, then check the length.
         * Note: trimmed, keeps its line breaks, empty after trimming means no note.
         * The trimmed values are what gets stored.
         */
        fun of(title: String, note: String?): Validated {
            val cleanTitle = title.replace(LINE_BREAK, " ").trim()
            val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }
            val reasons = buildSet {
                if (cleanTitle.isEmpty()) add(InvalidReason.TITLE_EMPTY)
                if (graphemeCount(cleanTitle) > MAX_TITLE_LENGTH) add(InvalidReason.TITLE_TOO_LONG)
                if (cleanNote != null && graphemeCount(cleanNote) > MAX_NOTE_LENGTH) add(InvalidReason.NOTE_TOO_LONG)
            }
            if (reasons.isNotEmpty()) return Validated.Invalid(reasons)
            return Validated.Valid(TaskContent(cleanTitle, cleanNote))
        }

        /** Counts what a person sees as one character (extended grapheme clusters). */
        private fun graphemeCount(text: String): Int {
            val iterator = BreakIterator.getCharacterInstance()
            iterator.setText(text)
            var count = 0
            while (iterator.next() != BreakIterator.DONE) count++
            return count
        }
    }
}
