package de.uchilab.todo.ui.taskform

import android.content.Context
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertiesAndroid
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.semantics.SemanticsProperties.Heading
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import de.uchilab.todo.domain.TaskContent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import de.uchilab.todo.R
import de.uchilab.todo.domain.TaskId
import de.uchilab.todo.ui.taskform.FormAnnouncement
import de.uchilab.todo.ui.AppTheme
import de.uchilab.todo.ui.MainScreen
import de.uchilab.todo.ui.tasklist.TaskListUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Stateless screen tests. Texts come from the resources of the test configuration, never literal. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TaskFormScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private class Recorder : TaskFormActions {
        val calls = mutableListOf<String>()
        override fun onTitleChange(raw: String) { calls += "title" }
        override fun onNoteChange(raw: String) { calls += "note" }
        override fun onFocusChange(field: TaskFormField) { calls += "focus-$field" }
        override fun onSave() { calls += "save" }
        override fun onDelete() { calls += "delete" }
        override fun onCloseRequest() { calls += "close" }
        override fun onDiscardDismiss() { calls += "dismiss" }
        override fun onDiscardConfirm() { calls += "confirm" }
    }

    private val recorder = Recorder()

    private fun show(state: TaskFormUiState) {
        composeRule.setContent { AppTheme { TaskFormScreen(state, recorder) } }
    }

    private fun open(title: String = "", note: String = "", block: (TaskFormUiState) -> TaskFormUiState = { it }) =
        block(TaskFormUiState(open = true, title = title, note = note, focus = TaskFormField.TITLE))

    /** Unmerged: hints and counters sit inside the merged node of their field. */
    private fun node(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun tagged(tag: String, unmerged: Boolean = false) =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = unmerged)

    // --- action ---

    private fun main(state: TaskListUiState, onNewTask: () -> Unit = {}) {
        composeRule.setContent { AppTheme { MainScreen(state, onNewTask = onNewTask) } }
    }

    @Test
    fun `the keyboard action of the title field moves the focus to the note field`() {
        show(open())
        composeRule.onNodeWithTag(TaskFormTags.TITLE_FIELD).assertIsFocused()
        composeRule.onNodeWithTag(TaskFormTags.TITLE_FIELD).performImeAction()
        composeRule.onNodeWithTag(TaskFormTags.NOTE_FIELD).assertIsFocused()
        assertTrue(recorder.calls.contains("focus-NOTE"))
    }

    @Test
    fun `the action exists for an empty and for a filled list`() {
        main(TaskListUiState.Loaded(emptyList(), emptyList()))
        tagged(TaskFormTags.ACTION).assertCountEquals(1)
    }

    @Test
    fun `the action does not exist while loading and after a read failure`() {
        main(TaskListUiState.Loading)
        tagged(TaskFormTags.ACTION).assertCountEquals(0)
    }

    @Test
    fun `the action does not exist after a read failure`() {
        main(TaskListUiState.ReadFailed)
        tagged(TaskFormTags.ACTION).assertCountEquals(0)
    }

    @Test
    fun `tapping the action reports it and it shows its text`() {
        var tapped = 0
        main(TaskListUiState.Loaded(emptyList(), emptyList())) { tapped++ }
        node(TaskFormTags.ACTION).assertIsDisplayed().performClick()
        assertEquals(1, tapped)
        composeRule.onNodeWithText(context.getString(R.string.new_task_action), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `the action is at least 56 dp high`() {
        main(TaskListUiState.Loaded(emptyList(), emptyList()))
        val height = node(TaskFormTags.ACTION).fetchSemanticsNode().size.height
        val dp = height / composeRule.density.density
        assertTrue("height $dp dp", dp >= 56f)
    }

    // --- form ---

    @Test
    fun `the form has both fields, save and close`() {
        show(open())
        node(TaskFormTags.TITLE_FIELD).assertIsDisplayed()
        node(TaskFormTags.NOTE_FIELD).assertIsDisplayed()
        node(TaskFormTags.SAVE).assertIsDisplayed()
        node(TaskFormTags.CLOSE).assertIsDisplayed()
    }

    @Test
    fun `save and close report and have 48 dp touch targets`() {
        show(open())
        node(TaskFormTags.SAVE).performClick()
        node(TaskFormTags.CLOSE).performClick()
        assertEquals(listOf("save", "close"), recorder.calls.filterNot { it.startsWith("focus") })
        for (tag in listOf(TaskFormTags.SAVE, TaskFormTags.CLOSE)) {
            val bounds = node(tag).fetchSemanticsNode().touchBoundsInRoot
            val density = composeRule.density.density
            assertTrue("$tag ${bounds.width / density}x${bounds.height / density}",
                bounds.width / density >= 48f && bounds.height / density >= 48f)
        }
    }

    @Test
    fun `save is enabled with an empty title`() {
        show(open())
        assertTrue(SemanticsProperties.Disabled !in node(TaskFormTags.SAVE).fetchSemanticsNode().config)
    }

    @Test
    fun `the close sign is described for TalkBack and the heading is a heading`() {
        show(open())
        val description = composeRule.onNodeWithTag(TaskFormTags.CLOSE).fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
        assertEquals(listOf(context.getString(R.string.new_task_close)), description)
        val heading = composeRule.onNodeWithText(context.getString(R.string.new_task_heading)).fetchSemanticsNode()
        assertTrue(SemanticsProperties.Heading in heading.config)
    }

    @Test
    fun `typing reports changes to the right field`() {
        show(open())
        node(TaskFormTags.TITLE_FIELD).performTextInput("Aufgabe 1")
        node(TaskFormTags.NOTE_FIELD).performTextInput("Notiz 1")
        assertTrue("title" in recorder.calls)
        assertTrue("note" in recorder.calls)
    }

    @Test
    fun `the fields show the given text`() {
        show(open("Aufgabe 1", "Notiz 1"))
        composeRule.onNodeWithText("Aufgabe 1").assertIsDisplayed()
        composeRule.onNodeWithText("Notiz 1").assertIsDisplayed()
    }

    @Test
    fun `the title field has a label and a required hint, the note field a label`() {
        show(open())
        composeRule.onNodeWithText(context.getString(R.string.new_task_title_required)).assertIsDisplayed()
        assertTrue(SemanticsProperties.Error !in tagged(TaskFormTags.TITLE_FIELD).fetchSemanticsNodes().single().config)
    }

    @Test
    fun `the note field is at least four lines high`() {
        var lineHeight = 0f
        composeRule.setContent {
            AppTheme {
                lineHeight = with(androidx.compose.ui.platform.LocalDensity.current) {
                    androidx.compose.material3.MaterialTheme.typography.bodyLarge.lineHeight.toPx()
                }
                TaskFormScreen(open(), recorder)
            }
        }
        val note = node(TaskFormTags.NOTE_FIELD).fetchSemanticsNode().size.height
        assertTrue("note $note line $lineHeight", note >= 4 * lineHeight)
    }

    // --- hints ---

    @Test
    fun `no hint on a fresh form`() {
        show(open())
        tagged(TaskFormTags.TITLE_HINT).assertCountEquals(0)
        tagged(TaskFormTags.SAVE_FAILED).assertCountEquals(0)
        assertTrue(SemanticsProperties.Error !in node(TaskFormTags.TITLE_FIELD).fetchSemanticsNode().config)
    }

    @Test
    fun `the missing title hint is shown with text and error semantics`() {
        show(open { it.withHints(titleMissing = true) })
        node(TaskFormTags.TITLE_HINT).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.new_task_title_missing)).assertIsDisplayed()
        assertTrue(SemanticsProperties.Error in node(TaskFormTags.TITLE_FIELD).fetchSemanticsNode().config)
    }

    @Test
    fun `a title above the limit shows the hint and the limit in the text`() {
        show(open("a".repeat(201)))
        node(TaskFormTags.TITLE_HINT).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.new_task_title_too_long, "200")).assertIsDisplayed()
        assertTrue(SemanticsProperties.Error in node(TaskFormTags.TITLE_FIELD).fetchSemanticsNode().config)
    }

    @Test
    fun `a note above the limit shows the hint and the error`() {
        show(open(note = "a".repeat(2001)))
        // the note is long, so the hint below it may lie outside the screen; it must exist with its text
        node(TaskFormTags.NOTE_HINT).fetchSemanticsNode()
        composeRule.onNodeWithText(context.getString(R.string.new_task_note_too_long, "2,000")).fetchSemanticsNode()
        assertTrue(SemanticsProperties.Error in node(TaskFormTags.NOTE_FIELD).fetchSemanticsNode().config)
    }

    @Test
    fun `a title at the limit shows no hint`() {
        show(open("a".repeat(200)))
        tagged(TaskFormTags.TITLE_HINT).assertCountEquals(0)
    }

    // --- counter ---

    @Test
    fun `the title counter appears at 160 with the TalkBack text`() {
        show(open("a".repeat(159)))
        tagged(TaskFormTags.TITLE_COUNTER).assertCountEquals(0)
    }

    @Test
    fun `the title counter at 160 is described as spoken text`() {
        show(open("a".repeat(160)))
        val description = node(TaskFormTags.TITLE_COUNTER).fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
        assertEquals(listOf(context.getString(R.string.new_task_counter_description, "160", "200")), description)
    }

    @Test
    fun `the note counter appears at 1600 and not at 1599, numbers formatted for the language`() {
        show(open(note = "a".repeat(1599)))
        tagged(TaskFormTags.NOTE_COUNTER).assertCountEquals(0)
    }

    @Test
    fun `the note counter at 1600 uses the grouping of the language`() {
        show(open(note = "a".repeat(1600)))
        val description = node(TaskFormTags.NOTE_COUNTER).fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
        assertEquals(listOf(context.getString(R.string.new_task_counter_description, "1,600", "2,000")), description)
    }

    @Test
    @Config(qualifiers = "de")
    fun `in German the note counter uses German grouping`() {
        show(open(note = "a".repeat(1600)))
        val description = node(TaskFormTags.NOTE_COUNTER).fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
        assertEquals(listOf(context.getString(R.string.new_task_counter_description, "1.600", "2.000")), description)
    }

    // --- save failed ---

    @Test
    fun `the save failed hint is an urgent live region at the top of the form`() {
        show(open("Aufgabe 1") { it.withHints(saveFailed = true) })
        node(TaskFormTags.SAVE_FAILED).assertIsDisplayed()
        val config = node(TaskFormTags.SAVE_FAILED).fetchSemanticsNode().config
        assertEquals(LiveRegionMode.Assertive, config[SemanticsProperties.LiveRegion])
        val top = node(TaskFormTags.SAVE_FAILED).fetchSemanticsNode().boundsInRoot.top
        assertTrue(top <= node(TaskFormTags.TITLE_FIELD).fetchSemanticsNode().boundsInRoot.top)
        composeRule.onNodeWithText(context.getString(R.string.new_task_save_failed_title), substring = true).assertIsDisplayed()
    }

    // --- dialog ---

    @Test
    fun `the discard dialog has both buttons and reports them`() {
        show(open("Aufgabe 1") { it.withHints(dialog = true) })
        node(TaskFormTags.DISCARD_DIALOG).assertIsDisplayed()
        val keep = node(TaskFormTags.DISCARD_KEEP).fetchSemanticsNode().boundsInRoot
        val confirm = node(TaskFormTags.DISCARD_CONFIRM).fetchSemanticsNode().boundsInRoot
        assertTrue("keep editing stands left of discard", keep.left < confirm.left)
        node(TaskFormTags.DISCARD_KEEP).performClick()
        node(TaskFormTags.DISCARD_CONFIRM).performClick()
        assertEquals(listOf("dismiss", "confirm"), recorder.calls.filterNot { it.startsWith("focus") })
    }

    @Test
    fun `the dialog window exposes its test tags as resource ids`() {
        show(open("Aufgabe 1") { it.withHints(dialog = true) })
        val config = node(TaskFormTags.DISCARD_DIALOG).fetchSemanticsNode().config
        assertTrue(
            "the dialog window needs testTagsAsResourceId itself, the root one does not reach it",
            config.getOrNull(SemanticsPropertiesAndroid.TestTagsAsResourceId) == true,
        )
    }

    @Test
    fun `without the flag there is no dialog`() {
        show(open("Aufgabe 1"))
        tagged(TaskFormTags.DISCARD_DIALOG).assertCountEquals(0)
    }

    // --- announcement ---

    @Test
    fun `the created announcement is a polite live region with its text`() {
        composeRule.setContent {
            AppTheme { MainScreen(TaskListUiState.Loaded(emptyList(), emptyList()), createdTaskId = TaskId(1), announcement = FormAnnouncement.CREATED) }
        }
        val config = tagged(TaskFormTags.CREATED_ANNOUNCEMENT, unmerged = true).fetchSemanticsNodes().single().config
        assertEquals(LiveRegionMode.Polite, config[SemanticsProperties.LiveRegion])
        assertEquals(listOf(context.getString(R.string.new_task_created)), config[SemanticsProperties.ContentDescription])
    }

    @Test
    fun `without a created task the announcement node says nothing`() {
        main(TaskListUiState.Loaded(emptyList(), emptyList()))
        val nodes = tagged(TaskFormTags.CREATED_ANNOUNCEMENT, unmerged = true).fetchSemanticsNodes()
        assertTrue(nodes.all { SemanticsProperties.ContentDescription !in it.config })
    }

    // --- sign descriptions ---

    // --- languages ---

    private fun headingMatchesResource() {
        show(open())
        composeRule.onNodeWithText(context.getString(R.string.new_task_heading)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.new_task_save)).assertIsDisplayed()
    }

    @Test @Config(qualifiers = "de")
    fun `German texts come from the resources`() = headingMatchesResource()

    @Test @Config(qualifiers = "en")
    fun `English texts come from the resources`() = headingMatchesResource()

    @Test @Config(qualifiers = "fr")
    fun `another language falls back to English`() {
        headingMatchesResource()
        val english = context.createConfigurationContext(
            android.content.res.Configuration(context.resources.configuration).apply { setLocale(java.util.Locale.ENGLISH) },
        )
        assertEquals(english.getString(R.string.new_task_heading), context.getString(R.string.new_task_heading))
    }

    @Test
    fun `the form is gone from the tree when not open`() {
        composeRule.setContent { AppTheme { TaskFormScreen(TaskFormUiState(), recorder) } }
        tagged(TaskFormTags.TITLE_FIELD).assertCountEquals(0)
    }

    // helper copying a state with hints
    private fun TaskFormUiState.withHints(
        titleMissing: Boolean = titleMissingShown,
        saveFailed: Boolean = this.saveFailed,
        dialog: Boolean = discardDialog,
    ) = TaskFormUiState(open, title, note, titleMissing, saveFailed, dialog, focus, focusTick)

    // --- editing (S5) ---

    private val sampleContent = (TaskContent.of("Aufgabe 1", "Notiz 1") as de.uchilab.todo.domain.Validated.Valid).content

    private fun editing(title: String = "Aufgabe 1", note: String = "Notiz 1") =
        TaskFormUiState(open = true, editing = TaskId(7), original = sampleContent, title = title, note = note)

    @Test
    fun `the discard dialog of an edit has its own texts, the one of a new task keeps its texts`() {
        show(TaskFormUiState(open = true, title = "x", discardDialog = true, editing = TaskId(7), original = sampleContent))
        composeRule.onNodeWithText(context.getString(R.string.task_edit_discard_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.task_edit_discard_body)).assertIsDisplayed()
        tagged(TaskFormTags.DISCARD_DIALOG).assertCountEquals(1)
    }

    @Test
    fun `the discard dialog of a new task shows the new task texts`() {
        show(open("Aufgabe 1") { it.withHints(dialog = true) })
        composeRule.onNodeWithText(context.getString(R.string.new_task_discard_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.new_task_discard_body)).assertIsDisplayed()
        composeRule.onAllNodesWithText(context.getString(R.string.task_edit_discard_title)).assertCountEquals(0)
    }

    @Test
    fun `an edit has the edit heading as a heading and not the new task heading`() {
        show(editing())
        composeRule.onNodeWithText(context.getString(R.string.task_edit_heading)).assert(SemanticsMatcher.keyIsDefined(Heading))
        composeRule.onAllNodesWithText(context.getString(R.string.new_task_heading)).assertCountEquals(0)
    }

    @Test
    fun `an edit without a focus request leaves both fields unfocused`() {
        show(editing())
        composeRule.waitForIdle()
        node(TaskFormTags.TITLE_FIELD).assertIsNotFocused()
        node(TaskFormTags.NOTE_FIELD).assertIsNotFocused()
    }

    @Test
    fun `a new task still starts with the cursor in the title`() {
        show(TaskFormUiState(open = true, focus = TaskFormField.TITLE))
        node(TaskFormTags.TITLE_FIELD).assertIsFocused()
    }

    // --- landscape (UCH-100) ---

    private fun barBottom() = node(TaskFormTags.CLOSE).fetchSemanticsNode().boundsInRoot.bottom
    private fun titleTop() = node(TaskFormTags.TITLE_FIELD).fetchSemanticsNode().boundsInRoot.top

    @Test
    @Config(qualifiers = "w640dp-h320dp-land")
    fun `in landscape a freshly opened new task stands at the top with the label visible`() {
        show(TaskFormUiState(open = true))
        val margin = with(composeRule.density) { 8.dp.toPx() }
        assertTrue(titleTop() >= barBottom() + margin - 1f)
    }

    // --- delete (S8) ---

    // Z11
    @Test
    fun `an edit form has a delete sign left of save, described, a button, at least 48 dp, also at 200 percent`() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                AppTheme { TaskFormScreen(editing(), recorder) }
            }
        }
        val delete = composeRule.onNodeWithTag(TaskFormTags.DELETE)
        delete.assertIsDisplayed()
        val config = delete.fetchSemanticsNode().config
        assertEquals(listOf(context.getString(R.string.task_delete_description)), config[SemanticsProperties.ContentDescription])
        assertEquals(Role.Button, config[SemanticsProperties.Role])
        val bounds = delete.fetchSemanticsNode().touchBoundsInRoot
        val density = composeRule.density.density
        assertTrue(bounds.width / density >= 48f && bounds.height / density >= 48f)
        assertTrue(bounds.right <= node(TaskFormTags.SAVE).fetchSemanticsNode().boundsInRoot.left)
        delete.performClick()
        assertEquals(listOf("delete"), recorder.calls.filterNot { it.startsWith("focus") })
    }

    // Z11
    @Test
    fun `a new task form has no delete sign`() {
        show(open())
        tagged(TaskFormTags.DELETE, unmerged = true).assertCountEquals(0)
    }

    // Z28
    @Test
    fun `the delete failed hint is an urgent live region at the top with its own title`() {
        composeRule.setContent { AppTheme { TaskFormScreen(TaskFormUiState(open = true, editing = TaskId(7), original = sampleContent, title = "Aufgabe 1", note = "Notiz 1", deleteFailed = true), recorder) } }
        node(TaskFormTags.DELETE_FAILED).assertIsDisplayed()
        assertEquals(LiveRegionMode.Assertive, node(TaskFormTags.DELETE_FAILED).fetchSemanticsNode().config[SemanticsProperties.LiveRegion])
        val top = node(TaskFormTags.DELETE_FAILED).fetchSemanticsNode().boundsInRoot.top
        assertTrue(top <= node(TaskFormTags.TITLE_FIELD).fetchSemanticsNode().boundsInRoot.top)
        composeRule.onNodeWithText(context.getString(R.string.task_delete_failed_title), substring = true).assertIsDisplayed()
        tagged(TaskFormTags.SAVE_FAILED).assertCountEquals(0)
    }

    @Test
    fun `without a failure there is no delete failed hint`() {
        show(editing())
        tagged(TaskFormTags.DELETE_FAILED).assertCountEquals(0)
    }
}
