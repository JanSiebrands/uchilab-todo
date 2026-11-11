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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TextButton
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import android.text.BidiFormatter
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.withTimeoutOrNull
import de.uchilab.todo.domain.TaskStatus
import de.uchilab.todo.ui.tasklist.ListNotice
import de.uchilab.todo.ui.tasklist.ListAnnouncement
import de.uchilab.todo.ui.tasklist.entryAtPlaceOf
import de.uchilab.todo.ui.tasklist.TaskListTags
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
import de.uchilab.todo.domain.Task
import de.uchilab.todo.domain.TaskId
import de.uchilab.todo.ui.taskform.FormAnnouncement
import androidx.compose.runtime.withFrameNanos
import de.uchilab.todo.ui.taskform.TaskFormActions
import de.uchilab.todo.ui.taskform.TaskFormField
import de.uchilab.todo.ui.taskform.TaskFormScreen
import de.uchilab.todo.ui.taskform.TaskFormTags
import de.uchilab.todo.ui.taskform.TaskFormUiState
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
    taskForm: TaskFormUiState = TaskFormUiState(),
    actions: TaskFormActions = NoTaskFormActions,
    onNewTask: () -> Unit = {},
    createdTaskId: TaskId? = null,
    announcement: FormAnnouncement? = null,
    onCreatedHandled: () -> Unit = {},
    onOpenTask: (Task) -> Unit = {},
    editedTaskId: TaskId? = null,
    onEditedHandled: () -> Unit = {},
    onStatusChange: (TaskId, TaskStatus) -> Unit = { _, _ -> },
    onHideDoneChange: (Boolean) -> Unit = {},
    notice: ListNotice? = null,
    onNoticeShown: (ListNotice) -> Unit = {},
    onUndo: (ListNotice) -> Unit = {},
    statusAnnouncement: ListAnnouncement? = null,
) {
    // Outside the branch on the state, so a position restored after a process restart is applied
    // as soon as the list appears. Only index and offset are saved, never titles or notes.
    val listState = rememberLazyListState()

    // A new task is on top only once the list has reported it; then jump up (with keys the list would
    // otherwise keep the entry that was visible before, and the new one would lie above the visible part).
    val newestId = (state as? TaskListUiState.Loaded)?.open?.firstOrNull()?.id
    LaunchedEffect(createdTaskId, newestId) {
        if (createdTaskId != null && newestId == createdTaskId) {
            listState.scrollToItem(0)
            onCreatedHandled()
        }
    }

    // After an edit the focus goes back to the edited entry (TalkBack follows it); the scroll position stays.
    // Once the entry has been composed with the request, the request is handled.
    LaunchedEffect(editedTaskId) {
        if (editedTaskId != null) {
            withFrameNanos { }
            onEditedHandled()
        }
    }

    // The focus goes to an entry once: after undo to the restored one, after an ended offer to the one at its place.
    // It is handed back once the entry is on the list (or can never be, hidden), so a later list change does not move it.
    var requestedFocus by remember { mutableStateOf<TaskId?>(null) }
    val restoredId = (statusAnnouncement as? ListAnnouncement.Restored)?.id
    LaunchedEffect(restoredId) { if (restoredId != null) requestedFocus = restoredId }
    val currentState by rememberUpdatedState(state)
    val requested = requestedFocus
    val requestedIsShown = requested != null && (state as? TaskListUiState.Loaded)?.shows(requested) == true
    val requestedIsHidden = requested != null && (state as? TaskListUiState.Loaded)?.isHidden(requested) == true
    LaunchedEffect(requested, requestedIsShown, requestedIsHidden) {
        if (requestedIsShown) withFrameNanos { }
        if (requestedIsShown || requestedIsHidden) requestedFocus = null
    }

    // One hint at a time above the "New task" action (the host sits in the Scaffold). A newer hint restarts the
    // effect, which dismisses the one on screen. Delete and failed restore carry an action and stay for the undo time
    // (design B2); the time runs here because it depends on the accessibility settings.
    val snackbarHostState = remember { SnackbarHostState() }
    val noticeText = noticeText(state, notice)
    val noticeAction = noticeActionLabel(notice)
    val accessibility = LocalAccessibilityManager.current
    val serviceEnabled = LocalContext.current.getSystemService(AccessibilityManager::class.java)?.isEnabled == true
    LaunchedEffect(notice, noticeText) {
        if (notice != null && noticeText != null) {
            if (noticeAction == null) {
                val duration = if (notice is ListNotice.DoneHidden) SnackbarDuration.Short else SnackbarDuration.Long
                snackbarHostState.showSnackbar(noticeText, duration = duration)
                onNoticeShown(notice)
            } else {
                val timeout = undoTimeoutMillis(serviceEnabled) { base ->
                    accessibility?.calculateRecommendedTimeoutMillis(base, containsIcons = false, containsText = true, containsControls = true) ?: base
                }
                val result = withTimeoutOrNull(timeout) {
                    snackbarHostState.showSnackbar(noticeText, noticeAction, duration = SnackbarDuration.Indefinite)
                }
                if (result == SnackbarResult.ActionPerformed) onUndo(notice)
                onNoticeShown(notice)
                if (result != SnackbarResult.ActionPerformed) {
                    (currentState as? TaskListUiState.Loaded)?.entryAtPlaceOf(notice.deletedTask())?.let { requestedFocus = it }
                }
            }
        }
    }

    Box(modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
        if (taskForm.open) {
            TaskFormScreen(taskForm, actions)
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
                snackbarHost = {
                    SnackbarHost(snackbarHostState) { data ->
                        NoticeSnackbar(data)
                    }
                },
                floatingActionButton = { if (state is TaskListUiState.Loaded) NewTaskAction(onNewTask) },
            ) { padding ->
                TaskListScreen(state, listState, onStatusChange, Modifier.padding(padding), onOpenTask, editedTaskId ?: requestedFocus, onHideDoneChange)
            }
        }
        FormAnnouncementNode(announcement)
        StatusAnnouncement(statusAnnouncementText(state, statusAnnouncement))
    }
}

