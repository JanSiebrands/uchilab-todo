package de.uchilab.todo.ui.tasklist

import de.uchilab.todo.domain.Task
import de.uchilab.todo.domain.TaskContent
import de.uchilab.todo.domain.TaskId
import de.uchilab.todo.domain.TaskReadException
import de.uchilab.todo.domain.TaskRepository
import de.uchilab.todo.domain.TaskStatus
import de.uchilab.todo.domain.Validated
import de.uchilab.todo.domain.WriteError
import de.uchilab.todo.domain.WriteResult
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
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

private fun task(id: Long, title: String, status: TaskStatus = TaskStatus.OPEN): Task =
    Task(TaskId(id), (TaskContent.of(title, null) as Validated.Valid).content, Instant.EPOCH, status)

private class FakeRepository(
    private val tasks: Flow<List<Task>>,
    var setStatusResult: WriteResult<Unit> = WriteResult.Success(Unit),
) : TaskRepository {
    val statusWrites = mutableListOf<Pair<TaskId, TaskStatus>>()
    override fun observeTasks(): Flow<List<Task>> = tasks
    override suspend fun add(content: TaskContent): WriteResult<Task> = error("not used")
    override suspend fun updateContent(id: TaskId, content: TaskContent): WriteResult<Unit> = error("not used")
    override suspend fun setStatus(id: TaskId, status: TaskStatus): WriteResult<Unit> {
        statusWrites += id to status
        return setStatusResult
    }
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
        assertEquals(listOf(2L, 1L), loaded.open.map { it.id.value })
    }

    @Test
    fun `an empty stored list is loaded and not loading`() = runTest {
        val viewModel = TaskListViewModel(FakeRepository(flow { emit(emptyList()) }))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        val loaded = viewModel.state.value as TaskListUiState.Loaded
        assertTrue(loaded.open.isEmpty() && loaded.done.isEmpty())
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
        val loaded = listOf(task(1, "GEHEIMER-TITEL"), task(2, "GEHEIMER-ZWEI", TaskStatus.DONE)).groupByStatus()
        assertFalse(loaded.toString().contains("GEHEIMER"))
    }

    // --- grouping (Z3, Z10) ---

    @Test
    fun `open and done tasks are split, each group keeps the newest first order`() {
        val grouped = listOf(
            task(6, "a", TaskStatus.DONE), task(5, "b"), task(4, "c", TaskStatus.DONE),
            task(3, "d"), task(2, "e", TaskStatus.DONE), task(1, "f"),
        ).groupByStatus()
        assertEquals(listOf(5L, 3L, 1L), grouped.open.map { it.id.value })
        assertEquals(listOf(6L, 4L, 2L), grouped.done.map { it.id.value })
    }

    @Test
    fun `a new open task stands first among the open ones although done tasks are older`() = runTest {
        val source = MutableSharedFlow<List<Task>>()
        val viewModel = TaskListViewModel(FakeRepository(source))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        source.emit(listOf(task(2, "alt", TaskStatus.DONE), task(1, "alt", TaskStatus.DONE)))
        source.emit(listOf(task(3, "neu")) + listOf(task(2, "alt", TaskStatus.DONE), task(1, "alt", TaskStatus.DONE)))
        val loaded = viewModel.state.value as TaskListUiState.Loaded
        assertEquals(3L, loaded.open.first().id.value)
        assertEquals(listOf(2L, 1L), loaded.done.map { it.id.value })
    }

    // --- changing the status (Z13, Z21) ---

    private fun viewModelWith(repository: FakeRepository) = TaskListViewModel(repository)

    @Test
    fun `a successful change writes the target status and announces it without a failure notice`() = runTest {
        val repository = FakeRepository(flow { emit(listOf(task(1, "eins"))) })
        val viewModel = viewModelWith(repository)

        viewModel.onStatusChange(TaskId(1), TaskStatus.DONE)

        assertEquals(listOf(TaskId(1) to TaskStatus.DONE), repository.statusWrites)
        assertFalse(viewModel.statusSaveFailed.value)
        assertEquals(StatusChange(TaskId(1), TaskStatus.DONE), viewModel.statusAnnouncement.value)
    }

    @Test
    fun `a failing write shows the failure, does not announce and leaves the list state alone`() = runTest {
        val source = MutableSharedFlow<List<Task>>()
        val repository = FakeRepository(source, WriteResult.Failure(WriteError.STORAGE_FAILURE))
        val viewModel = viewModelWith(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        source.emit(listOf(task(1, "eins")))
        val before = viewModel.state.value

        viewModel.onStatusChange(TaskId(1), TaskStatus.DONE)

        assertTrue(viewModel.statusSaveFailed.value)
        assertEquals(null, viewModel.statusAnnouncement.value)
        assertSame(before, viewModel.state.value)
    }

    @Test
    fun `a task that is gone also counts as a failed save`() = runTest {
        val repository = FakeRepository(emptyFlow(), WriteResult.Failure(WriteError.NOT_FOUND))
        val viewModel = viewModelWith(repository)
        viewModel.onStatusChange(TaskId(9), TaskStatus.OPEN)
        assertTrue(viewModel.statusSaveFailed.value)
        assertEquals(null, viewModel.statusAnnouncement.value)
    }

    @Test
    fun `the failure notice is cleared once it was shown`() = runTest {
        val repository = FakeRepository(emptyFlow(), WriteResult.Failure(WriteError.STORAGE_FAILURE))
        val viewModel = viewModelWith(repository)
        viewModel.onStatusChange(TaskId(1), TaskStatus.DONE)
        viewModel.onStatusSaveFailedShown()
        assertFalse(viewModel.statusSaveFailed.value)
    }

    @Test
    fun `the announcement does not reveal a title in its text form`() = runTest {
        val repository = FakeRepository(flow { emit(listOf(task(1, "GEHEIMER-TITEL"))) })
        val viewModel = viewModelWith(repository)
        viewModel.onStatusChange(TaskId(1), TaskStatus.DONE)
        assertFalse(viewModel.statusAnnouncement.value.toString().contains("GEHEIMER"))
    }
}
