package de.uchilab.checks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Unit test of the emulator list rule ui-titles-shown.py (used by emulator-tasks.sh, UCH-147). */
class UiTitlesShownTest {
    @get:Rule
    val tmp = TemporaryFolder()

    // Gradle runs the tests in the project directory tools/checks.
    private val rule = File("ui-titles-shown.py").absoluteFile

    private fun node(text: String = "", desc: String = "") = """<node text="$text" content-desc="$desc" />"""

    /** Runs the rule on a dump with these nodes and the expected titles; returns exit code and output. */
    private fun run(nodes: List<String>, titles: List<String>): Pair<Int, String> {
        val dump = tmp.newFile().apply { writeText("<hierarchy>${nodes.joinToString("")}</hierarchy>") }
        val expected = tmp.newFile().apply { writeText(titles.joinToString("\n", postfix = "\n")) }
        val process = ProcessBuilder("python3", "-I", rule.path, dump.path, expected.path).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        return process.waitFor() to output
    }

    private val titles = listOf("Alpha", "Beta", "Gamma")

    @Test
    fun `all titles shown is green, in any order`() {
        assertEquals(0, run(listOf(node("Gamma"), node("Alpha"), node("Beta")), titles).first)
    }

    @Test
    fun `a title only in content-desc counts as shown`() {
        assertEquals(0, run(listOf(node("Alpha"), node(desc = "Beta, done"), node("Gamma")), titles).first)
    }

    @Test
    fun `one title missing that is not the first is red`() {
        val (exit, output) = run(listOf(node("Alpha"), node("Beta")), titles)
        assertEquals(1, exit)
        assertTrue(output, output.contains("missing: Gamma"))
    }

    @Test
    fun `no title shown is red`() {
        val (exit, output) = run(listOf(node("Other")), titles)
        assertEquals(1, exit)
        assertTrue(output, output.contains("missing: Alpha, Beta, Gamma"))
    }
}
