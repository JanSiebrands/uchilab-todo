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
import androidx.compose.material3.Checkbox
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
private val HeadingMinHeight = 48.dp
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
) {
    when (state) {
        TaskListUiState.Loading -> Box(modifier.fillMaxSize())
        is TaskListUiState.Loaded ->
            if (state.open.isEmpty() && state.done.isEmpty()) {
                EmptyState(modifier)
            } else {
                TaskList(state, listState, onStatusChange, modifier)
            }
        TaskListUiState.ReadFailed -> ReadFailedState(modifier)
    }
}

@Composable
private fun TaskList(
    state: TaskListUiState.Loaded,
    listState: LazyListState,
    onStatusChange: (TaskId, TaskStatus) -> Unit,
    modifier: Modifier,
) {
    KeepPositionWhenTasksMove(state, listState)
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().testTag(TaskListTags.LIST),
        contentPadding = PaddingValues(bottom = ListBottomPadding),
    ) {
        items(state.open, key = { it.id.value }) { task -> TaskRow(task, onStatusChange, Modifier.animateItem()) }
        if (state.done.isNotEmpty()) {
            item(key = DONE_HEADING_KEY) { DoneHeading(Modifier.animateItem()) }
            items(state.done, key = { it.id.value }) { task -> TaskRow(task, onStatusChange, Modifier.animateItem()) }
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

@Composable
private fun DoneHeading(modifier: Modifier) {
    Text(
        text = stringResource(R.string.task_list_done_heading),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .testTag(TaskListTags.DONE_HEADING)
            .semantics { heading() }
            .heightIn(min = HeadingMinHeight)
            .wrapContentHeight(Alignment.CenterVertically)
            .padding(horizontal = EntryHorizontalPadding),
    )
}

@Composable
private fun TaskRow(task: Task, onStatusChange: (TaskId, TaskStatus) -> Unit, modifier: Modifier) {
    Column(modifier) {
        TaskEntry(task, onStatusChange)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * Check box (own focus target) and text. Title in full, note up to three lines; the text is one focus target
 * for TalkBack that reads the whole note. Only the check box changes the status.
 */
@Composable
private fun TaskEntry(task: Task, onStatusChange: (TaskId, TaskStatus) -> Unit) {
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
                .semantics(mergeDescendants = true) {
                    contentDescription = spoken
                    if (done) stateDescription = stateText
                }
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
        }
    }
}
