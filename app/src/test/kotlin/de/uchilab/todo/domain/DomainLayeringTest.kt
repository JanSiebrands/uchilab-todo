package de.uchilab.todo.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** The domain layer is plain Kotlin: no reference to `android.` or `androidx.` (plan section 2). */
class DomainLayeringTest {

    private val domainDir = File("src/main/kotlin/de/uchilab/todo/domain")
    private val forbidden = Regex("""\bandroidx?\.""")

    @Test
    fun `domain sources are found`() {
        assertTrue("no domain sources in ${domainDir.path}", domainFiles().isNotEmpty())
    }

    @Test
    fun `domain sources do not refer to android or androidx`() {
        val offenders = domainFiles().filter { file -> file.readLines().any { forbidden.containsMatchIn(it) } }
        assertTrue("android references in: ${offenders.map { it.name }}", offenders.isEmpty())
    }

    private fun domainFiles(): List<File> =
        domainDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
}
