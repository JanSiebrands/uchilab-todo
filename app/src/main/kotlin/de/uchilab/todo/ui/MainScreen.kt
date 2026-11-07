package de.uchilab.todo.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import de.uchilab.todo.R
import de.uchilab.todo.ui.tasklist.TaskListScreen
import de.uchilab.todo.ui.tasklist.TaskListUiState

/** Top bar with the working title and the task list below. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(state: TaskListUiState, modifier: Modifier = Modifier) {
    // Outside the branch on the state, so a position restored after a process restart is applied
    // as soon as the list appears. Only index and offset are saved, never titles or notes.
    val listState = rememberLazyListState()
    Scaffold(
        modifier = modifier.fillMaxSize(),
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
    ) { padding ->
        TaskListScreen(state, listState, Modifier.padding(padding))
    }
}
