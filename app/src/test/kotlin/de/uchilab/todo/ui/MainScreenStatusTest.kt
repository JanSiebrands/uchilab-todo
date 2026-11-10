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
}
