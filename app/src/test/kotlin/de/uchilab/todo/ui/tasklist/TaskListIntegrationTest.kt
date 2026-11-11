package de.uchilab.todo.ui.tasklist

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.performTextReplacement
import de.uchilab.todo.ui.taskform.TaskFormTags
import de.uchilab.todo.R
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import de.uchilab.todo.TodoApplication
import de.uchilab.todo.data.SharedPreferencesListSettings
import de.uchilab.todo.data.TaskDatabase
import java.io.File
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import de.uchilab.todo.domain.Task
import de.uchilab.todo.domain.TaskContent
import de.uchilab.todo.domain.TaskStatus
import de.uchilab.todo.domain.Validated
import de.uchilab.todo.domain.WriteResult
import de.uchilab.todo.ui.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** MainActivity with the real Room file, wired through [TodoApplication] like in the app. Invented data only. */
@RunWith(RobolectricTestRunner::class)
class TaskListIntegrationTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val application: TodoApplication = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null

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

    private fun itemCount() = composeRule.onAllNodesWithTag(TaskListTags.ITEM).fetchSemanticsNodes().size

    private fun waitForItems(count: Int) = composeRule.waitUntil(10_000) { itemCount() >= count }

    /** Titles of the entries currently composed, top to bottom. */
    private fun visibleTitles(): List<String> =
        composeRule.onAllNodesWithTag(TaskListTags.ITEM_TITLE, useUnmergedTree = true).fetchSemanticsNodes()
            .sortedBy { it.boundsInRoot.top }
            .map { it.config[SemanticsProperties.Text].joinToString("") }

    @Test
    fun `the app uses one repository instance`() {
        assertSame(application.taskRepository, application.taskRepository)
    }

    @Test
    fun `three tasks are listed newest first`() {
        add("Aufgabe 1")
        add("Aufgabe 2")
        add("Aufgabe 3")
        launch()
        waitForItems(3)
        assertEquals(listOf("Aufgabe 3", "Aufgabe 2", "Aufgabe 1"), visibleTitles())
    }

    @Test
    fun `a task added while the list is open appears without restart`() {
        launch()
        // wait until the first (empty) result is shown, so the task is added to an already open list
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag(TaskListTags.EMPTY).fetchSemanticsNodes().isNotEmpty()
        }
        add("Aufgabe live")
        waitForItems(1)
        assertEquals(listOf("Aufgabe live"), visibleTitles())
    }

    @Test
    fun `a done task is listed below the open ones`() {
        add("Aufgabe 1")
        val middle = add("Aufgabe 2")
        add("Aufgabe 3")
        runBlocking { application.taskRepository.setStatus(middle.id, TaskStatus.DONE) }
        launch()
        waitForItems(3)
        assertEquals(listOf("Aufgabe 3", "Aufgabe 1", "Aufgabe 2"), visibleTitles())
    }

    @Test
    fun `with 1000 tasks the oldest is reachable`() {
        for (n in 1..1000) add("Aufgabe $n")
        launch()
        waitForItems(1)
        composeRule.onNodeWithTag(TaskListTags.LIST).performScrollToIndex(999)
        composeRule.onNodeWithText("Aufgabe 1").assertIsDisplayed()
    }

    @Test
    fun `an empty store shows the empty state`() {
        launch()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag(TaskListTags.EMPTY).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithTag(TaskListTags.ERROR).fetchSemanticsNodes().let { assertEquals(0, it.size) }
    }

    @Test
    fun `the scroll position stays after the activity is recreated`() {
        for (n in 1..100) add("Aufgabe $n")
        launch()
        waitForItems(1)
        // newest first: index 50 is "Aufgabe 50"
        composeRule.onNodeWithTag(TaskListTags.LIST).performScrollToIndex(50)
        composeRule.onNodeWithText("Aufgabe 50").assertIsDisplayed()

        scenario!!.recreate()
        composeRule.waitUntil(10_000) { itemCount() > 0 }
        composeRule.onNodeWithText("Aufgabe 50").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("Aufgabe 100").fetchSemanticsNodes().size)
    }

    // --- checking off and taking back (S6) ---

    private fun checkboxNodes() = composeRule.onAllNodesWithTag(TaskListTags.ITEM_CHECKBOX)

    /** Taps the check box of the entry with this title. */
    private fun tapCheckboxOf(title: String) {
        checkboxNodes()[visibleTitles().indexOf(title)].performClick()
    }

    private fun checkboxStates(): List<ToggleableState> =
        checkboxNodes().fetchSemanticsNodes().sortedBy { it.boundsInRoot.top }.map { it.config[SemanticsProperties.ToggleableState] }

    private fun hasDoneHeading() = composeRule.onAllNodesWithTag(TaskListTags.DONE_HEADING).fetchSemanticsNodes().isNotEmpty()

    private fun waitForTitles(expected: List<String>) =
        composeRule.waitUntil(10_000) { visibleTitles() == expected }

    @Test
    fun `checking off an open task moves it below the done heading, without asking`() {
        add("Aufgabe 1")
        add("Aufgabe 2")
        add("Aufgabe 3")
        launch()
        waitForItems(3)
        assertFalse(hasDoneHeading())

        tapCheckboxOf("Aufgabe 3")

        waitForTitles(listOf("Aufgabe 2", "Aufgabe 1", "Aufgabe 3"))
        assertTrue(hasDoneHeading())
        assertEquals(listOf(ToggleableState.Off, ToggleableState.Off, ToggleableState.On), checkboxStates())
        val heading = composeRule.onNodeWithTag(TaskListTags.DONE_HEADING).fetchSemanticsNode().boundsInRoot
        val titles = composeRule.onAllNodesWithTag(TaskListTags.ITEM_TITLE, useUnmergedTree = true).fetchSemanticsNodes()
            .sortedBy { it.boundsInRoot.top }
        assertTrue(titles[1].boundsInRoot.bottom <= heading.top && heading.bottom <= titles[2].boundsInRoot.top)
    }

    @Test
    fun `taking a task back puts it at its place by creation date among the open ones`() {
        add("Aufgabe 1")
        add("Aufgabe 2")
        add("Aufgabe 3")
        launch()
        waitForItems(3)

        tapCheckboxOf("Aufgabe 2")
        waitForTitles(listOf("Aufgabe 3", "Aufgabe 1", "Aufgabe 2"))
        tapCheckboxOf("Aufgabe 2")

        waitForTitles(listOf("Aufgabe 3", "Aufgabe 2", "Aufgabe 1"))
        assertFalse(hasDoneHeading())
        assertEquals(List(3) { ToggleableState.Off }, checkboxStates())
    }

    @Test
    fun `the status stays after closing and starting the app again`() {
        add("Aufgabe 1")
        add("Aufgabe 2")
        launch()
        waitForItems(2)
        tapCheckboxOf("Aufgabe 2")
        waitForTitles(listOf("Aufgabe 1", "Aufgabe 2"))
        scenario!!.close()

        launch()
        waitForItems(2)
        assertEquals(listOf("Aufgabe 1", "Aufgabe 2"), visibleTitles())
        assertTrue(hasDoneHeading())
        assertEquals(listOf(ToggleableState.Off, ToggleableState.On), checkboxStates())
    }

    @Test
    fun `tapping five times in a row ends with the displayed status equal to the stored one`() {
        val task = add("Aufgabe 1")
        add("Aufgabe 2")
        launch()
        waitForItems(2)

        repeat(5) { checkboxNodes()[visibleTitles().indexOf("Aufgabe 1")].performClick() }

        fun stored() = runBlocking { application.taskRepository.observeTasks().first() }.single { it.id == task.id }.status
        fun shown() = if (checkboxStates()[visibleTitles().indexOf("Aufgabe 1")] == ToggleableState.On) TaskStatus.DONE else TaskStatus.OPEN
        composeRule.waitUntil(10_000) { shown() == stored() }
        composeRule.waitForIdle()
        assertEquals(stored(), shown())
        assertEquals(stored() == TaskStatus.DONE, hasDoneHeading())
    }

    @Test
    fun `after checking off and taking back the announcement names the task and the new status`() {
        add("Aufgabe 1")
        launch()
        waitForItems(1)
        val announcement = { composeRule.onNodeWithTag(TaskListTags.STATUS_ANNOUNCEMENT, useUnmergedTree = true) }

        tapCheckboxOf("Aufgabe 1")
        val done = application.getString(R.string.task_status_announce_done, "Aufgabe 1")
        composeRule.waitUntil(10_000) { announcement().fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription) == listOf(done) }
        announcement().assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))

        composeRule.waitUntil(10_000) { checkboxStates() == listOf(ToggleableState.On) }
        tapCheckboxOf("Aufgabe 1")
        val open = application.getString(R.string.task_status_announce_open, "Aufgabe 1")
        composeRule.waitUntil(10_000) { announcement().fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription) == listOf(open) }
    }

    @Test
    fun `the list stays where it is when the first visible entry is checked off`() {
        for (n in 1..30) add("Aufgabe $n")
        launch()
        waitForItems(1)
        composeRule.onNodeWithTag(TaskListTags.LIST).performScrollToIndex(5)
        composeRule.waitForIdle()
        fun topTitle(): String {
            val list = composeRule.onNodeWithTag(TaskListTags.LIST).fetchSemanticsNode().boundsInRoot
            val node = composeRule.onAllNodesWithTag(TaskListTags.ITEM_TITLE, useUnmergedTree = true).fetchSemanticsNodes()
                .filter { it.boundsInRoot.bottom > list.top }.minBy { it.boundsInRoot.top }
            return node.config[SemanticsProperties.Text].joinToString("")
        }
        assertEquals("Aufgabe 25", topTitle())

        tapCheckboxOf("Aufgabe 25")
        // the entry now lives at the end of the list, so wait for the store, then for the screen to settle
        composeRule.waitUntil(10_000) {
            runBlocking { application.taskRepository.observeTasks().first() }.single { it.content.title == "Aufgabe 25" }.status == TaskStatus.DONE
        }
        composeRule.waitForIdle()

        assertEquals("Aufgabe 24", topTitle())
    }

    // --- editing (S5) ---

    private fun stored(): List<Task> = runBlocking { application.taskRepository.observeTasks().first() }

    private fun count(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun openEntry(title: String) {
        composeRule.onNodeWithText(title).performClick()
        composeRule.waitUntil(10_000) { count(TaskFormTags.TITLE_FIELD) == 1 }
    }

    private fun formIsGone() = count(TaskFormTags.TITLE_FIELD) == 0

    private fun waitForFormGone() = composeRule.waitUntil(10_000) { formIsGone() }

    private fun back() {
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun replaceTitle(text: String) = composeRule.onNodeWithTag(TaskFormTags.TITLE_FIELD).performTextReplacement(text)
    private fun replaceNote(text: String) = composeRule.onNodeWithTag(TaskFormTags.NOTE_FIELD).performTextReplacement(text)
    private fun save() = composeRule.onNodeWithTag(TaskFormTags.SAVE).performClick()

    @Test
    fun `tapping an open or a done task opens the form with its title and note`() {
        add("Aufgabe offen", "Notiz offen")
        val done = add("Aufgabe erledigt", "Notiz erledigt")
        runBlocking { application.taskRepository.setStatus(done.id, TaskStatus.DONE) }
        launch()
        waitForItems(2)

        openEntry("Aufgabe offen")
        composeRule.onNodeWithTag(TaskFormTags.TITLE_FIELD).assert(hasText("Aufgabe offen"))
        composeRule.onNodeWithTag(TaskFormTags.NOTE_FIELD).assert(hasText("Notiz offen"))
        back()
        waitForFormGone()

        openEntry("Aufgabe erledigt")
        composeRule.onNodeWithTag(TaskFormTags.TITLE_FIELD).assert(hasText("Aufgabe erledigt"))
        composeRule.onNodeWithTag(TaskFormTags.NOTE_FIELD).assert(hasText("Notiz erledigt"))
    }

    @Test
    fun `opening and closing a done task without change keeps it done`() {
        val task = add("Aufgabe 1", "Notiz 1")
        runBlocking { application.taskRepository.setStatus(task.id, TaskStatus.DONE) }
        launch()
        waitForItems(1)
        openEntry("Aufgabe 1")
        back()
        waitForFormGone()
        assertEquals(TaskStatus.DONE, stored().single().status)
    }

    @Test
    fun `saving a change closes the form and the list shows the new title and note`() {
        add("Aufgabe 1", "Notiz 1")
        launch()
        waitForItems(1)
        openEntry("Aufgabe 1")
        replaceTitle("Aufgabe 2")
        replaceNote("Notiz 2")
        save()
        waitForFormGone()
        composeRule.waitUntil(10_000) { visibleTitles() == listOf("Aufgabe 2") }
        composeRule.onNodeWithTag(TaskListTags.ITEM_NOTE, useUnmergedTree = true).assert(hasText("Notiz 2"))
    }

    @Test
    fun `an edited task keeps its place, creation date and status`() {
        add("Aufgabe 1")
        val middle = add("Aufgabe 2")
        add("Aufgabe 3")
        runBlocking { application.taskRepository.setStatus(middle.id, TaskStatus.DONE) }
        val before = stored().single { it.id == middle.id }
        launch()
        waitForItems(3)
        assertEquals(listOf("Aufgabe 3", "Aufgabe 1", "Aufgabe 2"), visibleTitles())

        openEntry("Aufgabe 2")
        replaceTitle("Aufgabe zwei")
        save()
        waitForFormGone()

        composeRule.waitUntil(10_000) { visibleTitles() == listOf("Aufgabe 3", "Aufgabe 1", "Aufgabe zwei") }
        val after = stored().single { it.id == middle.id }
        assertEquals(TaskStatus.DONE, after.status)
        assertEquals(before.createdAt, after.createdAt)
    }

    @Test
    fun `clearing the note removes it from the task and from the entry`() {
        add("Aufgabe 1", "Notiz 1")
        launch()
        waitForItems(1)
        assertEquals(1, count(TaskListTags.ITEM_NOTE))
        openEntry("Aufgabe 1")
        replaceNote("")
        save()
        waitForFormGone()
        composeRule.waitUntil(10_000) { count(TaskListTags.ITEM_NOTE) == 0 }
        assertEquals(null, stored().single().content.note)
    }

    @Test
    fun `an edit stays after closing and starting the app again`() {
        add("Aufgabe 1")
        launch()
        waitForItems(1)
        openEntry("Aufgabe 1")
        replaceTitle("Aufgabe 2")
        save()
        waitForFormGone()
        composeRule.waitUntil(10_000) { visibleTitles() == listOf("Aufgabe 2") }
        scenario!!.close()

        launch()
        waitForItems(1)
        assertEquals(listOf("Aufgabe 2"), visibleTitles())
    }

    @Test
    fun `a changed form asks before closing, keep editing keeps the input, discard keeps the old version`() {
        add("Aufgabe 1", "Notiz 1")
        launch()
        waitForItems(1)
        openEntry("Aufgabe 1")
        replaceTitle("Aufgabe 2")

        back()
        composeRule.waitUntil(10_000) { count(TaskFormTags.DISCARD_DIALOG) == 1 }
        composeRule.onNodeWithTag(TaskFormTags.DISCARD_KEEP, useUnmergedTree = true).performClick()
        composeRule.waitUntil(10_000) { count(TaskFormTags.DISCARD_DIALOG) == 0 }
        composeRule.onNodeWithTag(TaskFormTags.TITLE_FIELD).assert(hasText("Aufgabe 2"))

        back()
        composeRule.waitUntil(10_000) { count(TaskFormTags.DISCARD_DIALOG) == 1 }
        composeRule.onNodeWithTag(TaskFormTags.DISCARD_CONFIRM, useUnmergedTree = true).performClick()
        waitForFormGone()
        composeRule.waitUntil(10_000) { visibleTitles() == listOf("Aufgabe 1") }
        assertEquals("Aufgabe 1", stored().single().content.title)
    }

    private fun firstVisibleTitle(): String {
        val list = composeRule.onNodeWithTag(TaskListTags.LIST).fetchSemanticsNode().boundsInRoot
        val node = composeRule.onAllNodesWithTag(TaskListTags.ITEM_TITLE, useUnmergedTree = true).fetchSemanticsNodes()
            .filter { it.boundsInRoot.bottom > list.top }.minBy { it.boundsInRoot.top }
        return node.config[SemanticsProperties.Text].joinToString("")
    }

    @Test
    fun `after saving an edit the list is at the same position as before, not at the top`() {
        for (n in 1..30) add("Aufgabe $n")
        launch()
        waitForItems(1)
        composeRule.onNodeWithTag(TaskListTags.LIST).performScrollToIndex(10)
        composeRule.waitForIdle()
        val top = firstVisibleTitle()
        assertEquals("Aufgabe 20", top)

        openEntry(top)
        replaceTitle("Aufgabe 20 neu")
        save()
        waitForFormGone()
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText("Aufgabe 20 neu").fetchSemanticsNodes().isNotEmpty() }
        composeRule.waitForIdle()
        assertEquals("Aufgabe 20 neu", firstVisibleTitle())
    }

    @Test
    fun `after saving an edit the live region says so politely`() {
        add("Aufgabe 1")
        launch()
        waitForItems(1)
        openEntry("Aufgabe 1")
        replaceTitle("Aufgabe 2")
        save()
        waitForFormGone()
        val announcement = { composeRule.onNodeWithTag(TaskFormTags.CREATED_ANNOUNCEMENT, useUnmergedTree = true) }
        val saved = application.getString(R.string.task_edit_saved)
        composeRule.waitUntil(10_000) {
            announcement().fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription) == listOf(saved)
        }
        announcement().assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun `after saving an edit the focus is on the edited entry`() {
        add("Aufgabe 1")
        add("Aufgabe 2")
        launch()
        waitForItems(2)
        openEntry("Aufgabe 1")
        replaceTitle("Aufgabe eins")
        save()
        waitForFormGone()
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText("Aufgabe eins").fetchSemanticsNodes().isNotEmpty() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Aufgabe eins").assertIsFocused()
    }

    // --- editing opens without focus, also after earlier focus requests ---

    private fun focused(tag: String) =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Focused) == true

    private fun assertNoFieldFocused() {
        composeRule.waitForIdle()
        assertFalse(focused(TaskFormTags.TITLE_FIELD))
        assertFalse(focused(TaskFormTags.NOTE_FIELD))
    }

    private fun openNewTask() {
        composeRule.onNodeWithTag(TaskFormTags.ACTION).performClick()
        composeRule.waitUntil(10_000) { count(TaskFormTags.TITLE_FIELD) == 1 }
    }

    private fun close() = composeRule.onNodeWithTag(TaskFormTags.CLOSE).performClick()

    @Test
    fun `an entry opened after a new task was closed has no field focused`() {
        add("Aufgabe 1", "Notiz 1")
        launch()
        waitForItems(1)
        openNewTask()
        close()
        waitForFormGone()

        openEntry("Aufgabe 1")
        assertNoFieldFocused()
    }

    @Test
    fun `an entry opened after a new task was saved has no field focused`() {
        add("Aufgabe 1", "Notiz 1")
        launch()
        waitForItems(1)
        openNewTask()
        replaceTitle("Aufgabe 2")
        save()
        waitForFormGone()
        waitForItems(2)

        openEntry("Aufgabe 1")
        assertNoFieldFocused()
    }

    @Test
    fun `an entry opened after an edit with a rejected save and a corrected save has no field focused`() {
        add("Aufgabe 1")
        add("Aufgabe 2")
        launch()
        waitForItems(2)
        openEntry("Aufgabe 1")
        replaceTitle("")
        replaceNote("Notiz")
        composeRule.waitUntil(10_000) { focused(TaskFormTags.NOTE_FIELD) }
        save()
        composeRule.waitUntil(10_000) { focused(TaskFormTags.TITLE_FIELD) }
        replaceTitle("Aufgabe eins")
        save()
        waitForFormGone()
        composeRule.waitUntil(10_000) { visibleTitles().contains("Aufgabe eins") }

        openEntry("Aufgabe 2")
        assertNoFieldFocused()
    }

    @Test
    fun `an entry stays without focus after the screen is recreated`() {
        add("Aufgabe 1", "Notiz 1")
        launch()
        waitForItems(1)
        openNewTask()
        close()
        waitForFormGone()
        openEntry("Aufgabe 1")
        assertNoFieldFocused()

        scenario!!.recreate()
        composeRule.waitUntil(10_000) { count(TaskFormTags.TITLE_FIELD) == 1 }
        assertNoFieldFocused()
    }

    @Test
    fun `a new task opened after an edit was closed has the title focused`() {
        add("Aufgabe 1")
        launch()
        waitForItems(1)
        openEntry("Aufgabe 1")
        close()
        waitForFormGone()

        openNewTask()
        composeRule.waitUntil(10_000) { focused(TaskFormTags.TITLE_FIELD) }
        composeRule.onNodeWithTag(TaskFormTags.TITLE_FIELD).assertIsFocused()
    }

    // --- hiding done tasks (S7); real Room file and the real setting ---

    private fun addDone(title: String): Task =
        add(title).also { runBlocking { application.taskRepository.setStatus(it.id, TaskStatus.DONE) } }

    private fun hideDoneBeforeStart() {
        assertTrue(runBlocking { application.listSettings.setHideDone(true) } is WriteResult.Success)
    }

    private fun toggle() = composeRule.onNodeWithTag(TaskListTags.DONE_TOGGLE)

    private fun waitForToggle() = composeRule.waitUntil(10_000) { count(TaskListTags.DONE_TOGGLE) == 1 }

    private fun toggleDescription() =
        composeRule.onNodeWithTag(TaskListTags.DONE_TOGGLE).fetchSemanticsNode().config[SemanticsProperties.ContentDescription]

    private fun hideDescription() = listOf(application.getString(R.string.task_list_hide_done_description))
    private fun showDescription() = listOf(application.getString(R.string.task_list_show_done_description))

    /** What a fresh reader of the stored file sees, not the instance the app holds. */
    private fun storedHideDone() = runBlocking { SharedPreferencesListSettings(application).hideDone.first() }

    // Z1
    @Test
    fun `hiding leaves only the open tasks in their order`() {
        add("Offen 1"); add("Offen 2"); add("Offen 3"); addDone("Fertig 1"); addDone("Fertig 2")
        launch()
        waitForItems(5)
        waitForToggle()

        toggle().performClick()

        waitForTitles(listOf("Offen 3", "Offen 2", "Offen 1"))
    }

    // Z2
    @Test
    fun `showing again lists open tasks, the row and the done tasks as before`() {
        add("Offen 1"); add("Offen 2"); addDone("Fertig 1"); addDone("Fertig 2")
        hideDoneBeforeStart()
        launch()
        waitForItems(2)
        waitForToggle()
        assertEquals(listOf("Offen 2", "Offen 1"), visibleTitles())

        toggle().performClick()

        waitForTitles(listOf("Offen 2", "Offen 1", "Fertig 2", "Fertig 1"))
        val heading = composeRule.onNodeWithTag(TaskListTags.DONE_HEADING).fetchSemanticsNode().boundsInRoot
        val titles = composeRule.onAllNodesWithTag(TaskListTags.ITEM_TITLE, useUnmergedTree = true).fetchSemanticsNodes().sortedBy { it.boundsInRoot.top }
        assertTrue(titles[1].boundsInRoot.bottom <= heading.top && heading.bottom <= titles[2].boundsInRoot.top)
    }

    // Z3
    @Test
    fun `hidden and shown both stay after closing and starting the app again`() {
        add("Offen 1"); addDone("Fertig 1")
        launch()
        waitForItems(2)
        waitForToggle()
        toggle().performClick()
        waitForTitles(listOf("Offen 1"))
        scenario!!.close()

        launch()
        waitForItems(1)
        waitForToggle()
        assertEquals(listOf("Offen 1"), visibleTitles())
        assertEquals(showDescription(), toggleDescription())
        toggle().performClick()
        waitForTitles(listOf("Offen 1", "Fertig 1"))
        composeRule.waitUntil(10_000) { !storedHideDone() }
        scenario!!.close()

        launch()
        waitForItems(2)
        assertEquals(listOf("Offen 1", "Fertig 1"), visibleTitles())
    }

    // Z6
    @Test
    fun `an update without a stored setting shows the done tasks and the button to hide them`() {
        add("Offen 1"); addDone("Fertig 1")
        assertFalse(File(application.dataDir, "shared_prefs/list_settings.xml").exists())
        launch()
        waitForItems(2)
        waitForToggle()
        assertEquals(listOf("Offen 1", "Fertig 1"), visibleTitles())
        assertEquals(hideDescription(), toggleDescription())
    }

    // Z7
    @Test
    fun `checking off while hidden removes the entry, stores it as done and shows the hint`() {
        add("Offen 1"); add("Offen 2"); addDone("Fertig 1")
        hideDoneBeforeStart()
        launch()
        waitForItems(2)

        tapCheckboxOf("Offen 2")

        waitForTitles(listOf("Offen 1"))
        assertEquals(TaskStatus.DONE, stored().single { it.content.title == "Offen 2" }.status)
        // Z8 in the real app: the hint names the task instead of the spoken announcement
        composeRule.waitUntil(10_000) {
            count(TaskListTags.STATUS_SNACKBAR) == 1
        }
        composeRule.onNodeWithText(application.getString(R.string.task_done_hidden, "Offen 2")).assertIsDisplayed()
        val announcement = composeRule.onNodeWithTag(TaskListTags.STATUS_ANNOUNCEMENT, useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(null, announcement.config.getOrNull(SemanticsProperties.ContentDescription))
    }

    // Z11
    @Test
    fun `a new task while everything is done and hidden stands on top and ends the all done state`() {
        addDone("Fertig 1")
        hideDoneBeforeStart()
        launch()
        composeRule.waitUntil(10_000) { count(TaskListTags.ALL_HIDDEN) == 1 }
        openNewTask()
        composeRule.onNodeWithTag(TaskFormTags.TITLE_FIELD).performTextReplacement("Neue Aufgabe")
        save()
        waitForFormGone()

        composeRule.waitUntil(10_000) { visibleTitles().firstOrNull() == "Neue Aufgabe" }
        assertEquals(0, count(TaskListTags.ALL_HIDDEN))
        waitForToggle()
    }

    // Z12
    /**
     * Open tasks come first, so the row follows the open entries. From index 25 five open entries stand above it:
     * the row is in view above the "New task" button, and the list stays long enough that hiding needs no correction.
     * With the done tasks hidden the list is shorter, the end of the list lies on screen and the row sits above the button.
     */
    private fun scrollRowIntoView(index: Int) {
        composeRule.onNodeWithTag(TaskListTags.LIST).performScrollToIndex(index)
        composeRule.waitForIdle()
        waitForToggle()
        toggle().assertIsDisplayed()
    }

    @Test
    fun `hiding does not move the list when the done row is in view`() {
        for (n in 1..5) addDone("Fertig $n")
        for (n in 1..30) add("Aufgabe $n")
        launch()
        waitForItems(1)
        scrollRowIntoView(25)
        val top = firstVisibleTitle()

        toggle().performClick()
        composeRule.waitUntil(10_000) { visibleTitles().none { it.startsWith("Fertig") } }
        composeRule.waitForIdle()

        assertEquals(top, firstVisibleTitle())
    }

    @Test
    fun `showing does not move the list when the done row is in view`() {
        for (n in 1..5) addDone("Fertig $n")
        for (n in 1..30) add("Aufgabe $n")
        hideDoneBeforeStart()
        launch()
        waitForItems(1)
        scrollRowIntoView(29)
        val top = firstVisibleTitle()

        toggle().performClick()
        composeRule.waitUntil(10_000) { visibleTitles().any { it.startsWith("Fertig") } }
        composeRule.waitForIdle()

        assertEquals(top, firstVisibleTitle())
    }

    // Z15
    @Test
    fun `after tapping the button the focus is still on it`() {
        add("Offen 1"); addDone("Fertig 1")
        launch()
        waitForItems(2)
        waitForToggle()
        toggle().performSemanticsAction(SemanticsActions.RequestFocus)
        toggle().assertIsFocused()

        toggle().performClick()
        composeRule.waitUntil(10_000) { visibleTitles() == listOf("Offen 1") }
        composeRule.waitForIdle()

        toggle().assertIsFocused()
        assertEquals(showDescription(), toggleDescription())
    }

    // Z16
    @Test
    fun `checking off while hidden moves the focus to the entry that takes its place`() {
        add("Offen 1"); add("Offen 2"); add("Offen 3"); addDone("Fertig 1")
        hideDoneBeforeStart()
        launch()
        waitForItems(3)

        tapCheckboxOf("Offen 2")
        waitForTitles(listOf("Offen 3", "Offen 1"))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Offen 1").assertIsFocused()
    }

    @Test
    fun `checking off the last open entry while hidden moves the focus to the button of the done row`() {
        add("Offen 1"); add("Offen 2"); addDone("Fertig 1")
        hideDoneBeforeStart()
        launch()
        waitForItems(2)

        tapCheckboxOf("Offen 1")
        waitForTitles(listOf("Offen 2"))
        composeRule.waitForIdle()

        toggle().assertIsFocused()
    }

    // Z20
    @Test
    fun `tapping five times in a row ends with the list equal to the stored setting`() {
        add("Offen 1"); addDone("Fertig 1")
        launch()
        waitForItems(2)
        waitForToggle()

        repeat(5) { toggle().performClick() }
        composeRule.waitUntil(10_000) { toggleDescription() == if (storedHideDone()) showDescription() else hideDescription() }
        composeRule.waitForIdle()

        val hidden = storedHideDone()
        assertEquals(if (hidden) showDescription() else hideDescription(), toggleDescription())
        assertEquals(if (hidden) listOf("Offen 1") else listOf("Offen 1", "Fertig 1"), visibleTitles())
    }

    // Z25
    @Test
    fun `turning the screen keeps the setting and the list`() {
        add("Offen 1"); addDone("Fertig 1")
        launch()
        waitForItems(2)
        waitForToggle()
        toggle().performClick()
        waitForTitles(listOf("Offen 1"))

        scenario!!.recreate()
        waitForItems(1)
        waitForToggle()

        assertEquals(listOf("Offen 1"), visibleTitles())
        assertEquals(showDescription(), toggleDescription())
    }

    // --- delete with undo (S8) ---

    private fun deleteViaForm(title: String) {
        openEntry(title)
        composeRule.onNodeWithTag(TaskFormTags.DELETE).performClick()
        waitForFormGone()
    }

    private fun deletedText(title: String) = application.getString(R.string.task_deleted, title)
    private fun undoNode() = composeRule.onNodeWithText(application.getString(R.string.task_undo_action))
    private fun waitForSnackbar() = composeRule.waitUntil(10_000) { count(TaskListTags.STATUS_SNACKBAR) == 1 }
    private fun waitForTitleInStore(title: String, present: Boolean) =
        composeRule.waitUntil(10_000) { stored().any { it.content.title == title } == present }

    private fun tapUndo() {
        undoNode().performClick()
        composeRule.waitForIdle()
    }

    // Z1
    @Test
    fun `deleting an open and a done task removes it and offers undo`() {
        add("Aufgabe offen")
        addDone("Aufgabe erledigt")
        launch()
        waitForItems(2)

        for (title in listOf("Aufgabe offen", "Aufgabe erledigt")) {
            deleteViaForm(title)
            waitForSnackbar()
            composeRule.waitUntil(10_000) { visibleTitles().none { it == title } }
            composeRule.onNodeWithText(deletedText(title)).assertIsDisplayed()
            undoNode().assertIsDisplayed()
            assertFalse(stored().any { it.content.title == title })
            tapUndo()
            waitForTitleInStore(title, present = true)
            composeRule.waitUntil(10_000) { visibleTitles().contains(title) }
        }
    }

    // Z2
    @Test
    fun `undo brings the task back unchanged at its old place`() {
        add("Aufgabe 1")
        val middle = add("Aufgabe 2", "Notiz zwei")
        add("Aufgabe 3")
        runBlocking { application.taskRepository.setStatus(middle.id, TaskStatus.DONE) }
        launch()
        waitForItems(3)
        val before = stored()
        val titles = visibleTitles()

        deleteViaForm("Aufgabe 2")
        waitForSnackbar()
        tapUndo()

        waitForTitles(titles)
        assertEquals(before, stored())
        assertEquals(1, stored().count { it.id == middle.id })
    }

    // Z3
    @Test
    fun `a task whose notice was swiped away stays deleted after a restart`() {
        add("Aufgabe 1")
        add("Aufgabe 2")
        launch()
        waitForItems(2)
        deleteViaForm("Aufgabe 2")
        waitForSnackbar()
        composeRule.onNodeWithTag(TaskListTags.STATUS_SNACKBAR).performTouchInput { swipeLeft() }
        composeRule.waitUntil(10_000) { count(TaskListTags.STATUS_SNACKBAR) == 0 }

        scenario!!.close()
        launch()
        waitForItems(1)

        assertEquals(listOf("Aufgabe 1"), visibleTitles())
        assertEquals(listOf("Aufgabe 1"), stored().map { it.content.title })
    }

    // Z22
    @Test
    fun `ending the app while undo is possible keeps the task deleted`() {
        add("Aufgabe 1")
        add("Aufgabe 2")
        launch()
        waitForItems(2)
        deleteViaForm("Aufgabe 2")
        waitForSnackbar()

        scenario!!.close()
        launch()
        waitForItems(1)

        assertEquals(listOf("Aufgabe 1"), visibleTitles())
        assertEquals(0, count(TaskListTags.STATUS_SNACKBAR))
    }

    // Z8
    @Test
    fun `the saved state of the activity holds no copy of the deleted task`() {
        val marker = "MARKER-Zq7k-Löschtest"
        add(marker, "MARKER-Zq7k-Notiz")
        add("Aufgabe 2")
        launch()
        waitForItems(2)
        deleteViaForm(marker)
        waitForSnackbar()

        val bundle = android.os.Bundle()
        scenario!!.onActivity {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().callActivityOnSaveInstanceState(it, bundle)
        }
        val parcel = android.os.Parcel.obtain()
        val bytes = try {
            parcel.writeBundle(bundle)
            parcel.marshall()
        } finally {
            parcel.recycle()
        }

        for (needle in listOf("MARKER-Zq7k", "Löschtest")) {
            for (charset in listOf(Charsets.UTF_8, Charsets.UTF_16LE, Charsets.UTF_16BE)) {
                assertFalse("$needle in $charset", indexOf(bytes, needle.toByteArray(charset)) >= 0)
            }
        }
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    // Z18
    @Test
    fun `an open task deleted while done tasks are hidden comes back with undo`() {
        add("Offen 1")
        add("Offen 2")
        addDone("Fertig 1")
        hideDoneBeforeStart()
        launch()
        waitForItems(2)
        val hiddenCount = application.getString(R.string.task_list_done_hidden_count, 1)
        composeRule.onNodeWithText(hiddenCount).assertIsDisplayed()

        deleteViaForm("Offen 2")
        waitForSnackbar()
        waitForTitles(listOf("Offen 1"))
        tapUndo()

        waitForTitles(listOf("Offen 2", "Offen 1"))
        composeRule.onNodeWithText(hiddenCount).assertIsDisplayed()
    }

    // Z20
    @Test
    fun `deleting the last task shows the empty state and undo brings the task back`() {
        add("Aufgabe 1")
        launch()
        waitForItems(1)

        deleteViaForm("Aufgabe 1")

        composeRule.waitUntil(10_000) { count(TaskListTags.EMPTY) == 1 }
        waitForSnackbar()
        tapUndo()
        waitForTitles(listOf("Aufgabe 1"))
        assertEquals(0, count(TaskListTags.EMPTY))
    }

    // Z23
    @Test
    fun `turning the screen while undo is possible keeps one offer and restores exactly once`() {
        add("Aufgabe 1")
        add("Aufgabe 2")
        launch()
        waitForItems(2)
        deleteViaForm("Aufgabe 2")
        waitForSnackbar()

        scenario!!.recreate()
        waitForSnackbar()
        undoNode().assertIsDisplayed()
        tapUndo()

        waitForTitles(listOf("Aufgabe 2", "Aufgabe 1"))
        assertEquals(1, stored().count { it.content.title == "Aufgabe 2" })
    }

    private fun listViewModel(): TaskListViewModel {
        lateinit var viewModel: TaskListViewModel
        scenario!!.onActivity { viewModel = androidx.lifecycle.ViewModelProvider(it)[TaskListViewModel::class.java] }
        return viewModel
    }

    // Z35
    @Test
    fun `after undo the restored task is announced and focused`() {
        add("Aufgabe 1")
        add("Aufgabe 2")
        add("Aufgabe 3")
        launch()
        waitForItems(3)

        deleteViaForm("Aufgabe 2")
        waitForSnackbar()
        tapUndo()
        waitForTitles(listOf("Aufgabe 3", "Aufgabe 2", "Aufgabe 1"))

        val announcement = { composeRule.onNodeWithTag(TaskListTags.STATUS_ANNOUNCEMENT, useUnmergedTree = true) }
        composeRule.waitUntil(10_000) {
            announcement().fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription) ==
                listOf(application.getString(R.string.task_restored_announce, "Aufgabe 2"))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Aufgabe 2").assertIsFocused()
    }

    // Z36
    @Test
    fun `a task above the visible part that is deleted and restored does not move the list`() {
        for (n in 1..30) add("Aufgabe $n")
        launch()
        waitForItems(1)
        composeRule.onNodeWithTag(TaskListTags.LIST).performScrollToIndex(12)
        composeRule.waitForIdle()
        val top = firstVisibleTitle()
        val above = stored().single { it.content.title == "Aufgabe ${top.removePrefix("Aufgabe ").toInt() + 3}" }
        val viewModel = listViewModel()

        runBlocking { application.taskRepository.delete(above.id) }
        scenario!!.onActivity { viewModel.onTaskDeleted(above) }
        waitForTitleInStore(above.content.title, present = false)
        composeRule.waitForIdle()
        assertEquals(top, firstVisibleTitle())

        scenario!!.onActivity { viewModel.onUndo(viewModel.notice.value!!) }
        waitForTitleInStore(above.content.title, present = true)
        composeRule.waitForIdle()

        assertEquals(top, firstVisibleTitle())
    }
}
