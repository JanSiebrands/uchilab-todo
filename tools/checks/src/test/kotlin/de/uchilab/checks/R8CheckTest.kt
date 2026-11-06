package de.uchilab.checks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class R8CheckTest {
    private val config = "-keep class de.uchilab.todo.ui.MainActivity\n-allowaccessmodification\n"
    private val mapping = "# compiler: R8\nde.uchilab.todo.ui.Helper -> a.b:\n    void run() -> a\n"
    private val usage = "de.uchilab.todo.Unused\n"

    @Test
    fun `active R8 has no violations`() {
        assertEquals(emptyList<String>(), R8Check.check(true, config, mapping, usage))
    }

    @Test
    fun `missing minify task is a violation`() {
        assertTrue(R8Check.check(false, config, mapping, usage).single().contains("isMinifyEnabled"))
    }

    @Test
    fun `disabling flags are violations, also indented or with comment`() {
        val violations = R8Check.check(true, "$config  -dontobfuscate # x\n-dontshrink\n-dontoptimize\n", mapping, usage)
        assertEquals(3, violations.size)
    }

    @Test
    fun `commented out flag is not a violation`() {
        assertEquals(emptyList<String>(), R8Check.check(true, "$config# -dontobfuscate\n", mapping, usage))
    }

    @Test
    fun `mapping without renamed class is a violation`() {
        val identity = "de.uchilab.todo.ui.Helper -> de.uchilab.todo.ui.Helper:\n    void run() -> a\n"
        assertTrue(R8Check.check(true, config, identity, usage).single().contains("rename"))
    }

    @Test
    fun `renamed synthetic classes only (keep-all rule) is a violation`() {
        val keepAll = "a.B -> a.B:\na.B\$\$ExternalSyntheticLambda0 -> c:\nx.Y -> R8\$\$REMOVED\$\$CLASS\$\$0:\n"
        assertTrue(R8Check.check(true, config, keepAll, usage).single().contains("rename"))
    }

    @Test
    fun `empty usage is a violation`() {
        assertTrue(R8Check.check(true, config, mapping, "\n").single().contains("remove"))
    }

    @Test
    fun `usage with removed members or synthetic classes only (keep-all rule) is a violation`() {
        val keepAll = "a.B:\n    void unused()\na.B\$\$ExternalSyntheticLambda0\n"
        assertTrue(R8Check.check(true, config, mapping, keepAll).single().contains("remove"))
    }

    @Test
    fun `missing output files are violations`() {
        assertEquals(3, R8Check.check(true, null, null, null).size)
    }
}
