package de.uchilab.todo.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.uchilab.todo.domain.TaskContent
import de.uchilab.todo.domain.TaskReadException
import de.uchilab.todo.domain.Validated
import de.uchilab.todo.ui.tasklist.TaskListUiState
import de.uchilab.todo.ui.tasklist.toUiState
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A damaged database file must never be deleted or replaced (ADR 0003, UCH-78). Production factory and repository. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CorruptDatabaseTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var db: TaskDatabase? = null

    private companion object {
        const val PAGE = 4096L
    }

    private val file: File get() = context.getDatabasePath(TaskDatabase.DATABASE_NAME)

    @Before
    fun cleanFiles() {
        file.parentFile?.mkdirs()
        listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
    }

    @After
    fun closeDb() {
        db?.close()
    }

    private fun open(): RoomTaskRepository {
        db?.close()
        val created = TaskDatabase.create(context)
        db = created
        return RoomTaskRepository(created.taskDao())
    }

    private fun state(repository: RoomTaskRepository): TaskListUiState =
        runBlocking { withTimeout(10_000) { repository.observeTasks().toUiState(false).first() } }

    private fun content(title: String) = (TaskContent.of(title, null) as Validated.Valid).content

    private fun seed(vararg titles: String) {
        val repository = open()
        runBlocking { titles.forEach { repository.add(content(it)) } }
        db?.close()
        db = null
    }

    private fun titlesOf(state: TaskListUiState) = (state as TaskListUiState.Loaded).let { it.open + it.done }.map { it.content.title }

    /** Keeps the header page intact and fills every following page with invented bytes. */
    private fun overwriteAllPagesAfterTheFirst() {
        RandomAccessFile(file, "rw").use {
            val rest = (it.length() - PAGE).toInt()
            it.seek(PAGE)
            it.write(ByteArray(rest) { i -> (0x5A + i % 7).toByte() })
        }
    }

    @Test
    fun `a file that is not a database gives a read failure and stays byte-identical, also after reopening`() {
        val bytes = "erfundener-text-kein-sqlite ".repeat(200).toByteArray()
        file.writeBytes(bytes)

        assertEquals(TaskListUiState.ReadFailed, state(open()))
        assertArrayEquals(bytes, file.readBytes())

        assertEquals(TaskListUiState.ReadFailed, state(open()))
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test
    fun `a database that opens but cannot be read gives a read failure and stays byte-identical`() {
        seed("eins", "zwei", "drei")
        File(file.path + "-wal").delete()
        File(file.path + "-shm").delete()
        overwriteAllPagesAfterTheFirst()
        val bytes = file.readBytes()

        val outcome = runCatching { state(open()) }
        assertEquals(TaskListUiState.ReadFailed, outcome.getOrNull())
        assertArrayEquals(bytes, file.readBytes())

        assertEquals(TaskListUiState.ReadFailed, state(open()))
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test
    fun `without a file a new one is created and the list is empty`() {
        assertFalse(file.exists())
        val loaded = state(open())
        assertTrue(loaded is TaskListUiState.Loaded)
        assertTrue((loaded as TaskListUiState.Loaded).let { it.open.isEmpty() && it.done.isEmpty() })
        assertTrue(file.exists())
    }

    @Test
    fun `damaged companion files never lead to deletion or to an empty list`() {
        seed("alpha", "beta")
        File(file.path + "-wal").writeBytes(ByteArray(64) { 0x41 })
        File(file.path + "-shm").writeBytes(ByteArray(64) { 0x42 })

        val result = state(open())
        assertTrue(file.exists())
        assertTrue(file.length() > 0)
        when (result) {
            is TaskListUiState.Loaded -> assertEquals(listOf("beta", "alpha"), titlesOf(result))
            TaskListUiState.ReadFailed -> Unit
            else -> throw AssertionError("was $result")
        }
    }

    @Test
    fun `missing companion files still show the tasks`() {
        seed("alpha", "beta")
        File(file.path + "-wal").delete()
        File(file.path + "-shm").delete()
        assertEquals(listOf("beta", "alpha"), titlesOf(state(open())))
    }

    @Test
    fun `an empty file counts as damaged, stays empty and fails again on a new instance`() {
        file.writeBytes(ByteArray(0))

        assertEquals(TaskListUiState.ReadFailed, state(open()))
        assertEquals(0L, file.length())

        assertEquals(TaskListUiState.ReadFailed, state(open()))
        assertEquals(0L, file.length())
    }

    @Test
    fun `a fresh start with a briefly empty file stays normal, also with parallel readers`() {
        val repository = open()
        runBlocking {
            withTimeout(10_000) {
                repository.add(content("neu"))
                val readers = List(2) { async { repository.observeTasks().first() } }.awaitAll()
                readers.forEach { assertEquals(listOf("neu"), it.map { task -> task.content.title }) }
            }
        }
    }

    @Test
    fun `the read failure carries no message and no cause`() {
        file.writeBytes("erfunden".repeat(100).toByteArray())
        val error = runCatching {
            runBlocking { withTimeout(10_000) { open().observeTasks().first() } }
        }.exceptionOrNull()
        assertTrue("was $error", error is TaskReadException)
        generateSequence(error) { it.cause }.forEach {
            assertTrue(it is TaskReadException)
            assertNull(it.message)
        }
    }
}
