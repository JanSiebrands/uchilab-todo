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
        val todoApplication = application as TodoApplication
        val repository = todoApplication.taskRepository
        val viewModel = ViewModelProvider(this, TaskListViewModel.factory(repository, todoApplication.listSettings))[TaskListViewModel::class.java]
        val taskFormViewModel = ViewModelProvider(this, TaskFormViewModel.factory(repository))[TaskFormViewModel::class.java]
        setContent {
            AppTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val notice by viewModel.notice.collectAsStateWithLifecycle()
                val statusAnnouncement by viewModel.statusAnnouncement.collectAsStateWithLifecycle()
                // Back is taken over while the input is open; it asks before discarding and does nothing while saving.
                BackHandler(enabled = taskFormViewModel.state.open) { taskFormViewModel.onCloseRequest() }
                MainScreen(
                    state = state,
                    taskForm = taskFormViewModel.state,
                    actions = taskFormViewModel,
                    onNewTask = taskFormViewModel::open,
                    createdTaskId = taskFormViewModel.createdTaskId,
                    announcement = taskFormViewModel.announcement,
                    onCreatedHandled = taskFormViewModel::onCreatedHandled,
                    onOpenTask = taskFormViewModel::openEdit,
                    editedTaskId = taskFormViewModel.editedTaskId,
                    onEditedHandled = taskFormViewModel::onEditedHandled,
                    onStatusChange = viewModel::onStatusChange,
                    onHideDoneChange = viewModel::onHideDoneChange,
                    notice = notice,
                    onNoticeShown = viewModel::onNoticeShown,
                    statusAnnouncement = statusAnnouncement,
                )
            }
        }
    }
}