/** The text of a hint; for [ListNotice.DoneHidden] the title comes from the loaded list, `null` while it is not there yet. */
@Composable
private fun noticeText(state: TaskListUiState, notice: ListNotice?): String? =
    when (notice) {
        null -> null
        ListNotice.StatusSaveFailed -> stringResource(R.string.task_status_save_failed)
        ListNotice.SettingSaveFailed -> stringResource(R.string.task_list_setting_save_failed)
        is ListNotice.Deleted -> stringResource(R.string.task_deleted, isolated(notice.task.content.title))
        is ListNotice.RestoreFailed -> stringResource(R.string.task_not_restored, isolated(notice.task.content.title))
        is ListNotice.DoneHidden -> (state as? TaskListUiState.Loaded)?.done?.firstOrNull { it.id == notice.id }
            ?.let { stringResource(R.string.task_done_hidden, it.content.title) }
    }

/** The spoken text for the last saved change; the title comes from the loaded list, not from the change. */
@Composable
private fun statusAnnouncementText(state: TaskListUiState, change: ListAnnouncement?): String? {
    val loaded = state as? TaskListUiState.Loaded ?: return null
    val id = when (change) {
        is ListAnnouncement.StatusChanged -> change.id
        is ListAnnouncement.Restored -> change.id
        null -> return null
    }
    val title = (loaded.open + loaded.done).firstOrNull { it.id == id }?.content?.title ?: return null
    val resource = when (change) {
        is ListAnnouncement.Restored -> R.string.task_restored_announce
        is ListAnnouncement.StatusChanged ->
            if (change.status == TaskStatus.DONE) R.string.task_status_announce_done else R.string.task_status_announce_open
    }
    return stringResource(resource, isolated(title))
}

