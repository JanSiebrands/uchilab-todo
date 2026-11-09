package de.uchilab.todo.ui.taskform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.uchilab.todo.R
import de.uchilab.todo.domain.TaskContent
import java.text.NumberFormat

private val FormPadding = 16.dp
private const val NOTE_MIN_LINES = 4

/** Stateless input screen: bar with close and save, the two fields, hints, save failure and the discard dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskFormScreen(state: TaskFormUiState, actions: TaskFormActions, modifier: Modifier = Modifier) {
    if (!state.open) return
    val titleFocus = remember { FocusRequester() }
    val noteFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Cursor in the title (or in the field that had the focus before rotation or process end) and the keyboard open.
    LaunchedEffect(Unit) {
        requestFocus(state.focus ?: TaskFormField.TITLE, titleFocus, noteFocus)
        keyboard?.show()
    }
    // Later requests, e.g. the first faulty field after "Save". Not on the first composition (handled above).
    LaunchedEffect(state.focusTick) {
        if (state.focusTick != 0) requestFocus(state.focus ?: TaskFormField.TITLE, titleFocus, noteFocus)
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag(TaskFormTags.FORM),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = actions::onCloseRequest, modifier = Modifier.testTag(TaskFormTags.CLOSE)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.new_task_close),
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.new_task_heading),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                actions = {
                    TextButton(onClick = actions::onSave, modifier = Modifier.testTag(TaskFormTags.SAVE)) {
                        Text(stringResource(R.string.new_task_save))
                    }
                },
            )
        },
    ) { padding ->
        Form(state, actions, titleFocus, noteFocus, padding)
    }

    if (state.discardDialog) DiscardDialog(actions)
}

private fun requestFocus(field: TaskFormField, title: FocusRequester, note: FocusRequester) {
    (if (field == TaskFormField.NOTE) note else title).requestFocus()
}

@Composable
private fun Form(
    state: TaskFormUiState,
    actions: TaskFormActions,
    titleFocus: FocusRequester,
    noteFocus: FocusRequester,
    padding: PaddingValues,
) {
    val locale = LocalConfiguration.current.locales[0]
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    Column(
        modifier = Modifier
            .padding(padding)
            .consumeWindowInsets(padding)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(FormPadding),
        verticalArrangement = Arrangement.spacedBy(FormPadding),
    ) {
        if (state.saveFailed) SaveFailedNotice()

        val titleHint = when {
            state.titleTooLong -> stringResource(R.string.new_task_title_too_long, numbers.format(TaskContent.MAX_TITLE_LENGTH))
            state.titleMissingShown -> stringResource(R.string.new_task_title_missing)
            else -> null
        }
        val titleCounter = counter(state.titleCounterVisible, state.titleLength, TaskContent.MAX_TITLE_LENGTH, numbers)
        Field(
            value = state.title,
            onValueChange = actions::onTitleChange,
            label = stringResource(R.string.new_task_title_label),
            hint = titleHint,
            requiredText = stringResource(R.string.new_task_title_required),
            counter = titleCounter,
            counterTooLong = state.titleTooLong,
            tag = TaskFormTags.TITLE_FIELD,
            hintTag = TaskFormTags.TITLE_HINT,
            counterTag = TaskFormTags.TITLE_COUNTER,
            singleLine = true,
            focusRequester = titleFocus,
            onFocus = { actions.onFocusChange(TaskFormField.TITLE) },
        )

        val noteHint = if (state.noteTooLong) {
            stringResource(R.string.new_task_note_too_long, numbers.format(TaskContent.MAX_NOTE_LENGTH))
        } else {
            null
        }
        val noteCounter = counter(state.noteCounterVisible, state.noteLength, TaskContent.MAX_NOTE_LENGTH, numbers)
        Field(
            value = state.note,
            onValueChange = actions::onNoteChange,
            label = stringResource(R.string.new_task_note_label),
            hint = noteHint,
            requiredText = null,
            counter = noteCounter,
            counterTooLong = state.noteTooLong,
            tag = TaskFormTags.NOTE_FIELD,
            hintTag = TaskFormTags.NOTE_HINT,
            counterTag = TaskFormTags.NOTE_COUNTER,
            singleLine = false,
            focusRequester = noteFocus,
            onFocus = { actions.onFocusChange(TaskFormField.NOTE) },
        )
    }
}

/** Visible and spoken text of a counter; both formatted for the language of the device. */
private class CounterText(val visible: String, val spoken: String)

@Composable
private fun counter(visible: Boolean, length: Int, limit: Int, numbers: NumberFormat): CounterText? {
    if (!visible) return null
    val used = numbers.format(length)
    val max = numbers.format(limit)
    return CounterText(
        visible = stringResource(R.string.new_task_counter, used, max),
        spoken = stringResource(R.string.new_task_counter_description, used, max),
    )
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hint: String?,
    requiredText: String?,
    counter: CounterText?,
    counterTooLong: Boolean,
    tag: String,
    hintTag: String,
    counterTag: String,
    singleLine: Boolean,
    focusRequester: FocusRequester,
    onFocus: () -> Unit,
) {
    val supporting = hint ?: requiredText
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = hint != null,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else NOTE_MIN_LINES,
        keyboardOptions = if (singleLine) {
            KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next)
        } else {
            KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        },
        supportingText = if (supporting == null && counter == null) {
            null
        } else {
            {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (supporting != null) {
                        Text(
                            text = supporting,
                            modifier = Modifier.weight(1f).then(if (hint != null) Modifier.testTag(hintTag) else Modifier),
                        )
                    }
                    if (counter != null) {
                        Text(
                            text = counter.visible,
                            color = if (counterTooLong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .testTag(counterTag)
                                .clearAndSetSemantics { contentDescription = counter.spoken },
                        )
                    }
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .then(if (hint != null) Modifier.semantics { error(hint) } else Modifier),
    )
}

/** Fixed text, never a technical message; urgent live region so TalkBack says it at once. */
@Composable
private fun SaveFailedNotice() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TaskFormTags.SAVE_FAILED)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
    ) {
        Row(Modifier.padding(FormPadding), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_task_list_error),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.new_task_save_failed_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.new_task_save_failed_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * "Keep editing" stands left (the safe choice), "Discard" right; tapping outside or back only closes the dialog.
 * The dialog lives in its own window, so the root's testTagsAsResourceId does not reach it and is set here again.
 */
@Composable
private fun DiscardDialog(actions: TaskFormActions) {
    AlertDialog(
        onDismissRequest = actions::onDiscardDismiss,
        modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag(TaskFormTags.DISCARD_DIALOG),
        title = { Text(stringResource(R.string.new_task_discard_title)) },
        text = { Text(stringResource(R.string.new_task_discard_body)) },
        dismissButton = {
            TextButton(onClick = actions::onDiscardDismiss, modifier = Modifier.testTag(TaskFormTags.DISCARD_KEEP)) {
                Text(stringResource(R.string.new_task_discard_keep))
            }
        },
        confirmButton = {
            TextButton(onClick = actions::onDiscardConfirm, modifier = Modifier.testTag(TaskFormTags.DISCARD_CONFIRM)) {
                Text(stringResource(R.string.new_task_discard_confirm))
            }
        },
    )
}
