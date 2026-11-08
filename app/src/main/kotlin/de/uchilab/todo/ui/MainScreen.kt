package de.uchilab.todo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.uchilab.todo.R
import de.uchilab.todo.domain.TaskId
import de.uchilab.todo.ui.newtask.NewTaskActions
import de.uchilab.todo.ui.newtask.NewTaskField
import de.uchilab.todo.ui.newtask.NewTaskScreen
import de.uchilab.todo.ui.newtask.NewTaskTags
import de.uchilab.todo.ui.newtask.NewTaskUiState
import de.uchilab.todo.ui.tasklist.TaskListScreen
import de.uchilab.todo.ui.tasklist.TaskListUiState

/**
 * Top bar with the working title, the task list and the "New task" action, or the input screen while it is open.
 * [listState] lives outside the branch so the scroll position stays while the input is open.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun MainScreen(
    state: TaskListUiState,
    modifier: Modifier = Modifier,
    newTask: NewTaskUiState = NewTaskUiState(),
    actions: NewTaskActions = NoNewTaskActions,
    onNewTask: () -> Unit = {},
    createdTaskId: TaskId? = null,
    createdAnnouncement: Boolean = false,
    onCreatedHandled: () -> Unit = {},
) {
    // Outside the branch on the state, so a position restored after a process restart is applied
    // as soon as the list appears. Only index and offset are saved, never titles or notes.
    val listState = rememberLazyListState()

    // A new task is on top only once the list has reported it; then jump up (with keys the list would
    // otherwise keep the entry that was visible before, and the new one would lie above the visible part).
    val newestId = (state as? TaskListUiState.Loaded)?.tasks?.firstOrNull()?.id
    LaunchedEffect(createdTaskId, newestId) {
        if (createdTaskId != null && newestId == createdTaskId) {
            listState.scrollToItem(0)
            onCreatedHandled()
        }
    }

    Box(modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
        if (newTask.open) {
            NewTaskScreen(newTask, actions)
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = stringResource(R.string.app_name),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.semantics { heading() },
                            )
                        },
                    )
                },
                floatingActionButton = { if (state is TaskListUiState.Loaded) NewTaskAction(onNewTask) },
            ) { padding ->
                TaskListScreen(state, listState, Modifier.padding(padding))
            }
        }
        CreatedAnnouncement(createdAnnouncement)
    }
}

@Composable
private fun NewTaskAction(onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.testTag(NewTaskTags.ACTION),
    ) {
        Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.new_task_action))
    }
}

/** Invisible node whose description changes once; TalkBack reads it politely. No announceForAccessibility. */
@Composable
private fun CreatedAnnouncement(created: Boolean) {
    val text = if (created) stringResource(R.string.new_task_created) else null
    Box(
        Modifier
            .size(1.dp)
            .testTag(NewTaskTags.CREATED_ANNOUNCEMENT)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                if (text != null) contentDescription = text
            },
    )
}

object NoNewTaskActions : NewTaskActions {
    override fun onTitleChange(raw: String) = Unit
    override fun onNoteChange(raw: String) = Unit
    override fun onFocusChange(field: NewTaskField) = Unit
    override fun onSave() = Unit
    override fun onCloseRequest() = Unit
    override fun onDiscardDismiss() = Unit
    override fun onDiscardConfirm() = Unit
}
