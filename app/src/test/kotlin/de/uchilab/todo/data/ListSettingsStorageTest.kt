package de.uchilab.todo.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.uchilab.todo.domain.WriteResult
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The setting through the production class, on the real file system of the test app. */
@RunWith(RobolectricTestRunner::class)
class ListSettingsStorageTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val settingsFile get() = File(context.dataDir, "shared_prefs/list_settings.xml")

    private fun newSettings() = SharedPreferencesListSettings(context)

    private fun firstValue() = runBlocking { newSettings().hideDone.first() }

    private fun writeRawFile(content: String) {
        settingsFile.parentFile!!.mkdirs()
        settingsFile.writeText("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n$content\n</map>\n")
    }

    /** Every file below the data directory, relative to it. */
    private fun dataFiles(): Set<String> =
        context.dataDir.walkTopDown().filter { it.isFile }.map { it.relativeTo(context.dataDir).path }.toSet()

    // Z4
    @Test
    fun `a stored true is read from the file before anything else was touched`() {
        writeRawFile("""    <boolean name="hide_done" value="true" />""")
        assertTrue(firstValue())
    }

    // Z5
    @Test
    fun `without a file the first value is false and reading creates no file`() {
        val before = dataFiles()
        assertFalse(firstValue())
        assertEquals(before, dataFiles())
        assertFalse(settingsFile.exists())
    }

    // Z24
    @Test
    fun `a value of another type reads as false without a crash`() {
        writeRawFile("""    <string name="hide_done">true</string>""")
        assertFalse(firstValue())
    }

    @Test
    fun `a stored value survives a new instance, also the last one of several writes`() {
        val settings = newSettings()
        runBlocking {
            assertTrue(settings.setHideDone(true) is WriteResult.Success)
            assertTrue(settings.setHideDone(false) is WriteResult.Success)
            assertTrue(settings.setHideDone(true) is WriteResult.Success)
        }
        assertTrue(firstValue())
        runBlocking { settings.setHideDone(false) }
        assertFalse(firstValue())
    }

    @Test
    fun `the value is reported only after it was written`() {
        val settings = newSettings()
        runBlocking {
            assertFalse(settings.hideDone.first())
            settings.setHideDone(true)
            assertTrue(settings.hideDone.first())
        }
    }

    // Z29
    @Test
    fun `the setting adds exactly one file with exactly one boolean key`() {
        val before = dataFiles()
        runBlocking { newSettings().setHideDone(true) }

        assertEquals(setOf("shared_prefs/list_settings.xml"), dataFiles() - before)
        val map = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(settingsFile).documentElement
        val entries = (0 until map.childNodes.length).map { map.childNodes.item(it) }.filter { it.nodeType == org.w3c.dom.Node.ELEMENT_NODE }
        assertEquals(1, entries.size)
        assertEquals("boolean", entries.single().nodeName)
        assertEquals("hide_done", entries.single().attributes.getNamedItem("name").nodeValue)
        assertEquals("true", entries.single().attributes.getNamedItem("value").nodeValue)
    }
}
