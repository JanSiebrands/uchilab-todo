package de.uchilab.todo.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.uchilab.todo.TodoApplication
import de.uchilab.todo.ui.newtask.NewTaskViewModel
import de.uchilab.todo.ui.tasklist.TaskListViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as TodoApplication).taskRepository
        val viewModel = ViewModelProvider(this, TaskListViewModel.factory(repository))[TaskListViewModel::class.java]
        val newTaskViewModel = ViewModelProvider(this, NewTaskViewModel.factory(repository))[NewTaskViewModel::class.java]
        setContent {
            AppTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                // Back is taken over while the input is open; it asks before discarding and does nothing while saving.
                BackHandler(enabled = newTaskViewModel.state.open) { newTaskViewModel.onCloseRequest() }
                MainScreen(
                    state = state,
                    newTask = newTaskViewModel.state,
                    actions = newTaskViewModel,
                    onNewTask = newTaskViewModel::open,
                    createdTaskId = newTaskViewModel.createdTaskId,
                    createdAnnouncement = newTaskViewModel.createdAnnouncement,
                    onCreatedHandled = newTaskViewModel::onCreatedHandled,
                )
            }
        }
    }
}
