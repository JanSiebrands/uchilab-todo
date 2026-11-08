package de.uchilab.todo.ui.tasklist

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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

/** Space below the list so the later "New task" action never covers the last entry (design 1). */
private val ListBottomPadding = 96.dp
private const val NOTE_MAX_LINES = 3

/**
 * Stateless: shows the list, the empty state or the read failure notice. The caller owns [listState]
 * so the scroll position survives the list appearing late after a process restart.
 */
@Composable
fun TaskListScreen(state: TaskListUiState, listState: LazyListState, modifier: Modifier = Modifier) {
    when (state) {
        TaskListUiState.Loading -> Box(modifier.fillMaxSize())
        is TaskListUiState.Loaded ->
            if (state.tasks.isEmpty()) EmptyState(modifier) else TaskList(state.tasks, listState, modifier)
        TaskListUiState.ReadFailed -> ReadFailedState(modifier)
    }
}

@Composable
private fun TaskList(tasks: List<Task>, listState: LazyListState, modifier: Modifier) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().testTag(TaskListTags.LIST),
        contentPadding = PaddingValues(bottom = ListBottomPadding),
    ) {
        items(tasks, key = { it.id.value }) { task ->
            TaskEntry(task)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** Title in full, note up to three lines; one focus target for TalkBack that reads the whole note. */
@Composable
private fun TaskEntry(task: Task) {
    val title = task.content.title
    val note = task.content.note
    val spoken = if (note == null) title else stringResource(R.string.task_item_description, title, note)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = EntryMinHeight)
            .testTag(TaskListTags.ITEM)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .padding(horizontal = EntryHorizontalPadding, vertical = EntryVerticalPadding),
    ) {
        Text(
            text = title,
            style = contentDirection(MaterialTheme.typography.bodyLarge),
            color = MaterialTheme.colorScheme.onSurface,
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
