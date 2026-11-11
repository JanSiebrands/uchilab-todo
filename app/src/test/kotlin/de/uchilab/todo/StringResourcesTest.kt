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
    fun `app name is defined and not blank in both languages`() {
        val english = strings("values")["app_name"]
        val german = strings("values-de")["app_name"]
        assertTrue("app_name missing or blank in values", !english.isNullOrBlank())
        assertTrue("app_name missing or blank in values-de", !german.isNullOrBlank())
    }

    private val requiredKeys = listOf(
        "task_list_empty_title", "task_list_empty_body", "task_list_error_title", "task_list_error_body",
        "task_item_description", "task_list_empty_hint",
        "new_task_action", "new_task_heading", "new_task_close", "new_task_save", "new_task_title_label",
        "new_task_title_required", "new_task_note_label", "new_task_title_missing", "new_task_title_too_long",
        "new_task_note_too_long", "new_task_counter", "new_task_counter_description", "new_task_save_failed_title",
        "new_task_save_failed_body", "new_task_discard_title", "new_task_discard_body", "new_task_discard_keep",
        "new_task_discard_confirm", "new_task_created",
        "task_list_hide_done", "task_list_show_done", "task_list_hide_done_description", "task_list_show_done_description",
        "task_list_done_hidden_count", "task_list_all_hidden_title", "task_list_all_hidden_show", "task_done_hidden",
        "task_list_setting_save_failed",
    )

    @Test
    fun `all list texts exist and are not blank in both languages`() {
        for (dir in listOf("values", "values-de")) {
            val texts = strings(dir)
            for (key in requiredKeys) {
                assertTrue("$key missing or blank in $dir", !texts[key].isNullOrBlank())
            }
        }
    }

    @Test
    fun `limit and counter texts take their numbers by placeholder`() {
        for (dir in listOf("values", "values-de")) {
            val texts = strings(dir)
            assertTrue(texts.getValue("new_task_title_too_long").contains("%1\$s"))
            assertTrue(texts.getValue("new_task_note_too_long").contains("%1\$s"))
            for (key in listOf("new_task_counter", "new_task_counter_description")) {
                assertTrue("$dir $key", texts.getValue(key).contains("%1\$s") && texts.getValue(key).contains("%2\$s"))
            }
        }
    }

    @Test
    fun `no string is blank`() {
        for (dir in listOf("values", "values-de")) {
            val blank = strings(dir).filterValues { it.isBlank() }.keys
            assertTrue("blank strings in $dir: $blank", blank.isEmpty())
        }
    }

    @Test
    fun `german and english use the same placeholders`() {
        val placeholder = Regex("""%(\d+\$)?[sd]""")
        val english = strings("values")
        val german = strings("values-de")
        for (key in english.keys) {
            val expected = placeholder.findAll(english.getValue(key)).map { it.value }.sorted().toList()
            val actual = placeholder.findAll(german.getValue(key)).map { it.value }.sorted().toList()
            assertEquals("placeholders of $key", expected, actual)
        }
    }

    @Test
    fun `the entry text has a placeholder for title and for the note`() {
        for (dir in listOf("values", "values-de")) {
            val text = strings(dir).getValue("task_item_description")
            assertTrue("$dir: title placeholder", text.contains("%1\$s"))
            assertTrue("$dir: note placeholder", text.contains("%2\$s"))
        }
    }

    private fun plurals(dir: String): Map<String, Map<String, String>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$dir/strings.xml"))
        val nodes = doc.getElementsByTagName("plurals")
        return (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            val items = (node as org.w3c.dom.Element).getElementsByTagName("item")
            node.attributes.getNamedItem("name").nodeValue to
                (0 until items.length).associate { items.item(it).attributes.getNamedItem("quantity").nodeValue to items.item(it).textContent }
        }
    }

    @Test
    fun `german and english define the same plurals with the same quantities and none is blank`() {
        val english = plurals("values")
        val german = plurals("values-de")
        assertTrue("no plurals defined", english.isNotEmpty())
        assertEquals(english.keys, german.keys)
        for (name in english.keys) {
            assertEquals("quantities of $name", english.getValue(name).keys, german.getValue(name).keys)
            for ((dir, texts) in listOf("values" to english, "values-de" to german)) {
                for ((quantity, text) in texts.getValue(name)) {
                    assertTrue("$dir $name/$quantity is blank", text.isNotBlank())
                    assertTrue("$dir $name/$quantity has no number placeholder", text.contains("%1\$d"))
                }
            }
        }
    }

    @Test
    fun `the hidden count text takes its number by placeholder in both languages`() {
        for (dir in listOf("values", "values-de")) {
            assertTrue(dir, strings(dir).getValue("task_list_done_hidden_count").contains("%1\$d"))
            assertTrue(dir, strings(dir).getValue("task_done_hidden").contains("%1\$s"))
        }
    }

    // Z40
    @Test
    fun `the delete texts exist in both languages and take the title by placeholder`() {
        for (dir in listOf("values", "values-de")) {
            val texts = strings(dir)
            for (key in listOf("task_deleted", "task_not_restored", "task_restored_announce")) {
                assertTrue("$dir $key", texts.getValue(key).contains("%1\$s"))
            }
            for (key in listOf("task_undo_action", "task_restore_retry_action", "task_delete_description", "task_delete_failed_title", "task_delete_failed_body")) {
                assertTrue("$dir $key", texts.getValue(key).isNotBlank())
            }
        }
    }
}
