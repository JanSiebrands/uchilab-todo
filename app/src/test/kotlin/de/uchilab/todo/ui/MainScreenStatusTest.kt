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
import de.uchilab.todo.ui.tasklist.TaskListTags
import de.uchilab.todo.ui.tasklist.TaskListUiState
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
        composeRule.setContent { AppTheme { MainScreen(empty, statusSaveFailed = true) } }
        composeRule.onNodeWithTag(TaskListTags.STATUS_SNACKBAR).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.task_status_save_failed)).assertIsDisplayed()
    }

    @Test
    fun `without a failure there is no snackbar`() {
        composeRule.setContent { AppTheme { MainScreen(empty, statusSaveFailed = false) } }
        composeRule.onAllNodesWithTag(TaskListTags.STATUS_SNACKBAR).assertCountEquals(0)
    }

    @Test
    fun `the failure is reported as shown once the snackbar is gone`() {
        var shown = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { AppTheme { MainScreen(empty, statusSaveFailed = true, onStatusSaveFailedShown = { shown++ }) } }
        composeRule.mainClock.advanceTimeBy(1_000)
        assertEquals(0, shown)
        composeRule.mainClock.advanceTimeBy(15_000)
        assertEquals(1, shown)
    }
}
