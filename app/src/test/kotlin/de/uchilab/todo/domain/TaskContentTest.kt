package de.uchilab.todo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskContentTest {

    private val marker = "MARKER-7f3a-Einkauf"
    private val family = "👨‍👩‍👧‍👦" // one family emoji (ZWJ sequence)

    private fun reasons(title: String, note: String?): Set<InvalidReason> =
        (TaskContent.of(title, note) as? Validated.Invalid)?.reasons ?: emptySet()

    private fun isValid(title: String, note: String?) = TaskContent.of(title, note) is Validated.Valid

    @Test
    fun `title with 200 characters is valid and with 201 is too long`() {
        assertTrue(isValid("a".repeat(200), null))
        assertEquals(setOf(InvalidReason.TITLE_TOO_LONG), reasons("a".repeat(201), null))
    }

    @Test
    fun `note with 2000 characters is valid and with 2001 is too long`() {
        assertTrue(isValid("t", "a".repeat(2000)))
        assertEquals(setOf(InvalidReason.NOTE_TOO_LONG), reasons("t", "a".repeat(2001)))
    }

    @Test
    fun `an emoji sequence counts as one character at the limits`() {
        assertTrue(isValid(family.repeat(200), null))
        assertEquals(setOf(InvalidReason.TITLE_TOO_LONG), reasons(family.repeat(201), null))
        assertTrue(isValid("t", family.repeat(2000)))
        assertEquals(setOf(InvalidReason.NOTE_TOO_LONG), reasons("t", family.repeat(2001)))
    }

    @Test
    fun `empty title and title of only spaces are rejected`() {
        assertEquals(setOf(InvalidReason.TITLE_EMPTY), reasons("", null))
        assertEquals(setOf(InvalidReason.TITLE_EMPTY), reasons("   \t ", null))
    }

    @Test
    fun `surrounding spaces do not count towards the limits`() {
        assertTrue(isValid("  " + "a".repeat(200) + "  ", "  " + "b".repeat(2000) + " "))
    }

    @Test
    fun `all applicable reasons are reported together`() {
        assertEquals(
            setOf(InvalidReason.TITLE_EMPTY, InvalidReason.NOTE_TOO_LONG),
            reasons(" ", "a".repeat(2001)),
        )
        assertEquals(
            setOf(InvalidReason.TITLE_TOO_LONG, InvalidReason.NOTE_TOO_LONG),
            reasons("a".repeat(201), "a".repeat(2001)),
        )
    }

    @Test
    fun `a note of only spaces counts as no note`() {
        val content = (TaskContent.of("t", "  \n ") as Validated.Valid).content
        assertNull(content.note)
        assertNull((TaskContent.of("t", null) as Validated.Valid).content.note)
    }

    @Test
    fun `line breaks in the note are allowed and kept`() {
        val content = (TaskContent.of("t", "a\nb") as Validated.Valid).content
        assertEquals("a\nb", content.note)
    }

    @Test
    fun `toString of valid content does not contain title or note`() {
        val content = (TaskContent.of(marker, "$marker-note") as Validated.Valid).content
        assertFalse(content.toString().contains("MARKER"))
        assertFalse(TaskContent.of(marker, null).toString().contains("MARKER"))
    }

    @Test
    fun `toString of a task shows no title or note`() {
        val content = (TaskContent.of(marker, "$marker-note") as Validated.Valid).content
        val task = Task(TaskId(1), content, java.time.Instant.EPOCH, TaskStatus.OPEN)
        assertFalse(task.toString().contains("MARKER"))
    }

    @Test
    fun `a rejected input appears neither in the result nor in its reasons`() {
        val tooLong = marker + "x".repeat(300)
        val result = TaskContent.of(tooLong, marker + "y".repeat(2100)) as Validated.Invalid
        assertFalse(result.toString().contains("MARKER"))
        assertFalse(result.reasons.toString().contains("MARKER"))
        assertFalse(result.reasons.any { it.name.contains("MARKER") })
    }

    private fun valid(title: String, note: String?) = (TaskContent.of(title, note) as Validated.Valid).content

    @Test
    fun `title and note are stored trimmed, inner whitespace stays`() {
        val content = valid("  Milch  und  Brot ", "  Notiz\n\nzwei \n")
        assertEquals("Milch  und  Brot", content.title)
        assertEquals("Notiz\n\nzwei", content.note)
    }

    @Test
    fun `every kind of line break in the title becomes exactly one space`() {
        val breaks = listOf("\r\n", "\n", "\r", "\u0085", "\u2028", "\u2029")
        for (lineBreak in breaks) {
            assertEquals("Brot Butter", valid("Brot${lineBreak}Butter", null).title)
        }
        assertEquals("Brot  Butter", valid("Brot \nButter", null).title) // spaces are not merged
        assertEquals("Milch", valid("Milch\n", null).title)
    }

    @Test
    fun `consecutive line breaks each become a space`() {
        assertEquals("a  b", valid("a\n\nb", null).title)
        assertEquals("a  b", valid("a\r\n\r\nb", null).title)
    }

    @Test
    fun `a title of only line breaks is empty`() {
        assertEquals(setOf(InvalidReason.TITLE_EMPTY), reasons("\n\r\n\u2028", null))
    }

    @Test
    fun `line breaks are replaced before the length check`() {
        assertTrue(isValid("a".repeat(200) + "\n", null))
        assertEquals(setOf(InvalidReason.TITLE_TOO_LONG), reasons("a".repeat(100) + "\n" + "a".repeat(100), null))
    }

    @Test
    fun `the note keeps its line breaks`() {
        assertEquals("a\r\nb\u2028c", valid("t", "a\r\nb\u2028c").note)
    }
}
