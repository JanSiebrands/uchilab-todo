package de.uchilab.todo.ui.tasklist

import android.annotation.SuppressLint
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import de.uchilab.todo.domain.TaskId
import de.uchilab.todo.domain.TaskStatus
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.uchilab.todo.R
import de.uchilab.todo.domain.Task

private val EntryMinHeight = 56.dp
private val EntryHorizontalPadding = 16.dp
private val EntryVerticalPadding = 12.dp
private val CheckboxStartPadding = 4.dp
private val TextStartPadding = 4.dp
private val DoneRowMinHeight = 56.dp
private val DoneRowEndPadding = 8.dp
private val TouchTarget = 48.dp
private const val DONE_HEADING_KEY = "done_heading"

/** Space below the list so the later "New task" action never covers the last entry (design 1). */
private val ListBottomPadding = 96.dp
private const val NOTE_MAX_LINES = 3

/**
 * Stateless: shows the list, the empty state or the read failure notice. The caller owns [listState]
 * so the scroll position survives the list appearing late after a process restart.
 */
@Composable
fun TaskListScreen(
    state: TaskListUiState,
    listState: LazyListState,
    onStatusChange: (TaskId, TaskStatus) -> Unit,
    modifier: Modifier = Modifier,
    onOpenTask: (Task) -> Unit = {},
    focusTaskId: TaskId? = null,
    onHideDoneChange: (Boolean) -> Unit = {},
) {
    when (state) {
        TaskListUiState.Loading -> Box(modifier.fillMaxSize())
        is TaskListUiState.Loaded ->
            when {
                state.open.isEmpty() && state.done.isEmpty() -> EmptyState(modifier)
                state.open.isEmpty() && state.hideDone -> AllHiddenState(state.done.size, onHideDoneChange, modifier)
                else -> TaskList(state, listState, onStatusChange, onOpenTask, focusTaskId, onHideDoneChange, modifier)
            }
        TaskListUiState.ReadFailed -> ReadFailedState(modifier)
    }
}

@Composable
private fun TaskList(
    state: TaskListUiState.Loaded,
    listState: LazyListState,
    onStatusChange: (TaskId, TaskStatus) -> Unit,
    onOpenTask: (Task) -> Unit,
    focusTaskId: TaskId?,
    onHideDoneChange: (Boolean) -> Unit,
    modifier: Modifier,
) {
    KeepPositionWhenTasksMove(state, listState)
    val checkOffFocus = rememberFocusAfterCheckOff(state)
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().testTag(TaskListTags.LIST),
        contentPadding = PaddingValues(bottom = ListBottomPadding),
    ) {
        items(state.open, key = { it.id.value }) { task ->
            val focus = focusTaskId == task.id || checkOffFocus == FocusTarget.Entry(task.id)
            TaskRow(task, onStatusChange, onOpenTask, focus, Modifier.animateItem())
        }
        if (state.done.isNotEmpty()) {
            item(key = DONE_HEADING_KEY) {
                DoneHeading(
                    doneCount = state.done.size,
                    hideDone = state.hideDone,
                    onToggle = { onHideDoneChange(!state.hideDone) },
                    requestFocus = checkOffFocus == FocusTarget.Toggle,
                    modifier = Modifier.animateItem(),
                )
            }
            if (!state.hideDone) {
                items(state.done, key = { it.id.value }) { task ->
                    TaskRow(task, onStatusChange, onOpenTask, focusTaskId == task.id, Modifier.animateItem())
                }
            }
        }
    }
}

/**
 * With keys the list follows the entry that was first visible. When that entry changes its group, the view
 * would jump with it; asking for the same index and offset keeps the view where it is. Only a pure move
 * (same tasks, other order) counts; a new task on top keeps the existing behaviour.
 * The scroll position is read without observation, so scrolling does not recompose the list.
 */
