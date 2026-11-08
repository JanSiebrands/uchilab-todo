package de.uchilab.todo.ui.tasklist

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertFalse
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
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
import androidx.compose.ui.unit.dp
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
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) // real text measuring, needed for line counts
class TaskListScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun task(id: Long, title: String, note: String? = null, status: TaskStatus = TaskStatus.OPEN): Task =
        Task(TaskId(id), (TaskContent.of(title, note) as Validated.Valid).content, Instant.EPOCH, status)

    private fun loaded(tasks: List<Task>) = tasks.groupByStatus()

    private var onStatusChange: (TaskId, TaskStatus) -> Unit = { _, _ -> }

    private fun show(state: TaskListUiState) {
        composeRule.setContent {
            AppTheme { TaskListScreen(state, rememberLazyListState(), onStatusChange) }
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
        show(loaded(listOf(task(1, "Titel", "Notiz"))))
        val title = unmerged(TaskListTags.ITEM_TITLE).fetchSemanticsNodes().single()
        val note = unmerged(TaskListTags.ITEM_NOTE).fetchSemanticsNodes().single()
        assertTrue(note.boundsInRoot.top >= title.boundsInRoot.bottom)
    }

    @Test
    fun `an entry without a note has no note line and is lower than one with a note`() {
        show(loaded(listOf(task(2, "Mit", "Notiz"), task(1, "Ohne"))))
        unmerged(TaskListTags.ITEM_NOTE).assertCountEquals(1)
        val heights = composeRule.onAllNodesWithTag(TaskListTags.ITEM).fetchSemanticsNodes().map { it.size.height }
        assertEquals(2, heights.size)
        assertTrue("entry with note must be taller: $heights", heights[0] > heights[1])
    }

    @Test
    fun `an entry without a note shows its title`() {
        show(loaded(listOf(task(1, "Nur Titel"))))
        unmerged(TaskListTags.ITEM_TITLE).assertCountEquals(1)
        unmerged(TaskListTags.ITEM_NOTE).assertCountEquals(0)
    }

    // --- empty state ---

    @Test
    fun `an empty loaded list shows the empty state with its texts`() {
        show(loaded(emptyList()))
        composeRule.onNodeWithTag(TaskListTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.task_list_empty_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.task_list_empty_body)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.task_list_empty_hint)).assertIsDisplayed()
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
    fun `the empty state list itself offers no click target, the action lives in the main screen`() {
        show(loaded(emptyList()))
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun `the empty state is scrollable`() {
        show(loaded(emptyList()))
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
        show(loaded(listOf(task(1, title, note))))
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
        show(loaded(listOf(task(1, "Nur Titel"))))
        composeRule.onNodeWithTag(TaskListTags.ITEM).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Nur Titel")),
        )
    }

    @Test
    fun `the text of an entry is not clickable, the check box is the only click target`() {
        show(loaded(listOf(task(1, "A", "B"))))
        composeRule.onNodeWithTag(TaskListTags.ITEM).assert(!SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(1)
    }

    // --- languages ---

    @Test
    @Config(qualifiers = "de")
    fun `german shows the german texts`() {
        show(loaded(emptyList()))
        val german = context.getString(R.string.task_list_empty_title)
        assertNotEquals(englishContext().getString(R.string.task_list_empty_title), german)
        composeRule.onNodeWithText(german).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en")
    fun `english shows the english texts`() {
        show(loaded(emptyList()))
        composeRule.onNodeWithText(englishContext().getString(R.string.task_list_empty_title)).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "fr")
    fun `a language without translation falls back to english`() {
        show(loaded(emptyList()))
        composeRule.onNodeWithText(englishContext().getString(R.string.task_list_empty_title)).assertIsDisplayed()
    }

    // --- edge cases ---

    private val longTitle = "Wort ".repeat(39) + "Worte" // 200 characters with spaces

    @Test
    fun `a 200 character title is shown completely, wrapped, and not ellipsized`() {
        assertEquals(200, longTitle.length)
        show(loaded(listOf(task(1, longTitle))))
        val node = unmerged(TaskListTags.ITEM_TITLE).fetchSemanticsNodes().single()
        assertEquals(longTitle, node.config[SemanticsProperties.Text].joinToString(""))
        val layout = textLayout(node)
        assertTrue("title wraps over several lines", layout.lineCount > 1)
        assertTrue("title is not ellipsized", (0 until layout.lineCount).none { layout.isLineEllipsized(it) })
    }

    @Test
    fun `a 2000 character note shows at most three lines ending in an ellipsis`() {
        val note = "Notiz ".repeat(400).trim().take(2000)
        show(loaded(listOf(task(1, "Titel", note))))
        val layout = textLayout(unmerged(TaskListTags.ITEM_NOTE).fetchSemanticsNodes().single())
        assertEquals(3, layout.lineCount)
        assertTrue(layout.isLineEllipsized(2))
    }

    @Test
    fun `a short note is not ellipsized`() {
        show(loaded(listOf(task(1, "Titel", "kurz"))))
        val layout = textLayout(unmerged(TaskListTags.ITEM_NOTE).fetchSemanticsNodes().single())
        assertTrue((0 until layout.lineCount).none { layout.isLineEllipsized(it) })
    }

    @Test
    fun `a title and note of 200 and 2000 characters without spaces stay inside the width and need no horizontal scrolling`() {
        val title = "W".repeat(200)
        val note = "M".repeat(2000)
        show(loaded(listOf(task(1, title, note))))
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
        show(loaded(listOf(task(1, "Titel", note))))
        val node = unmerged(TaskListTags.ITEM_NOTE).fetchSemanticsNodes().single()
        assertEquals(note, node.config[SemanticsProperties.Text].joinToString(""))
    }

    @Test
    fun `each paragraph takes the direction of its own content`() {
        val note = "שלום עולם\nHello world\n123"
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                AppTheme { TaskListScreen(loaded(listOf(task(1, "Titel", note))), rememberLazyListState(), onStatusChange) }
            }
        }
        val layout = textLayout(unmerged(TaskListTags.ITEM_NOTE).fetchSemanticsNodes().single())
        assertEquals(ResolvedTextDirection.Rtl, layout.getParagraphDirection(0))
        assertEquals(ResolvedTextDirection.Ltr, layout.getParagraphDirection(note.indexOf("Hello")))
        // content decides, the layout direction does not: digits alone have no strong direction
        assertEquals(ResolvedTextDirection.Ltr, layout.getParagraphDirection(note.indexOf("123")))
    }

    @Test
    fun `a done entry is as high as an open one with the same content`() {
        show(loaded(listOf(task(2, "Fertig", "Notiz", TaskStatus.DONE), task(1, "Offen", "Notiz"))))
        val nodes = composeRule.onAllNodesWithTag(TaskListTags.ITEM).fetchSemanticsNodes()
        assertEquals(2, nodes.size)
        assertEquals(nodes[0].size.height, nodes[1].size.height)
        unmerged(TaskListTags.ITEM_NOTE).assertCountEquals(2)
    }

    // --- structure: headings, decorative signs, size, spacing, keys ---

    private fun isHeading() = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    @Test
    fun `the bar title is a heading`() {
        composeRule.setContent { AppTheme { MainScreen(loaded(emptyList())) } }
        composeRule.onNodeWithText(context.getString(R.string.app_name)).assert(isHeading())
    }

    @Test
    fun `the title of the empty state is a heading`() {
        show(loaded(emptyList()))
        composeRule.onNodeWithText(context.getString(R.string.task_list_empty_title)).assert(isHeading())
    }

    @Test
    fun `the title of the read failure notice is a heading`() {
        show(TaskListUiState.ReadFailed)
        composeRule.onNodeWithText(context.getString(R.string.task_list_error_title)).assert(isHeading())
    }

    private fun assertNoDescriptionInUnmergedTree() {
        composeRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
            useUnmergedTree = true,
        ).assertCountEquals(0)
    }

    @Test
    fun `the sign of the empty state is hidden from TalkBack`() {
        show(loaded(emptyList()))
        composeRule.onNodeWithTag(TaskListTags.EMPTY).assertIsDisplayed()
        assertNoDescriptionInUnmergedTree()
    }

    @Test
    fun `the sign of the read failure notice is hidden from TalkBack`() {
        show(TaskListUiState.ReadFailed)
        composeRule.onNodeWithTag(TaskListTags.ERROR).assertIsDisplayed()
        assertNoDescriptionInUnmergedTree()
    }

    private fun countPixelsOfThemeColor(state: TaskListUiState, pick: (androidx.compose.material3.ColorScheme) -> Color): Pair<Int, Int> {
        var expected = Color.Unspecified
        composeRule.setContent {
            AppTheme {
                expected = pick(MaterialTheme.colorScheme)
                TaskListScreen(state, rememberLazyListState(), onStatusChange)
            }
        }
        val image = composeRule.onNodeWithTag(TaskListTags.SIGN).captureToImage()
        val pixels = IntArray(image.width * image.height)
        image.readPixels(pixels)
        val target = expected.toArgb()
        return pixels.count { it == target } to pixels.size
    }

    @Test
    fun `the sign of the empty state is drawn in the colour of the theme`() {
        val (matching, total) = countPixelsOfThemeColor(loaded(emptyList())) { it.onSurfaceVariant }
        assertTrue("only $matching of $total pixels have the theme colour", matching * 100 >= total * 5)
    }

    @Test
    fun `the sign of the read failure notice is drawn in the colour of the theme`() {
        val (matching, total) = countPixelsOfThemeColor(TaskListUiState.ReadFailed) { it.error }
        assertTrue("only $matching of $total pixels have the theme colour", matching * 100 >= total * 5)
    }

    @Test
    fun `an entry with a short title is at least 56 dp high`() {
        show(loaded(listOf(task(1, "A"))))
        val minimum = with(composeRule.density) { 56.dp.roundToPx() }
        val height = composeRule.onNodeWithTag(TaskListTags.ITEM).fetchSemanticsNode().size.height
        assertTrue("height $height below $minimum", height >= minimum)
    }

    @Test
    fun `the last entry ends at least 96 dp above the bottom of the list`() {
        val tasks = (30L downTo 1L).map { task(it, "Aufgabe $it") }
        show(loaded(tasks))
        composeRule.onNodeWithTag(TaskListTags.LIST).performScrollToIndex(tasks.lastIndex)
        composeRule.waitForIdle()
        val list = composeRule.onNodeWithTag(TaskListTags.LIST).fetchSemanticsNode().boundsInRoot
        val last = composeRule.onAllNodesWithTag(TaskListTags.ITEM).fetchSemanticsNodes()
            .maxOf { it.boundsInRoot.bottom }
        val space = with(composeRule.density) { 96.dp.toPx() }
        assertTrue("gap ${list.bottom - last} below $space", list.bottom - last >= space)
    }

    @Test
    fun `a task added on top does not move the entry the user is looking at`() {
        val old = (20L downTo 1L).map { task(it, "Aufgabe $it") }
        var tasks by mutableStateOf(old)
        lateinit var listState: androidx.compose.foundation.lazy.LazyListState
        composeRule.setContent {
            listState = rememberLazyListState()
            AppTheme { TaskListScreen(loaded(tasks), listState, onStatusChange) }
        }
        composeRule.onNodeWithTag(TaskListTags.LIST).performScrollToIndex(8)
        composeRule.waitForIdle()
        val watched = old[listState.firstVisibleItemIndex].id

        tasks = listOf(task(21, "Neu")) + old
        composeRule.waitForIdle()

        assertEquals(watched, tasks[listState.firstVisibleItemIndex].id)
    }

    // --- scroll position after process death ---

    @Test
    fun `the scroll position survives restoration even though the list is loaded after the restore`() {
        val tasks = (1L..100L).reversed().map { task(it, "Aufgabe $it") }
        var uiState: TaskListUiState by mutableStateOf(loaded(tasks))
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { AppTheme { MainScreen(uiState) } }

        composeRule.onNodeWithTag(TaskListTags.LIST).performScrollToIndex(50)
        composeRule.onNodeWithText("Aufgabe ${tasks[50].id.value}").assertIsDisplayed()

        uiState = TaskListUiState.Loading // a restarted process starts with Loading
        composeRule.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(TaskListTags.LIST).assertCountEquals(0)

        uiState = loaded(tasks)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Aufgabe ${tasks[50].id.value}").assertIsDisplayed()
    }

    // --- check box and done group (S6) ---

    private fun checkboxes() = composeRule.onAllNodesWithTag(TaskListTags.ITEM_CHECKBOX)

    @Test
    fun `the check box tells which task and whether it is open or done, as a check box`() {
        show(loaded(listOf(task(2, "Fertig", status = TaskStatus.DONE), task(1, "Offen"))))
        val nodes = checkboxes().fetchSemanticsNodes()
        assertEquals(2, nodes.size)
        val open = nodes.single { it.config[SemanticsProperties.ContentDescription] == listOf(context.getString(R.string.task_status_of, "Offen")) }
        val done = nodes.single { it.config[SemanticsProperties.ContentDescription] == listOf(context.getString(R.string.task_status_of, "Fertig")) }
        assertEquals(context.getString(R.string.task_status_open), open.config[SemanticsProperties.StateDescription])
        assertEquals(context.getString(R.string.task_status_done), done.config[SemanticsProperties.StateDescription])
        assertEquals(Role.Checkbox, open.config[SemanticsProperties.Role])
        assertEquals(ToggleableState.Off, open.config[SemanticsProperties.ToggleableState])
        assertEquals(ToggleableState.On, done.config[SemanticsProperties.ToggleableState])
    }

    @Test
    fun `an entry has two focus targets, the check box and the merged text, done adds the state to the text`() {
        show(loaded(listOf(task(2, "Fertig", "Notiz", TaskStatus.DONE), task(1, "Offen", "Notiz"))))
        checkboxes().assertCountEquals(2)
        val texts = composeRule.onAllNodesWithTag(TaskListTags.ITEM).fetchSemanticsNodes()
        assertEquals(2, texts.size)
        val open = texts[0].config
        val done = texts[1].config
        assertEquals(listOf(context.getString(R.string.task_item_description, "Fertig", "Notiz")), done[SemanticsProperties.ContentDescription])
        assertEquals(context.getString(R.string.task_status_done), done[SemanticsProperties.StateDescription])
        assertEquals(listOf(context.getString(R.string.task_item_description, "Offen", "Notiz")), open[SemanticsProperties.ContentDescription])
        assertFalse(open.contains(SemanticsProperties.StateDescription))
    }

    @Test
    fun `tapping the check box asks for the opposite status`() {
        val calls = mutableListOf<Pair<TaskId, TaskStatus>>()
        onStatusChange = { id, status -> calls += id to status }
        show(loaded(listOf(task(2, "Fertig", status = TaskStatus.DONE), task(1, "Offen"))))
        checkboxes()[0].performClick() // open entries come first
        checkboxes()[1].performClick()
        assertEquals(listOf(TaskId(1) to TaskStatus.DONE, TaskId(2) to TaskStatus.OPEN), calls)
    }

    @Test
    fun `tapping the text of an entry changes nothing`() {
        val calls = mutableListOf<Pair<TaskId, TaskStatus>>()
        onStatusChange = { id, status -> calls += id to status }
        show(loaded(listOf(task(1, "Offen", "Notiz"))))
        unmerged(TaskListTags.ITEM_TITLE)[0].performClick()
        unmerged(TaskListTags.ITEM_NOTE)[0].performClick()
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `only done tasks show the list with the heading on top and no empty state`() {
        show(loaded(listOf(task(2, "Zwei", status = TaskStatus.DONE), task(1, "Eins", status = TaskStatus.DONE))))
        composeRule.onAllNodesWithTag(TaskListTags.EMPTY).assertCountEquals(0)
        val heading = composeRule.onNodeWithTag(TaskListTags.DONE_HEADING).fetchSemanticsNode()
        val firstEntry = composeRule.onAllNodesWithTag(TaskListTags.ITEM).fetchSemanticsNodes().minOf { it.boundsInRoot.top }
        assertTrue(heading.boundsInRoot.bottom <= firstEntry)
    }

    @Test
    fun `the done heading stands between open and done entries`() {
        show(loaded(listOf(task(3, "Offen"), task(2, "Fertig", status = TaskStatus.DONE))))
        val heading = composeRule.onNodeWithTag(TaskListTags.DONE_HEADING).fetchSemanticsNode().boundsInRoot
        val entries = composeRule.onAllNodesWithTag(TaskListTags.ITEM).fetchSemanticsNodes().sortedBy { it.boundsInRoot.top }
        assertTrue(entries[0].boundsInRoot.bottom <= heading.top)
        assertTrue(heading.bottom <= entries[1].boundsInRoot.top)
    }

    @Test
    fun `without done tasks there is no done heading`() {
        show(loaded(listOf(task(1, "Offen"))))
        composeRule.onAllNodesWithTag(TaskListTags.DONE_HEADING).assertCountEquals(0)
    }

    @Test
    fun `the done heading is a heading with the heading text and cannot be tapped`() {
        show(loaded(listOf(task(1, "Fertig", status = TaskStatus.DONE))))
        composeRule.onNodeWithTag(TaskListTags.DONE_HEADING).assert(isHeading())
            .assert(!SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
        composeRule.onNodeWithText(context.getString(R.string.task_list_done_heading)).assertIsDisplayed()
    }

    @Test
    fun `with a long title and 200 percent text the check box stays reachable, at least 48 dp, left of the title`() {
        val calls = mutableListOf<TaskId>()
        onStatusChange = { id, _ -> calls += id }
        show200Percent(loaded(listOf(task(1, longTitle))))
        val box = checkboxes()[0]
        box.assertIsDisplayed()
        // the node itself is the 24 dp box; the area that reacts to a tap is the 48 dp one around it
        val touch = box.fetchSemanticsNode().touchBoundsInRoot
        val minimum = with(composeRule.density) { 48.dp.toPx() }
        assertTrue("touch area ${touch.width} x ${touch.height} below $minimum", touch.width >= minimum && touch.height >= minimum)
        val boxBounds = box.fetchSemanticsNode().boundsInRoot
        val title = unmerged(TaskListTags.ITEM_TITLE).fetchSemanticsNodes().single().boundsInRoot
        assertTrue(boxBounds.right <= title.left)
        checkboxes()[0].performClick()
        assertEquals(listOf(TaskId(1)), calls)
    }

    private fun show200Percent(state: TaskListUiState) {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                AppTheme { TaskListScreen(state, rememberLazyListState(), onStatusChange) }
            }
        }
    }
}
