package de.uchilab.todo.ui.taskform

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import de.uchilab.todo.TodoApplication
import de.uchilab.todo.data.TaskDatabase
import de.uchilab.todo.domain.Task
import de.uchilab.todo.domain.TaskContent
import de.uchilab.todo.domain.TaskStatus
import de.uchilab.todo.domain.Validated
import de.uchilab.todo.domain.WriteResult
import de.uchilab.todo.ui.MainActivity
import de.uchilab.todo.ui.tasklist.TaskListTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** MainActivity with the real Room file, wired through [TodoApplication]. Invented data only. */
@RunWith(RobolectricTestRunner::class)
class TaskFormIntegrationTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val application: TodoApplication = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val family = "👨‍👩‍👧‍👦"

    @Before
    fun cleanFile() {
        application.deleteDatabase(TaskDatabase.DATABASE_NAME)
    }

    @After
    fun closeScenario() {
        scenario?.close()
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun add(title: String, note: String? = null): Task {
        val content = (TaskContent.of(title, note) as Validated.Valid).content
        return (runBlocking { application.taskRepository.add(content) } as WriteResult.Success).value
    }

    private fun stored(): List<Task> = runBlocking { application.taskRepository.observeTasks().first() }

    private fun count(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun waitFor(message: String, condition: () -> Boolean) {
        composeRule.waitUntil(10_000) { condition() }
        assertTrue(message, condition())
    }

    private fun openForm() {
        waitFor("action") { count(TaskFormTags.ACTION) == 1 }
        composeRule.onNodeWithTag(TaskFormTags.ACTION).performClick()
        waitFor("form") { count(TaskFormTags.TITLE_FIELD) == 1 }
    }

    private fun typeTitle(text: String) = composeRule.onNodeWithTag(TaskFormTags.TITLE_FIELD).performTextInput(text)
    private fun typeNote(text: String) = composeRule.onNodeWithTag(TaskFormTags.NOTE_FIELD).performTextInput(text)
    private fun save() = composeRule.onNodeWithTag(TaskFormTags.SAVE).performClick()
    private fun formIsGone() = count(TaskFormTags.TITLE_FIELD) == 0

    private fun back() {
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    // --- opening ---

    @Test
    fun `the action in the empty state opens the form with the cursor in the title and the keyboard open`() {
        launch()
        openForm()
        composeRule.onNodeWithTag(TaskFormTags.TITLE_FIELD).assertIsFocused()
        val manager = application.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        waitFor("keyboard") { shadowOf(manager).isSoftInputVisible }
    }

    @Test
    fun `the action in a filled list opens the form with the cursor in the title`() {
        add("Aufgabe 1")
        launch()
        waitFor("list") { count(TaskListTags.ITEM) == 1 }
        openForm()
        composeRule.onNodeWithTag(TaskFormTags.TITLE_FIELD).assertIsFocused()
    }

    // --- creating ---

    @Test
    fun `a saved task closes the form and is the topmost open entry`() {
        add("Aufgabe 1")
        add("Aufgabe 2")
        launch()
        waitFor("list") { count(TaskListTags.ITEM) == 2 }
        openForm()
        typeTitle("Aufgabe neu")
        save()
        waitFor("form closed") { formIsGone() && count(TaskListTags.ITEM) == 3 }
        val tops = composeRule.onAllNodesWithTag(TaskListTags.ITEM_TITLE, useUnmergedTree = true)
            .fetchSemanticsNodes().sortedBy { it.boundsInRoot.top }
            .map { it.config[SemanticsProperties.Text].joinToString("") }
        assertEquals(listOf("Aufgabe neu", "Aufgabe 2", "Aufgabe 1"), tops)
        val first = stored().first()
        assertEquals("Aufgabe neu", first.content.title)
        assertEquals(TaskStatus.OPEN, first.status)
    }

    @Test
    fun `after saving the list jumps to the top`() {
        for (n in 1..100) add("Aufgabe $n")
        launch()
        waitFor("list") { count(TaskListTags.ITEM) > 0 }
        composeRule.onNodeWithTag(TaskListTags.LIST).performScrollToIndex(50)
        openForm()
        typeTitle("Aufgabe neu")
        save()
        waitFor("form closed") { formIsGone() }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag(TaskListTags.ITEM_TITLE, useUnmergedTree = true).fetchSemanticsNodes()
                .any { it.config[SemanticsProperties.Text].joinToString("") == "Aufgabe neu" }
        }
        val top = composeRule.onAllNodesWithTag(TaskListTags.ITEM_TITLE, useUnmergedTree = true)
            .fetchSemanticsNodes().minByOrNull { it.boundsInRoot.top }!!
        assertEquals("Aufgabe neu", top.config[SemanticsProperties.Text].joinToString(""))
        assertTrue(top.boundsInRoot.top >= 0f)
    }

    @Test
    fun `title and note are stored, line breaks in the note stay`() {
        launch()
        openForm()
        typeTitle("Aufgabe 1")
        typeNote("Zeile 1\nZeile 2")
        save()
        waitFor("form closed") { formIsGone() }
        val task = stored().single()
        assertEquals("Aufgabe 1", task.content.title)
        assertEquals("Zeile 1\nZeile 2", task.content.note)
    }

    @Test
    fun `a title typed with line breaks is stored on one line`() {
        launch()
        openForm()
        typeTitle("A\nB")
        save()
        waitFor("form closed") { formIsGone() }
        assertTrue(stored().single().content.title.none { it == '\n' || it == '\r' })
    }

    @Test
    fun `a created task is announced by a polite live region`() {
        launch()
        openForm()
        typeTitle("Aufgabe 1")
        save()
        waitFor("announcement") {
            composeRule.onAllNodesWithTag(TaskFormTags.CREATED_ANNOUNCEMENT, useUnmergedTree = true)
                .fetchSemanticsNodes().any { SemanticsProperties.ContentDescription in it.config }
        }
        val config = composeRule.onAllNodesWithTag(TaskFormTags.CREATED_ANNOUNCEMENT, useUnmergedTree = true)
            .fetchSemanticsNodes().single().config
        assertEquals(LiveRegionMode.Polite, config[SemanticsProperties.LiveRegion])
    }

    // --- invalid input ---

    @Test
    fun `saving without a title shows the hint, keeps the form open and stores nothing`() {
        launch()
        openForm()
        typeTitle("   ")
        save()
        waitFor("hint") { count(TaskFormTags.TITLE_HINT) == 1 }
        assertTrue(SemanticsProperties.Error in composeRule.onNodeWithTag(TaskFormTags.TITLE_FIELD).fetchSemanticsNode().config)
        composeRule.onNodeWithTag(TaskFormTags.TITLE_FIELD).assertIsFocused()
        assertTrue(stored().isEmpty())
    }

    @Test
    fun `exactly 200 and 2000 characters with emoji can be saved`() {
        launch()
        openForm()
        typeTitle(family.repeat(200))
        typeNote(family.repeat(2000))
        save()
        waitFor("form closed") { formIsGone() }
        val task = stored().single()
        assertEquals(200, TaskContent.titleLength(task.content.title))
        assertEquals(2000, TaskContent.noteLength(task.content.note!!))
    }

    @Test
    fun `201 characters in the title and 2001 in the note are not saved and show hints`() {
        launch()
        openForm()
        typeTitle(family.repeat(201))
        typeNote(family.repeat(2001))
        waitFor("hints") { count(TaskFormTags.TITLE_HINT) == 1 && count(TaskFormTags.NOTE_HINT) == 1 }
        save()
        composeRule.waitForIdle()
        assertTrue(count(TaskFormTags.TITLE_FIELD) == 1)
        assertTrue(SemanticsProperties.Error in composeRule.onNodeWithTag(TaskFormTags.TITLE_FIELD).fetchSemanticsNode().config)
        assertTrue(SemanticsProperties.Error in composeRule.onNodeWithTag(TaskFormTags.NOTE_FIELD).fetchSemanticsNode().config)
        assertTrue(stored().isEmpty())
    }

    // --- saving twice, failing ---

    @Test
    fun `two taps on save in the same moment store exactly one task`() {
        launch()
        openForm()
        typeTitle("Aufgabe 1")
        val click = composeRule.onNodeWithTag(TaskFormTags.SAVE).fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        composeRule.runOnUiThread {
            click()
            click()
        }
        waitFor("form closed") { formIsGone() }
        assertEquals(1, stored().size)
    }

    /** A second connection holds the write lock, so the writes of the app fail with a storage error. */
    private fun <T> withWriteLockedStore(block: () -> T): T {
        val raw = SQLiteDatabase.openDatabase(
            application.getDatabasePath(TaskDatabase.DATABASE_NAME).path, null, SQLiteDatabase.OPEN_READWRITE,
        )
        raw.beginTransactionNonExclusive()
        raw.execSQL("CREATE TABLE IF NOT EXISTS lock_probe (x INTEGER)")
        return try {
            block()
        } finally {
            raw.endTransaction()
            raw.close()
        }
    }

    @Test
    fun `a failing write shows the hint, keeps the text and a new try can succeed`() {
        launch()
        openForm()
        typeTitle("Aufgabe 1")
        typeNote("Notiz 1")
        withWriteLockedStore {
            save()
            waitFor("save failed hint") { count(TaskFormTags.SAVE_FAILED) == 1 }
        }
        assertEquals(LiveRegionMode.Assertive,
            composeRule.onNodeWithTag(TaskFormTags.SAVE_FAILED).fetchSemanticsNode().config[SemanticsProperties.LiveRegion])
        composeRule.onNodeWithTag(TaskFormTags.TITLE_FIELD).assertIsDisplayed()
        assertTrue(stored().isEmpty())
        save()
        waitFor("form closed") { formIsGone() }
        assertEquals(1, stored().size)
        assertEquals(0, count(TaskFormTags.SAVE_FAILED))
    }

    // --- closing ---

    @Test
    fun `back with empty fields closes without asking`() {
        launch()
        openForm()
        back()
        waitFor("closed") { formIsGone() }
        assertEquals(0, count(TaskFormTags.DISCARD_DIALOG))
    }

    @Test
    fun `back with text asks, back in the dialog closes only the dialog`() {
        launch()
        openForm()
        typeTitle("Aufgabe 1")
        back()
        waitFor("dialog") { count(TaskFormTags.DISCARD_DIALOG) == 1 }
        back()
        waitFor("dialog gone") { count(TaskFormTags.DISCARD_DIALOG) == 0 }
        assertEquals(1, count(TaskFormTags.TITLE_FIELD))
    }

    @Test
    fun `the close sign with text asks, only confirming discards, the next input is empty`() {
        launch()
        openForm()
        typeTitle("Aufgabe 1")
        composeRule.onNodeWithTag(TaskFormTags.CLOSE).performClick()
        waitFor("dialog") { count(TaskFormTags.DISCARD_DIALOG) == 1 }
        composeRule.onNodeWithTag(TaskFormTags.DISCARD_KEEP).performClick()
        waitFor("dialog gone") { count(TaskFormTags.DISCARD_DIALOG) == 0 }
        assertEquals(1, count(TaskFormTags.TITLE_FIELD))
        composeRule.onNodeWithTag(TaskFormTags.CLOSE).performClick()
        waitFor("dialog") { count(TaskFormTags.DISCARD_DIALOG) == 1 }
        composeRule.onNodeWithTag(TaskFormTags.DISCARD_CONFIRM).performClick()
        waitFor("closed") { formIsGone() }
        assertTrue(stored().isEmpty())
        openForm()
        val text = composeRule.onNodeWithTag(TaskFormTags.TITLE_FIELD).fetchSemanticsNode().config
        assertEquals("", text[SemanticsProperties.EditableText].text)
    }

    // --- rotation ---

    @Test
    fun `rotating keeps text, the hint and the open dialog`() {
        launch()
        openForm()
        typeNote("Notiz 1")
        save()
        waitFor("hint") { count(TaskFormTags.TITLE_HINT) == 1 }
        composeRule.onNodeWithTag(TaskFormTags.CLOSE).performClick()
        waitFor("dialog") { count(TaskFormTags.DISCARD_DIALOG) == 1 }

        scenario!!.recreate()

        waitFor("dialog back") { count(TaskFormTags.DISCARD_DIALOG) == 1 }
        assertEquals(1, count(TaskFormTags.TITLE_HINT))
        val note = composeRule.onNodeWithTag(TaskFormTags.NOTE_FIELD).fetchSemanticsNode().config
        assertEquals("Notiz 1", note[SemanticsProperties.EditableText].text)
    }

    @Test
    fun `rotating after a save failure keeps the hint`() {
        launch()
        openForm()
        typeTitle("Aufgabe 1")
        withWriteLockedStore {
            save()
            waitFor("save failed hint") { count(TaskFormTags.SAVE_FAILED) == 1 }
        }
        scenario!!.recreate()
        waitFor("hint back") { count(TaskFormTags.SAVE_FAILED) == 1 }
        assertEquals("Aufgabe 1", composeRule.onNodeWithTag(TaskFormTags.TITLE_FIELD).fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text)
    }
}