@SuppressLint("FrequentlyChangingValue")
@Composable
private fun KeepPositionWhenTasksMove(state: TaskListUiState.Loaded, listState: LazyListState) {
    val ids = (state.open + state.done).map { it.id.value }
    val previous = remember { arrayOf<List<Long>?>(null) }
    val before = previous[0]
    if (before != null && before != ids && before.toSet() == ids.toSet()) {
        Snapshot.withoutReadObservation {
            listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
    }
    previous[0] = ids
}

/** Where the focus goes after a task was checked off while done tasks are hidden (design A4). */
private sealed interface FocusTarget {
    data class Entry(val id: TaskId) : FocusTarget

    data object Toggle : FocusTarget
}

/**
 * When an open task leaves the open group because it became done, while done tasks are hidden, the focus would
 * be lost with it. The target is the open task that now stands at its place, otherwise the button of the done
 * row. Without open tasks the "all done" state follows and announces itself. The target is handed out for the
 * time the request needs to be applied.
 */
@Composable
private fun rememberFocusAfterCheckOff(state: TaskListUiState.Loaded): FocusTarget? {
    val openIds = state.open.map { it.id }
    val previous = remember { arrayOf<List<TaskId>?>(null) }
    var target by remember { mutableStateOf<FocusTarget?>(null) }
    LaunchedEffect(openIds, state.hideDone) {
        val before = previous[0]
        previous[0] = openIds
        val gone = before?.filter { it !in openIds }?.singleOrNull()
        if (gone != null && state.hideDone && state.done.any { it.id == gone }) {
            target = openIds.getOrNull(before.indexOf(gone))?.let { FocusTarget.Entry(it) }
                ?: FocusTarget.Toggle.takeIf { openIds.isNotEmpty() }
        }
    }
    LaunchedEffect(target) {
        if (target != null) {
            withFrameNanos { }
            target = null
        }
    }
    return target
}

/** Heading with the number of hidden tasks while hidden, and the button that shows or hides the done tasks. */
@Composable
private fun DoneHeading(
    doneCount: Int,
    hideDone: Boolean,
    onToggle: () -> Unit,
    requestFocus: Boolean,
    modifier: Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    if (requestFocus) LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val buttonDescription = stringResource(if (hideDone) R.string.task_list_show_done_description else R.string.task_list_hide_done_description)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = DoneRowMinHeight)
            .padding(start = EntryHorizontalPadding, end = DoneRowEndPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .testTag(TaskListTags.DONE_HEADING)
                .semantics(mergeDescendants = true) { heading() },
        ) {
            Text(
                text = stringResource(R.string.task_list_done_heading),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (hideDone) {
                Text(
                    text = stringResource(R.string.task_list_done_hidden_count, doneCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(
            onClick = onToggle,
            modifier = Modifier
                .defaultMinSize(minWidth = TouchTarget, minHeight = TouchTarget)
                .testTag(TaskListTags.DONE_TOGGLE)
                .focusRequester(focusRequester)
                .semantics { contentDescription = buttonDescription },
        ) {
            Text(stringResource(if (hideDone) R.string.task_list_show_done else R.string.task_list_hide_done))
        }
    }
}

@Composable
private fun TaskRow(
    task: Task,
    onStatusChange: (TaskId, TaskStatus) -> Unit,
    onOpenTask: (Task) -> Unit,
    requestFocus: Boolean,
    modifier: Modifier,
) {
    Column(modifier) {
        TaskEntry(task, onStatusChange, onOpenTask, requestFocus)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * Check box (own focus target) and text. Title in full, note up to three lines; the text is one focus target
 * for TalkBack that reads the whole note. Only the check box changes the status; a tap on the text opens the
 * task for editing. [requestFocus] puts the focus on the text after the task was edited.
 */
@Composable
private fun TaskEntry(
    task: Task,
    onStatusChange: (TaskId, TaskStatus) -> Unit,
    onOpenTask: (Task) -> Unit,
    requestFocus: Boolean,
) {
    val focusRequester = remember { FocusRequester() }
    if (requestFocus) LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val editLabel = stringResource(R.string.task_item_edit)
    val title = task.content.title
    val note = task.content.note
    val done = task.status == TaskStatus.DONE
    val spoken = if (note == null) title else stringResource(R.string.task_item_description, title, note)
    val stateText = stringResource(if (done) R.string.task_status_done else R.string.task_status_open)
    val checkboxDescription = stringResource(R.string.task_status_of, title)
    Row(Modifier.fillMaxWidth().heightIn(min = EntryMinHeight), verticalAlignment = Alignment.Top) {
        Checkbox(
            checked = done,
            onCheckedChange = { onStatusChange(task.id, if (done) TaskStatus.OPEN else TaskStatus.DONE) },
            modifier = Modifier
                .padding(start = CheckboxStartPadding)
                .testTag(TaskListTags.ITEM_CHECKBOX)
                .semantics {
                    contentDescription = checkboxDescription
                    stateDescription = stateText
                },
        )
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = EntryMinHeight)
                .testTag(TaskListTags.ITEM)
                .focusRequester(focusRequester)
                .semantics(mergeDescendants = true) {
                    contentDescription = spoken
                    if (done) stateDescription = stateText
                }
                .clickable(onClickLabel = editLabel) { onOpenTask(task) }
                .padding(start = TextStartPadding, end = EntryHorizontalPadding, top = EntryVerticalPadding, bottom = EntryVerticalPadding),
        ) {
            Text(
                text = title,
                style = contentDirection(MaterialTheme.typography.bodyLarge),
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                modifier = Modifier.testTag(TaskListTags.ITEM_TITLE),
            )
            if (note != null) {
                Text(
                    text = note,
                    style = contentDirection(MaterialTheme.typography.bodyMedium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = NOTE_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(TaskListTags.ITEM_NOTE),
                )
            }
        }
    }
}

/** Each paragraph gets the direction of its own content (right-to-left text next to left-to-right text). */
private fun contentDirection(style: TextStyle): TextStyle = style.copy(textDirection = TextDirection.Content)

@Composable
private fun EmptyState(modifier: Modifier) {
    Notice(
        icon = R.drawable.ic_task_list_empty,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        title = stringResource(R.string.task_list_empty_title),
        body = stringResource(R.string.task_list_empty_body),
        hint = stringResource(R.string.task_list_empty_hint),
        modifier = modifier,
        contentModifier = Modifier.testTag(TaskListTags.EMPTY),
    )
}

/** Every task is done and hidden: not the empty state, and the way back is right here (design A3). */
@Composable
private fun AllHiddenState(doneCount: Int, onHideDoneChange: (Boolean) -> Unit, modifier: Modifier) {
    Notice(
        icon = R.drawable.ic_task_list_all_done,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        title = stringResource(R.string.task_list_all_hidden_title),
        body = pluralStringResource(R.plurals.task_list_all_hidden_body, doneCount, doneCount),
        modifier = modifier,
        contentModifier = Modifier.testTag(TaskListTags.ALL_HIDDEN).semantics { liveRegion = LiveRegionMode.Polite },
        action = {
            FilledTonalButton(
                onClick = { onHideDoneChange(false) },
                modifier = Modifier.padding(top = 16.dp).heightIn(min = TouchTarget).testTag(TaskListTags.ALL_HIDDEN_SHOW),
            ) {
                Text(stringResource(R.string.task_list_all_hidden_show))
            }
        },
    )
}

@Composable
private fun ReadFailedState(modifier: Modifier) {
    Notice(
        icon = R.drawable.ic_task_list_error,
        iconTint = MaterialTheme.colorScheme.error,
        title = stringResource(R.string.task_list_error_title),
        body = stringResource(R.string.task_list_error_body),
        modifier = modifier,
        contentModifier = Modifier.testTag(TaskListTags.ERROR).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Centered sign, heading and explanation; scrolls so nothing is cut off at 200 % text or in landscape. */
@Composable
private fun Notice(
    @DrawableRes icon: Int,
    iconTint: Color,
    title: String,
    body: String,
    modifier: Modifier,
    contentModifier: Modifier,
    hint: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val minHeight = maxHeight
        Column(
            modifier = contentModifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = minHeight)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.testTag(TaskListTags.SIGN),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp).semantics { heading() },
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            action?.invoke()
        }
    }
}
