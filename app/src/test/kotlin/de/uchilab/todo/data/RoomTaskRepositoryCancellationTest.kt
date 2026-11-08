package de.uchilab.todo.data

import de.uchilab.todo.domain.TaskContent
import de.uchilab.todo.domain.TaskId
import de.uchilab.todo.domain.TaskStatus
import de.uchilab.todo.domain.Validated
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/** A DAO whose every write throws the given exception. */
private class ThrowingDao(private val error: () -> Throwable) : TaskDao {
    override fun observeAll(): Flow<List<TaskEntity>> = emptyFlow()
    override suspend fun insert(entity: TaskEntity): Long = throw error()
    override suspend fun updateContent(id: Long, title: String, note: String?): Int = throw error()
    override suspend fun updateStatus(id: Long, status: String): Int = throw error()
    override suspend fun delete(id: Long): Int = throw error()
}

/**
 * The repository reports only the SQLite exception type as STORAGE_FAILURE. Anything else, above all
 * a cancellation, must pass through unchanged (plan section 7).
 */
class RoomTaskRepositoryCancellationTest {

    private val content = (TaskContent.of("Milch", null) as Validated.Valid).content
    private val id = TaskId(1)

    private fun writes(repository: RoomTaskRepository): Map<String, suspend () -> Any> = mapOf(
        "add" to { repository.add(content) },
        "updateContent" to { repository.updateContent(id, content) },
        "setStatus" to { repository.setStatus(id, TaskStatus.DONE) },
        "delete" to { repository.delete(id) },
    )

    private fun assertPassesThrough(error: () -> Throwable) {
        val repository = RoomTaskRepository(ThrowingDao(error))
        for ((name, write) in writes(repository)) {
            val thrown = try {
                runBlocking { write() }
                null
            } catch (e: Throwable) {
                e
            }
            if (thrown == null) fail("$name swallowed the exception and returned a result")
            assertEquals("$name changed the exception", error()::class, thrown!!::class)
        }
    }

    @Test
    fun `a cancellation passes through every write and is not reported as a result`() =
        assertPassesThrough { CancellationException("cancelled") }

    @Test
    fun `any other exception passes through every write and is not reported as a result`() =
        assertPassesThrough { IllegalStateException("boom") }
}
