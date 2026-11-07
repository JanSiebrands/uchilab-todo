package de.uchilab.todo.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plain JVM test: screens use theme roles only (design principles), no fixed colours or font sizes. */
class UiThemeRolesTest {

    private val uiDir = File("src/main/kotlin/de/uchilab/todo/ui")
    private val fixedColour = Regex("""\bColor\s*\(|\bColor\.(Red|Green|Blue|Black|White|Gray|Grey|Yellow|Cyan|Magenta)\b|0x[0-9A-Fa-f]{6,8}\b""")
    private val fixedFontSize = Regex("""\d\s*\.\s*sp\b|\bfontSize\s*=""")

    private fun sources(): List<File> = uiDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun `ui sources are found`() {
        assertTrue(sources().isNotEmpty())
    }

    @Test
    fun `ui sources contain no fixed colours or font sizes`() {
        val offenders = sources().flatMap { file ->
            file.readLines().withIndex().filter { (_, line) ->
                fixedColour.containsMatchIn(line) || fixedFontSize.containsMatchIn(line)
            }.map { (index, _) -> "${file.name}:${index + 1}" }
        }
        assertTrue("fixed colours or sizes in: $offenders", offenders.isEmpty())
    }
}
