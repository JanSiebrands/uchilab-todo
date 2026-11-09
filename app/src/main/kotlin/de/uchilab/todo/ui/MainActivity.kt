package de.uchilab.todo.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.uchilab.todo.TodoApplication
import de.uchilab.todo.ui.taskform.TaskFormViewModel
import de.uchilab.todo.ui.tasklist.TaskListViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as TodoApplication).taskRepository
        val viewModel = ViewModelProvider(this, TaskListViewModel.factory(repository))[TaskListViewModel::class.java]
        val taskFormViewModel = ViewModelProvider(this, TaskFormViewModel.factory(repository))[TaskFormViewModel::class.java]
        setContent {
            AppTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val statusSaveFailed by viewModel.statusSaveFailed.collectAsStateWithLifecycle()
                val statusAnnouncement by viewModel.statusAnnouncement.collectAsStateWithLifecycle()
                // Back is taken over while the input is open; it asks before discarding and does nothing while saving.
                BackHandler(enabled = taskFormViewModel.state.open) { taskFormViewModel.onCloseRequest() }
                MainScreen(
                    state = state,
                    taskForm = taskFormViewModel.state,
                    actions = taskFormViewModel,
                    onNewTask = taskFormViewModel::open,
                    createdTaskId = taskFormViewModel.createdTaskId,
                    createdAnnouncement = taskFormViewModel.createdAnnouncement,
                    onCreatedHandled = taskFormViewModel::onCreatedHandled,
                    onStatusChange = viewModel::onStatusChange,
                    statusSaveFailed = statusSaveFailed,
                    onStatusSaveFailedShown = viewModel::onStatusSaveFailedShown,
                    statusAnnouncement = statusAnnouncement,
                )
            }
        }
    }
}
