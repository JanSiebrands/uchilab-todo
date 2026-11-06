package de.uchilab.todo.data

import android.database.SQLException
import de.uchilab.todo.domain.Task
import de.uchilab.todo.domain.TaskContent
import de.uchilab.todo.domain.TaskId
import de.uchilab.todo.domain.TaskRepository
import de.uchilab.todo.domain.TaskStatus
import de.uchilab.todo.domain.WriteError
import de.uchilab.todo.domain.WriteResult
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTaskRepository(
    private val dao: TaskDao,
    private val clock: Clock = Clock.systemUTC(),
) : TaskRepository {

    override fun observeTasks(): Flow<List<Task>> = dao.observeAll().map { rows -> rows.map(TaskEntity::toDomain) }

    override suspend fun add(content: TaskContent): WriteResult<Task> = write {
        val createdAt = Instant.ofEpochMilli(clock.millis())
        val status = TaskStatus.OPEN
        val id = dao.insert(TaskEntity(0, content.title, content.note, createdAt.toEpochMilli(), status.toStored()))
        WriteResult.Success(Task(TaskId(id), content, createdAt, status))
    }

    override suspend fun updateContent(id: TaskId, content: TaskContent): WriteResult<Unit> = write {
        affected(dao.updateContent(id.value, content.title, content.note))
    }

    override suspend fun setStatus(id: TaskId, status: TaskStatus): WriteResult<Unit> = write {
        affected(dao.updateStatus(id.value, status.toStored()))
    }

    override suspend fun delete(id: TaskId): WriteResult<Unit> = write { affected(dao.delete(id.value)) }

    private fun affected(rows: Int): WriteResult<Unit> =
        if (rows > 0) WriteResult.Success(Unit) else WriteResult.Failure(WriteError.NOT_FOUND)

    /**
     * Catches only the SQLite exception type of the driver, no `Exception`/`Throwable` and thus never
     * a `CancellationException`. The exception is dropped: its message can contain values (security L3).
     */
    private inline fun <T> write(block: () -> WriteResult<T>): WriteResult<T> =
        try {
            block()
        } catch (_: SQLException) {
            WriteResult.Failure(WriteError.STORAGE_FAILURE)
        }
}
