package de.uchilab.todo

import android.app.Application
import de.uchilab.todo.data.RoomTaskRepository
import de.uchilab.todo.data.SharedPreferencesListSettings
import de.uchilab.todo.data.TaskDatabase
import de.uchilab.todo.domain.ListSettings
import de.uchilab.todo.domain.TaskRepository

/** Wiring without a DI framework (ADR 0001/0003): exactly one database instance per process. */
class TodoApplication : Application() {
    val taskRepository: TaskRepository by lazy { RoomTaskRepository(TaskDatabase.create(this).taskDao()) }
    val listSettings: ListSettings by lazy { SharedPreferencesListSettings(this) }
}
