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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import de.uchilab.todo.domain.ListSettings
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

private class FakeListSettings(
    initial: Boolean = false,
    var result: WriteResult<Unit> = WriteResult.Success(Unit),
) : ListSettings {
    private val value = MutableStateFlow(initial)
    val writes = mutableListOf<Boolean>()
    override val hideDone: Flow<Boolean> = value
    override suspend fun setHideDone(hide: Boolean): WriteResult<Unit> {
        writes += hide
        if (result is WriteResult.Success) value.value = hide
        return result
    }
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
        val viewModel = TaskListViewModel(FakeRepository(source), FakeListSettings())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        assertSame(TaskListUiState.Loading, viewModel.state.value)

        source.emit(listOf(task(2, "zwei"), task(1, "eins")))
        val loaded = viewModel.state.value as TaskListUiState.Loaded
        assertEquals(listOf(2L, 1L), loaded.open.map { it.id.value })
    }

    @Test
    fun `an empty stored list is loaded and not loading`() = runTest {
        val viewModel = TaskListViewModel(FakeRepository(flow { emit(emptyList()) }), FakeListSettings())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        val loaded = viewModel.state.value as TaskListUiState.Loaded
        assertTrue(loaded.open.isEmpty() && loaded.done.isEmpty())
    }

    @Test
    fun `a read failure becomes the read failed state`() = runTest {
        val viewModel = TaskListViewModel(FakeRepository(flow { throw TaskReadException() }), FakeListSettings())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        assertSame(TaskListUiState.ReadFailed, viewModel.state.value)
    }

    @Test
    fun `a read failure after loaded data replaces the list`() = runTest {
        val source = flow {
            emit(listOf(task(1, "eins")))
            throw TaskReadException()
        }
        val states = source.toUiState(false).toList()
        assertEquals(2, states.size)
        assertTrue(states[0] is TaskListUiState.Loaded)
        assertSame(TaskListUiState.ReadFailed, states[1])
    }

    @Test
    fun `other exceptions are not swallowed`() = runTest {
        val failure = IllegalStateException("kein Lesefehler")
        try {
            flow<List<Task>> { throw failure }.toUiState(false).toList()
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
            flow<List<Task>> { throw cancellation }.toUiState(false).collect { seen += it }
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
        val viewModel = TaskListViewModel(FakeRepository(source), FakeListSettings())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        source.emit(listOf(task(2, "alt", TaskStatus.DONE), task(1, "alt", TaskStatus.DONE)))
        source.emit(listOf(task(3, "neu")) + listOf(task(2, "alt", TaskStatus.DONE), task(1, "alt", TaskStatus.DONE)))
        val loaded = viewModel.state.value as TaskListUiState.Loaded
        assertEquals(3L, loaded.open.first().id.value)
        assertEquals(listOf(2L, 1L), loaded.done.map { it.id.value })
    }

    // --- changing the status (Z13, Z21) ---

    private fun viewModelWith(repository: FakeRepository, settings: FakeListSettings = FakeListSettings()) =
        TaskListViewModel(repository, settings)

    @Test
    fun `a successful change writes the target status and announces it without a failure notice`() = runTest {
        val repository = FakeRepository(flow { emit(listOf(task(1, "eins"))) })
        val viewModel = viewModelWith(repository)

        viewModel.onStatusChange(TaskId(1), TaskStatus.DONE)

        assertEquals(listOf(TaskId(1) to TaskStatus.DONE), repository.statusWrites)
        assertEquals(null, viewModel.notice.value)
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

        assertEquals(ListNotice.StatusSaveFailed, viewModel.notice.value)
        assertEquals(null, viewModel.statusAnnouncement.value)
        assertSame(before, viewModel.state.value)
    }

    @Test
    fun `a task that is gone also counts as a failed save`() = runTest {
        val repository = FakeRepository(emptyFlow(), WriteResult.Failure(WriteError.NOT_FOUND))
        val viewModel = viewModelWith(repository)
        viewModel.onStatusChange(TaskId(9), TaskStatus.OPEN)
        assertEquals(ListNotice.StatusSaveFailed, viewModel.notice.value)
        assertEquals(null, viewModel.statusAnnouncement.value)
    }

    @Test
    fun `the failure notice is cleared once it was shown`() = runTest {
        val repository = FakeRepository(emptyFlow(), WriteResult.Failure(WriteError.STORAGE_FAILURE))
        val viewModel = viewModelWith(repository)
        viewModel.onStatusChange(TaskId(1), TaskStatus.DONE)
        viewModel.onNoticeShown(viewModel.notice.value!!)
        assertEquals(null, viewModel.notice.value)
    }

    @Test
    fun `the announcement does not reveal a title in its text form`() = runTest {
        val repository = FakeRepository(flow { emit(listOf(task(1, "GEHEIMER-TITEL"))) })
        val viewModel = viewModelWith(repository)
        viewModel.onStatusChange(TaskId(1), TaskStatus.DONE)
        assertFalse(viewModel.statusAnnouncement.value.toString().contains("GEHEIMER"))
    }

    // --- hiding done tasks (S7) ---

    @Test
    fun `the state stays loading until the setting has arrived too`() = runTest {
        val setting = MutableSharedFlow<Boolean>()
        val settings = object : ListSettings {
            override val hideDone: Flow<Boolean> = setting
            override suspend fun setHideDone(hide: Boolean): WriteResult<Unit> = error("not used")
        }
        val viewModel = TaskListViewModel(FakeRepository(flow { emit(listOf(task(1, "eins", TaskStatus.DONE))) }), settings)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        assertSame(TaskListUiState.Loading, viewModel.state.value)
        setting.emit(true)
        assertTrue((viewModel.state.value as TaskListUiState.Loaded).hideDone)
    }

    @Test
    fun `the state carries the stored setting and all done tasks even while they are hidden`() = runTest {
        val repository = FakeRepository(flow { emit(listOf(task(2, "zwei", TaskStatus.DONE), task(1, "eins"))) })
        val viewModel = viewModelWith(repository, FakeListSettings(initial = true))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        val loaded = viewModel.state.value as TaskListUiState.Loaded
        assertTrue(loaded.hideDone)
        assertEquals(listOf(2L), loaded.done.map { it.id.value })
        assertEquals(listOf(1L), loaded.open.map { it.id.value })
    }

    @Test
    fun `a hide change writes the target value, the state follows the stored value`() = runTest {
        val settings = FakeListSettings()
        val viewModel = viewModelWith(FakeRepository(flow { emit(listOf(task(1, "eins", TaskStatus.DONE))) }), settings)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        viewModel.onHideDoneChange(true)
        assertEquals(listOf(true), settings.writes)
        assertTrue((viewModel.state.value as TaskListUiState.Loaded).hideDone)
        assertEquals(null, viewModel.notice.value)

        viewModel.onHideDoneChange(false)
        assertEquals(listOf(true, false), settings.writes)
        assertFalse((viewModel.state.value as TaskListUiState.Loaded).hideDone)
    }

    // Z21
    @Test
    fun `a failing setting write keeps the shown value and raises the setting notice`() = runTest {
        val settings = FakeListSettings(result = WriteResult.Failure(WriteError.STORAGE_FAILURE))
        val viewModel = viewModelWith(FakeRepository(flow { emit(listOf(task(1, "eins", TaskStatus.DONE))) }), settings)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        viewModel.onHideDoneChange(true)

        assertFalse((viewModel.state.value as TaskListUiState.Loaded).hideDone)
        assertEquals(ListNotice.SettingSaveFailed, viewModel.notice.value)
    }

    // Z9
    @Test
    fun `checking off while done tasks are hidden raises the hidden notice and no announcement`() = runTest {
        val viewModel = viewModelWith(FakeRepository(flow { emit(listOf(task(1, "eins"))) }), FakeListSettings(initial = true))

        viewModel.onStatusChange(TaskId(1), TaskStatus.DONE)

        val notice = viewModel.notice.value as ListNotice.DoneHidden
        assertEquals(TaskId(1), notice.id)
        assertEquals(null, viewModel.statusAnnouncement.value)
    }

    @Test
    fun `checking off while done tasks are shown announces as before and raises no notice`() = runTest {
        val viewModel = viewModelWith(FakeRepository(flow { emit(listOf(task(1, "eins"))) }), FakeListSettings(initial = false))

        viewModel.onStatusChange(TaskId(1), TaskStatus.DONE)

        assertEquals(null, viewModel.notice.value)
        assertEquals(StatusChange(TaskId(1), TaskStatus.DONE), viewModel.statusAnnouncement.value)
    }

    @Test
    fun `a failed check off while done tasks are hidden is a failure, not the hidden notice`() = runTest {
        val repository = FakeRepository(emptyFlow(), WriteResult.Failure(WriteError.STORAGE_FAILURE))
        val viewModel = viewModelWith(repository, FakeListSettings(initial = true))

        viewModel.onStatusChange(TaskId(1), TaskStatus.DONE)

        assertEquals(ListNotice.StatusSaveFailed, viewModel.notice.value)
    }

    @Test
    fun `a newer notice is not cleared when the older one is reported as shown`() = runTest {
        val repository = FakeRepository(emptyFlow(), WriteResult.Failure(WriteError.STORAGE_FAILURE))
        val settings = FakeListSettings(result = WriteResult.Failure(WriteError.STORAGE_FAILURE))
        val viewModel = viewModelWith(repository, settings)
        viewModel.onStatusChange(TaskId(1), TaskStatus.DONE)
        val older = viewModel.notice.value!!
        viewModel.onHideDoneChange(true) // a different notice replaces the first one
        val newer = viewModel.notice.value!!
        assertTrue(newer !== older)

        viewModel.onNoticeShown(older)

        assertSame(newer, viewModel.notice.value)
    }

    @Test
    fun `the notice and the state do not reveal a title in their text form`() = runTest {
        val viewModel = viewModelWith(FakeRepository(flow { emit(listOf(task(1, "GEHEIMER-TITEL"))) }), FakeListSettings(initial = true))
        viewModel.onStatusChange(TaskId(1), TaskStatus.DONE)
        assertFalse(viewModel.notice.value.toString().contains("GEHEIMER"))
    }
}
