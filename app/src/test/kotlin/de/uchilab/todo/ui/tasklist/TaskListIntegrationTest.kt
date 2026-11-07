package de.uchilab.todo.ui.tasklist

import androidx.compose.ui.semantics.SemanticsProperties
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
import de.uchilab.todo.data.TaskDatabase
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
        composeRule.waitForIdle()
        add("Aufgabe live")
        waitForItems(1)
        assertEquals(listOf("Aufgabe live"), visibleTitles())
    }

    @Test
    fun `done tasks stay in their place`() {
        add("Aufgabe 1")
        val middle = add("Aufgabe 2")
        add("Aufgabe 3")
        runBlocking { application.taskRepository.setStatus(middle.id, TaskStatus.DONE) }
        launch()
        waitForItems(3)
        assertEquals(listOf("Aufgabe 3", "Aufgabe 2", "Aufgabe 1"), visibleTitles())
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
}