/** A title set into a sentence in its own direction, so right-to-left text does not scramble the rest (design). */
private fun isolated(title: String): String = BidiFormatter.getInstance().unicodeWrap(title)

@Composable
private fun noticeActionLabel(notice: ListNotice?): String? =
    when (notice) {
        is ListNotice.Deleted -> stringResource(R.string.task_undo_action)
        is ListNotice.RestoreFailed -> stringResource(R.string.task_restore_retry_action)
        else -> null
    }

private fun ListNotice.deletedTask(): Task = when (this) {
    is ListNotice.Deleted -> task
    is ListNotice.RestoreFailed -> task
    else -> error("no task")
}

/** Seconds the undo offer stays: 10, with an accessibility service 30, never less than the system recommends. */
internal fun undoTimeoutMillis(serviceEnabled: Boolean, recommended: (Long) -> Long): Long {
    val base = if (serviceEnabled) 30_000L else 10_000L
    return maxOf(base, recommended(base))
}

private fun TaskListUiState.Loaded.isHidden(id: TaskId): Boolean = hideDone && done.any { it.id == id }

private fun TaskListUiState.Loaded.shows(id: TaskId): Boolean = open.any { it.id == id } || (!hideDone && done.any { it.id == id })

/**
 * The hint on screen. With an action (undo, try again) the focus goes to the action when it appears, so TalkBack
 * reaches it at once. The text is not cut: the snackbar grows with the title.
 */
@Composable
private fun NoticeSnackbar(data: SnackbarData) {
    val modifier = Modifier.testTag(TaskListTags.STATUS_SNACKBAR).semantics { liveRegion = LiveRegionMode.Polite }
    val label = data.visuals.actionLabel
    val focus = remember { FocusRequester() }
    if (label != null) LaunchedEffect(data) { focus.requestFocus() }
    // Swiping to either side ends the hint (design B2); the host itself only dismisses on its own accessibility action.
    val swipe = rememberSwipeToDismissBoxState()
    LaunchedEffect(swipe.currentValue) { if (swipe.currentValue != SwipeToDismissBoxValue.Settled) data.dismiss() }
    SwipeToDismissBox(state = swipe, backgroundContent = {}) {
        if (label == null) {
            Snackbar(data, modifier = modifier)
        } else {
            Snackbar(
                modifier = modifier,
                action = {
                    TextButton(
                        onClick = data::performAction,
                        colors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionColor),
                        modifier = Modifier.focusRequester(focus),
                    ) { Text(label) }
                },
            ) { Text(data.visuals.message) }
        }
    }
}

/** Invisible node whose description changes after a status change; TalkBack reads it politely. */
@Composable
private fun StatusAnnouncement(text: String?) {
    Box(
        Modifier
            .size(1.dp)
            .testTag(TaskListTags.STATUS_ANNOUNCEMENT)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                if (text != null) contentDescription = text
            },
    )
}

@Composable
private fun NewTaskAction(onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.testTag(TaskFormTags.ACTION),
    ) {
        Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.new_task_action))
    }
}

/** Invisible node whose description changes once; TalkBack reads it politely. No announceForAccessibility. */
@Composable
private fun FormAnnouncementNode(announcement: FormAnnouncement?) {
    val text = when (announcement) {
        FormAnnouncement.CREATED -> stringResource(R.string.new_task_created)
        FormAnnouncement.EDITED -> stringResource(R.string.task_edit_saved)
        null -> null
    }
    Box(
        Modifier
            .size(1.dp)
            .testTag(TaskFormTags.CREATED_ANNOUNCEMENT)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                if (text != null) contentDescription = text
            },
    )
}

object NoTaskFormActions : TaskFormActions {
    override fun onTitleChange(raw: String) = Unit
    override fun onNoteChange(raw: String) = Unit
    override fun onFocusChange(field: TaskFormField) = Unit
    override fun onSave() = Unit
    override fun onDelete() = Unit
    override fun onCloseRequest() = Unit
    override fun onDiscardDismiss() = Unit
    override fun onDiscardConfirm() = Unit
}
