package de.uchilab.todo.ui.tasklist

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule

import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsNode
import androidx.test.core.app.ApplicationProvider
import de.uchilab.todo.R
import de.uchilab.todo.domain.Task
import de.uchilab.todo.domain.TaskContent
import de.uchilab.todo.domain.TaskId
import de.uchilab.todo.domain.TaskStatus
import de.uchilab.todo.domain.Validated
import de.uchilab.todo.ui.AppTheme
import de.uchilab.todo.ui.MainScreen
import java.time.Instant
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Stateless screen tests. Texts come from the resources of the test configuration, never literal. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) // real text measuring, needed for line counts
class TaskListScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun task(id: Long, title: String, note: String? = null, status: TaskStatus = TaskStatus.OPEN): Task =
        Task(TaskId(id), (TaskContent.of(title, note) as Validated.Valid).content, Instant.EPOCH, status)

    private fun show(state: TaskListUiState) {
        composeRule.setContent {
            AppTheme { TaskListScreen(state, rememberLazyListState()) }
        }
    }

    private fun textLayout(node: SemanticsNode): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first()
    }

    private fun unmerged(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)

    private fun englishContext(): Context {
        val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) }
        return context.createConfigurationContext(configuration)
    }

    // --- entry: note below title, none without a note ---

    @Test
    fun `the note stands below the title`() {
        show(TaskListUiState.Loaded(listOf(task(1, "Titel", "Notiz"))))
        val title = unmerged(TaskListTags.ITEM_TITLE).fetchSemanticsNodes().single()
        val note = unmerged(TaskListTags.ITEM_NOTE).fetchSemanticsNodes().single()
        assertTrue(note.boundsInRoot.top >= title.boundsInRoot.bottom)
    }

    @Test
    fun `an entry without a note has no note line and is lower than one with a note`() {
        show(TaskListUiState.Loaded(listOf(task(2, "Mit", "Notiz"), task(1, "Ohne"))))
        unmerged(TaskListTags.ITEM_NOTE).assertCountEquals(1)
        val heights = composeRule.onAllNodesWithTag(TaskListTags.ITEM).fetchSemanticsNodes().map { it.size.height }
        assertEquals(2, heights.size)
        assertTrue("entry with note must be taller: $heights", heights[0] > heights[1])
    }

    @Test
    fun `an entry without a note shows its title`() {
        show(TaskListUiState.Loaded(listOf(task(1, "Nur Titel"))))
        unmerged(TaskListTags.ITEM_TITLE).assertCountEquals(1)
        unmerged(TaskListTags.ITEM_NOTE).assertCountEquals(0)
    }

    // --- empty state ---

    @Test
    fun `an empty loaded list shows the empty state with its texts`() {
        show(TaskListUiState.Loaded(emptyList()))
        composeRule.onNodeWithTag(TaskListTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.task_list_empty_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.task_list_empty_body)).assertIsDisplayed()
        composeRule.onAllNodesWithTag(TaskListTags.LIST).assertCountEquals(0)
    }

    @Test
    fun `while loading there is no empty state, no list and no error`() {
        show(TaskListUiState.Loading)
        composeRule.onAllNodesWithTag(TaskListTags.EMPTY).assertCountEquals(0)
        composeRule.onAllNodesWithTag(TaskListTags.LIST).assertCountEquals(0)
        composeRule.onAllNodesWithTag(TaskListTags.ERROR).assertCountEquals(0)
    }

    @Test
    fun `the empty state does not promise the new task action before story S4`() {
        show(TaskListUiState.Loaded(emptyList()))
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun `the empty state is scrollable`() {
        show(TaskListUiState.Loaded(emptyList()))
        composeRule.onNodeWithTag(TaskListTags.EMPTY)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
    }

    // --- read failure ---

    @Test
    fun `a read failure shows the notice instead of the empty state, announced politely, without a button`() {
        show(TaskListUiState.ReadFailed)
        composeRule.onNodeWithTag(TaskListTags.ERROR).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.task_list_error_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.task_list_error_body)).assertIsDisplayed()
        composeRule.onAllNodesWithTag(TaskListTags.EMPTY).assertCountEquals(0)
        composeRule.onAllNodesWithTag(TaskListTags.LIST).assertCountEquals(0)
        composeRule.onNodeWithTag(TaskListTags.ERROR)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun `the read failure notice is scrollable`() {
        show(TaskListUiState.ReadFailed)
        composeRule.onNodeWithTag(TaskListTags.ERROR)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
    }

    // --- TalkBack ---

    @Test
    fun `an entry is one focus target reading the title and the whole note`() {
        val title = "Titel eins"
        val note = "Notiz ".repeat(330).trim() // 1979 characters
        assertTrue(note.length in 1900..2000)
        show(TaskListUiState.Loaded(listOf(task(1, title, note))))
        val expected = context.getString(R.string.task_item_description, title, note)
        composeRule.onNodeWithTag(TaskListTags.ITEM).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(expected)),
        )
        // merged: the children are not separate targets
        composeRule.onAllNodesWithTag(TaskListTags.ITEM_TITLE).assertCountEquals(0)
        composeRule.onAllNodesWithTag(TaskListTags.ITEM_NOTE).assertCountEquals(0)
        composeRule.onAllNodesWithTag(TaskListTags.ITEM).assertCountEquals(1)
    }

    @Test
    fun `an entry without a note reads only the title`() {
        show(TaskListUiState.Loaded(listOf(task(1, "Nur Titel"))))
        composeRule.onNodeWithTag(TaskListTags.ITEM).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Nur Titel")),
        )
    }

    @Test
    fun `entries are not clickable`() {
        show(TaskListUiState.Loaded(listOf(task(1, "A", "B"))))
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    // --- languages ---

    @Test
    @Config(qualifiers = "de")
    fun `german shows the german texts`() {
        show(TaskListUiState.Loaded(emptyList()))
        val german = context.getString(R.string.task_list_empty_title)
        assertNotEquals(englishContext().getString(R.string.task_list_empty_title), german)
        composeRule.onNodeWithText(german).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en")
    fun `english shows the english texts`() {
        show(TaskListUiState.Loaded(emptyList()))
        composeRule.onNodeWithText(englishContext().getString(R.string.task_list_empty_title)).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "fr")
    fun `a language without translation falls back to english`() {
        show(TaskListUiState.Loaded(emptyList()))
        composeRule.onNodeWithText(englishContext().getString(R.string.task_list_empty_title)).assertIsDisplayed()
    }

    // --- edge cases ---

    private val longTitle = "Wort ".repeat(39) + "Worte" // 200 characters with spaces

    @Test
    fun `a 200 character title is shown completely, wrapped, and not ellipsized`() {
        assertEquals(200, longTitle.length)
        show(TaskListUiState.Loaded(listOf(task(1, longTitle))))
        val node = unmerged(TaskListTags.ITEM_TITLE).fetchSemanticsNodes().single()
        assertEquals(longTitle, node.config[SemanticsProperties.Text].joinToString(""))
        val layout = textLayout(node)
        assertTrue("title wraps over several lines", layout.lineCount > 1)
        assertTrue("title is not ellipsized", (0 until layout.lineCount).none { layout.isLineEllipsized(it) })
    }

    @Test
    fun `a 2000 character note shows at most three lines ending in an ellipsis`() {
        val note = "Notiz ".repeat(400).trim().take(2000)
        show(TaskListUiState.Loaded(listOf(task(1, "Titel", note))))
        val layout = textLayout(unmerged(TaskListTags.ITEM_NOTE).fetchSemanticsNodes().single())
        assertEquals(3, layout.lineCount)
        assertTrue(layout.isLineEllipsized(2))
    }

    @Test
    fun `a short note is not ellipsized`() {
        show(TaskListUiState.Loaded(listOf(task(1, "Titel", "kurz"))))
        val layout = textLayout(unmerged(TaskListTags.ITEM_NOTE).fetchSemanticsNodes().single())
        assertTrue((0 until layout.lineCount).none { layout.isLineEllipsized(it) })
    }

    @Test
    fun `a title and note of 200 and 2000 characters without spaces stay inside the width and need no horizontal scrolling`() {
        val title = "W".repeat(200)
        val note = "M".repeat(2000)
        show(TaskListUiState.Loaded(listOf(task(1, title, note))))
        val root = composeRule.onNodeWithTag(TaskListTags.LIST).fetchSemanticsNode().boundsInRoot
        for (node in composeRule.onAllNodesWithTag(TaskListTags.ITEM).fetchSemanticsNodes()) {
            assertTrue(node.boundsInRoot.left >= root.left && node.boundsInRoot.right <= root.right)
        }
        for (tag in listOf(TaskListTags.ITEM_TITLE, TaskListTags.ITEM_NOTE)) {
            val textNode = unmerged(tag).fetchSemanticsNodes().single()
            assertTrue(textNode.boundsInRoot.right <= root.right)
            assertTrue(textLayout(textNode).lineCount > 1)
        }
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
            .assertCountEquals(0)
    }

    @Test
    fun `hebrew, emoji, umlauts and line breaks in the note appear unchanged`() {
        val note = "שלום עולם\nÄpfel Öl Übung ß 😀👍🏽\n\nzweiter Absatz"
        show(TaskListUiState.Loaded(listOf(task(1, "Titel", note))))
        val node = unmerged(TaskListTags.ITEM_NOTE).fetchSemanticsNodes().single()
        assertEquals(note, node.config[SemanticsProperties.Text].joinToString(""))
    }

    @Test
    fun `each paragraph takes the direction of its own content`() {
        val note = "שלום עולם\nHello world\n123"
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                AppTheme { TaskListScreen(TaskListUiState.Loaded(listOf(task(1, "Titel", note))), rememberLazyListState()) }
            }
        }
        val layout = textLayout(unmerged(TaskListTags.ITEM_NOTE).fetchSemanticsNodes().single())
        assertEquals(ResolvedTextDirection.Rtl, layout.getParagraphDirection(0))
        assertEquals(ResolvedTextDirection.Ltr, layout.getParagraphDirection(note.indexOf("Hello")))
        // content decides, the layout direction does not: digits alone have no strong direction
        assertEquals(ResolvedTextDirection.Ltr, layout.getParagraphDirection(note.indexOf("123")))
    }

    @Test
    fun `done tasks look like open tasks`() {
        show(TaskListUiState.Loaded(listOf(task(2, "Fertig", "Notiz", TaskStatus.DONE), task(1, "Offen", "Notiz"))))
        val nodes = composeRule.onAllNodesWithTag(TaskListTags.ITEM).fetchSemanticsNodes()
        assertEquals(2, nodes.size)
        assertEquals(nodes[0].size.height, nodes[1].size.height)
        unmerged(TaskListTags.ITEM_NOTE).assertCountEquals(2)
    }

    // --- scroll position after process death ---

    @Test
    fun `the scroll position survives restoration even though the list is loaded after the restore`() {
        val tasks = (1L..100L).reversed().map { task(it, "Aufgabe $it") }
        var uiState: TaskListUiState by mutableStateOf(TaskListUiState.Loaded(tasks))
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { AppTheme { MainScreen(uiState) } }

        composeRule.onNodeWithTag(TaskListTags.LIST).performScrollToIndex(50)
        composeRule.onNodeWithText("Aufgabe ${tasks[50].id.value}").assertIsDisplayed()

        uiState = TaskListUiState.Loading // a restarted process starts with Loading
        composeRule.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(TaskListTags.LIST).assertCountEquals(0)

        uiState = TaskListUiState.Loaded(tasks)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Aufgabe ${tasks[50].id.value}").assertIsDisplayed()
    }
}
