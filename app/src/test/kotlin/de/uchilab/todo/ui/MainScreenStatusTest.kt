package de.uchilab.todo.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import de.uchilab.todo.R
import de.uchilab.todo.ui.tasklist.ListNotice
import de.uchilab.todo.ui.tasklist.TaskListTags
import de.uchilab.todo.ui.tasklist.TaskListUiState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import android.view.accessibility.AccessibilityManager
import de.uchilab.todo.ui.tasklist.ListAnnouncement
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.robolectric.Shadows.shadowOf
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onAllNodesWithText
import de.uchilab.todo.domain.Task
import de.uchilab.todo.domain.TaskContent
import de.uchilab.todo.domain.TaskId
import de.uchilab.todo.domain.TaskStatus
import de.uchilab.todo.domain.Validated
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The snackbar for a failed status save lives in the main screen. Texts come from resources. */
@RunWith(RobolectricTestRunner::class)
class MainScreenStatusTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val empty = TaskListUiState.Loaded(emptyList(), emptyList())

    @Test
    fun `a failed status save shows the snackbar with the text of the design`() {
        composeRule.setContent { AppTheme { MainScreen(empty, notice = ListNotice.StatusSaveFailed) } }
        composeRule.onNodeWithTag(TaskListTags.STATUS_SNACKBAR).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.task_status_save_failed)).assertIsDisplayed()
    }

    @Test
    fun `without a failure there is no snackbar`() {
        composeRule.setContent { AppTheme { MainScreen(empty, notice = null) } }
        composeRule.onAllNodesWithTag(TaskListTags.STATUS_SNACKBAR).assertCountEquals(0)
    }

    @Test
    fun `the failure is reported as shown once the snackbar is gone`() {
        var shown = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { AppTheme { MainScreen(empty, notice = ListNotice.StatusSaveFailed, onNoticeShown = { shown++ }) } }
        composeRule.mainClock.advanceTimeBy(1_000)
        assertEquals(0, shown)
        composeRule.mainClock.advanceTimeBy(15_000)
        assertEquals(1, shown)
    }

    // Z22
    @Test
    fun `a failed setting save shows the snackbar with its text`() {
        composeRule.setContent { AppTheme { MainScreen(empty, notice = ListNotice.SettingSaveFailed) } }
        composeRule.onNodeWithTag(TaskListTags.STATUS_SNACKBAR).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.task_list_setting_save_failed)).assertIsDisplayed()
    }

    // Z8
    @Test
    fun `checking off while hidden shows the hidden notice with the title and no spoken announcement`() {
        val task = Task(TaskId(7), (TaskContent.of("Erfundene Aufgabe", null) as Validated.Valid).content, Instant.EPOCH, TaskStatus.DONE)
        val state = TaskListUiState.Loaded(listOf(openTask), listOf(task), hideDone = true)
        composeRule.setContent { AppTheme { MainScreen(state, notice = ListNotice.DoneHidden(task.id), statusAnnouncement = null) } }
        composeRule.onNodeWithTag(TaskListTags.STATUS_SNACKBAR).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.task_done_hidden, "Erfundene Aufgabe")).assertIsDisplayed()
        val announcement = composeRule.onNodeWithTag(TaskListTags.STATUS_ANNOUNCEMENT, useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(null, announcement.config.getOrNull(SemanticsProperties.ContentDescription))
    }

    @Test
    fun `a newer notice replaces the one on screen`() {
        var notice by mutableStateOf<ListNotice?>(ListNotice.StatusSaveFailed)
        composeRule.setContent { AppTheme { MainScreen(empty, notice = notice) } }
        composeRule.onNodeWithText(context.getString(R.string.task_status_save_failed)).assertIsDisplayed()
        notice = ListNotice.SettingSaveFailed
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.task_list_setting_save_failed)).assertIsDisplayed()
        composeRule.onAllNodesWithText(context.getString(R.string.task_status_save_failed)).assertCountEquals(0)
    }

    private val openTask = Task(TaskId(1), (TaskContent.of("Offen", null) as Validated.Valid).content, Instant.EPOCH, TaskStatus.OPEN)

    // --- delete with undo (S8) ---

    private val deletedTask = Task(TaskId(7), (TaskContent.of("Erfundene Aufgabe", null) as Validated.Valid).content, Instant.EPOCH, TaskStatus.OPEN)
    private val undoLabel get() = context.getString(R.string.task_undo_action)
    private val retryLabel get() = context.getString(R.string.task_restore_retry_action)

    private fun undoAction() = composeRule.onNodeWithText(undoLabel)

    // Z12
    @Test
    fun `the deleted notice names the task, announces itself and offers undo as a button`() {
        composeRule.setContent { AppTheme { MainScreen(empty, notice = ListNotice.Deleted(deletedTask)) } }
        composeRule.onNodeWithText(context.getString(R.string.task_deleted, "Erfundene Aufgabe")).assertIsDisplayed()
        val config = composeRule.onNodeWithTag(TaskListTags.STATUS_SNACKBAR).fetchSemanticsNode().config
        assertTrue(SemanticsProperties.LiveRegion in config)
        assertEquals(Role.Button, undoAction().fetchSemanticsNode().config[SemanticsProperties.Role])
    }

    // Z13
    @Test
    fun `the focus goes to the undo action and to the retry action`() {
        composeRule.setContent { AppTheme { MainScreen(empty, notice = ListNotice.Deleted(deletedTask)) } }
        undoAction().assertIsFocused()
    }

    // Z13
    @Test
    fun `a failed restore notice puts the focus on the retry action`() {
        composeRule.setContent { AppTheme { MainScreen(empty, notice = ListNotice.RestoreFailed(deletedTask)) } }
        composeRule.onNodeWithText(retryLabel).assertIsFocused()
    }

    // Z14
    @Test
    fun `the undo action is at least 48 dp high`() {
        composeRule.setContent { AppTheme { MainScreen(empty, notice = ListNotice.Deleted(deletedTask)) } }
        undoAction().assertHeightIsAtLeast(48.dp)
    }

    // Z31
    @Test
    fun `a failed restore shows its text with the title and the retry action`() {
        composeRule.setContent { AppTheme { MainScreen(empty, notice = ListNotice.RestoreFailed(deletedTask)) } }
        composeRule.onNodeWithText(context.getString(R.string.task_not_restored, "Erfundene Aufgabe")).assertIsDisplayed()
        composeRule.onNodeWithText(retryLabel).assertIsDisplayed()
    }

    @Test
    fun `the undo action reports the notice that is on screen`() {
        var undone: ListNotice? = null
        val notice = ListNotice.Deleted(deletedTask)
        composeRule.setContent { AppTheme { MainScreen(empty, notice = notice, onUndo = { undone = it }) } }
        undoAction().performClick()
        composeRule.waitForIdle()
        assertSame(notice, undone)
    }

    @Test
    fun `a long title is not cut off in the notice`() {
        val longTitle = "Erfundener sehr langer Titel ".repeat(6).trim()
        val task = Task(TaskId(8), (TaskContent.of(longTitle, null) as Validated.Valid).content, Instant.EPOCH, TaskStatus.OPEN)
        composeRule.setContent { AppTheme { MainScreen(empty, notice = ListNotice.Deleted(task)) } }
        val layout = composeRule.onNodeWithText(context.getString(R.string.task_deleted, longTitle)).fetchSemanticsNode()
        val result = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        layout.config[SemanticsActions.GetTextLayoutResult].action?.invoke(result)
        assertEquals(Int.MAX_VALUE, result.single().layoutInput.maxLines)
        assertFalse(result.single().didOverflowHeight)
    }

    // Z15
    @Test
    fun `without an accessibility service the undo action stays for 10 seconds, then the offer ends`() {
        var shown = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { AppTheme { MainScreen(empty, notice = ListNotice.Deleted(deletedTask), onNoticeShown = { shown++ }) } }
        composeRule.mainClock.advanceTimeBy(9_900)
        undoAction().assertIsDisplayed()
        assertEquals(0, shown)
        composeRule.mainClock.advanceTimeBy(1_000)
        assertEquals(1, shown)
        composeRule.onAllNodesWithText(undoLabel).assertCountEquals(0)
    }

    // Z15
    @Test
    fun `with an accessibility service the undo action stays for 30 seconds`() {
        shadowOf(context.getSystemService(AccessibilityManager::class.java)).setEnabled(true)
        var shown = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { AppTheme { MainScreen(empty, notice = ListNotice.Deleted(deletedTask), onNoticeShown = { shown++ }) } }
        composeRule.mainClock.advanceTimeBy(29_900)
        undoAction().assertIsDisplayed()
        assertEquals(0, shown)
        composeRule.mainClock.advanceTimeBy(1_000)
        assertEquals(1, shown)
    }

    // Z16
    @Test
    fun `the undo time is 10 or 30 seconds, never below, and a longer system value wins`() {
        assertEquals(10_000L, undoTimeoutMillis(false) { it })
        assertEquals(30_000L, undoTimeoutMillis(true) { it })
        assertEquals(10_000L, undoTimeoutMillis(false) { 5_000 })
        assertEquals(60_000L, undoTimeoutMillis(true) { 60_000 })
    }

    // Z17
    @Test
    fun `swiping the notice away ends the offer without undo`() {
        var shown = 0
        var undone = 0
        composeRule.setContent {
            AppTheme { MainScreen(empty, notice = ListNotice.Deleted(deletedTask), onNoticeShown = { shown++ }, onUndo = { undone++ }) }
        }
        composeRule.onNodeWithTag(TaskListTags.STATUS_SNACKBAR).performTouchInput { swipeLeft() }
        composeRule.waitUntil(5_000) { shown == 1 }
        assertEquals(0, undone)
    }

    // Z35 (spoken text)
    @Test
    fun `a restored task is announced politely with its title from the list`() {
        val state = TaskListUiState.Loaded(listOf(deletedTask), emptyList())
        composeRule.setContent { AppTheme { MainScreen(state, statusAnnouncement = ListAnnouncement.Restored(deletedTask.id)) } }
        val description = composeRule.onNodeWithTag(TaskListTags.STATUS_ANNOUNCEMENT, useUnmergedTree = true)
            .fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
        assertEquals(listOf(context.getString(R.string.task_restored_announce, "Erfundene Aufgabe")), description)
    }
}
