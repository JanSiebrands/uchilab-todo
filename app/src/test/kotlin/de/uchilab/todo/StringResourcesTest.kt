package de.uchilab.todo

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plain JVM test: English (default) and German string resources must stay in sync. */
class StringResourcesTest {

    private fun strings(dir: String): Map<String, String> {
        val file = File("src/main/res/$dir/strings.xml")
        assertTrue("missing ${file.path}", file.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate {
            val node = nodes.item(it)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
    }

    @Test
    fun `german and english define the same keys`() {
        assertEquals(strings("values").keys, strings("values-de").keys)
    }

    @Test
    fun `app name is the working title in both languages`() {
        assertEquals("uchilab-todo", strings("values")["app_name"])
        assertEquals("uchilab-todo", strings("values-de")["app_name"])
    }
}
