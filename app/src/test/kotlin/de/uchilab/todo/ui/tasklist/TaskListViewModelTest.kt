package de.uchilab.todo.ui.tasklist

import de.uchilab.todo.domain.Task
import de.uchilab.todo.domain.TaskContent
import de.uchilab.todo.domain.TaskId
import de.uchilab.todo.domain.TaskReadException
import de.uchilab.todo.domain.TaskRepository
import de.uchilab.todo.domain.TaskStatus
import de.uchilab.todo.domain.Validated
import de.uchilab.todo.domain.WriteResult
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

private fun task(id: Long, title: String): Task =
    Task(TaskId(id), (TaskContent.of(title, null) as Validated.Valid).content, Instant.EPOCH, TaskStatus.OPEN)

private class FakeRepository(private val tasks: Flow<List<Task>>) : TaskRepository {
    override fun observeTasks(): Flow<List<Task>> = tasks
    override suspend fun add(content: TaskContent): WriteResult<Task> = error("not used")
    override suspend fun updateContent(id: TaskId, content: TaskContent): WriteResult<Unit> = error("not used")
    override suspend fun setStatus(id: TaskId, status: TaskStatus): WriteResult<Unit> = error("not used")
    override suspend fun delete(id: TaskId): WriteResult<Unit> = error("not used")
}

@OptIn(ExperimentalCoroutinesApi::class)
class TaskListViewModelTest {

    @Before
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    @Test
    fun `state is loading before the repository delivers anything, then loaded in the delivered order`() = runTest {
        val source = MutableSharedFlow<List<Task>>()
        val viewModel = TaskListViewModel(FakeRepository(source))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        assertSame(TaskListUiState.Loading, viewModel.state.value)

        source.emit(listOf(task(2, "zwei"), task(1, "eins")))
        val loaded = viewModel.state.value as TaskListUiState.Loaded
        assertEquals(listOf(2L, 1L), loaded.tasks.map { it.id.value })
    }

    @Test
    fun `an empty stored list is loaded and not loading`() = runTest {
        val viewModel = TaskListViewModel(FakeRepository(flow { emit(emptyList()) }))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        val loaded = viewModel.state.value as TaskListUiState.Loaded
        assertTrue(loaded.tasks.isEmpty())
    }

    @Test
    fun `a read failure becomes the read failed state`() = runTest {
        val viewModel = TaskListViewModel(FakeRepository(flow { throw TaskReadException() }))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        assertSame(TaskListUiState.ReadFailed, viewModel.state.value)
    }

    @Test
    fun `a read failure after loaded data replaces the list`() = runTest {
        val source = flow {
            emit(listOf(task(1, "eins")))
            throw TaskReadException()
        }
        val states = source.toUiState().toList()
        assertEquals(2, states.size)
        assertTrue(states[0] is TaskListUiState.Loaded)
        assertSame(TaskListUiState.ReadFailed, states[1])
    }

    @Test
    fun `other exceptions are not swallowed`() = runTest {
        val failure = IllegalStateException("kein Lesefehler")
        try {
            flow<List<Task>> { throw failure }.toUiState().toList()
            fail("expected the exception to pass through")
        } catch (e: IllegalStateException) {
            assertSame(failure, e)
        }
    }

    @Test
    fun `cancellation passes through and is not turned into a state`() = runTest {
        val cancellation = CancellationException("abgebrochen")
        val seen = mutableListOf<TaskListUiState>()
        try {
            flow<List<Task>> { throw cancellation }.toUiState().collect { seen += it }
            fail("expected the cancellation to pass through")
        } catch (e: CancellationException) {
            assertSame(cancellation, e)
        }
        assertTrue(seen.isEmpty())
    }

    @Test
    fun `the loaded state does not reveal titles or notes in its text form`() {
        val loaded = TaskListUiState.Loaded(listOf(task(1, "GEHEIMER-TITEL")))
        assertFalse(loaded.toString().contains("GEHEIMER"))
    }
}
