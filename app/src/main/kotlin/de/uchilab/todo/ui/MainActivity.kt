package de.uchilab.todo.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.uchilab.todo.TodoApplication
import de.uchilab.todo.ui.tasklist.TaskListViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as TodoApplication).taskRepository
        val viewModel = ViewModelProvider(this, TaskListViewModel.factory(repository))[TaskListViewModel::class.java]
        setContent {
            AppTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                MainScreen(state)
            }
        }
    }
}
