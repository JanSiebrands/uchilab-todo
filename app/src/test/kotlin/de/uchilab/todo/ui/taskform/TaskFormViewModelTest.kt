package de.uchilab.todo.ui.taskform

import androidx.lifecycle.SavedStateHandle
import de.uchilab.todo.domain.Task
import de.uchilab.todo.domain.TaskContent
import de.uchilab.todo.domain.TaskId
import de.uchilab.todo.domain.TaskRepository
import de.uchilab.todo.domain.TaskStatus
import de.uchilab.todo.domain.WriteError
import de.uchilab.todo.domain.WriteResult
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Plain JVM test with a fake repository. Invented data only. */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskFormViewModelTest {

    private class FakeRepository : TaskRepository {
        val added = mutableListOf<TaskContent>()
        var result: (TaskContent) -> WriteResult<Task> = { content ->
            WriteResult.Success(Task(TaskId(added.size.toLong()), content, Instant.EPOCH, TaskStatus.OPEN))
        }

        /** When set, [add] suspends until it completes. */
        var gate: CompletableDeferred<Unit>? = null

        override fun observeTasks(): Flow<List<Task>> = emptyFlow()

        override suspend fun add(content: TaskContent): WriteResult<Task> {
            added += content
            gate?.await()
            return result(content)
        }

        override suspend fun updateContent(id: TaskId, content: TaskContent) = error("not used")
        override suspend fun setStatus(id: TaskId, status: TaskStatus) = error("not used")
        override suspend fun delete(id: TaskId) = error("not used")
    }

    private val repository = FakeRepository()
    private val handle = SavedStateHandle()
    private lateinit var viewModel: TaskFormViewModel
    private val family = "👨‍👩‍👧‍👦"

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = TaskFormViewModel(repository, handle)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun opened(): TaskFormViewModel = viewModel.also { it.open() }

    // --- opening ---

    @Test
    fun `a new view model is closed and empty`() {
        assertFalse(viewModel.state.open)
        assertEquals("", viewModel.state.title)
        assertEquals("", viewModel.state.note)
    }

    @Test
    fun `open shows empty fields with the focus on the title and no hints`() {
        opened()
        val state = viewModel.state
        assertTrue(state.open)
        assertEquals(TaskFormField.TITLE, state.focus)
        assertFalse(state.titleMissingShown || state.saveFailed || state.discardDialog)
    }

    @Test
    fun `open after an unsaved input starts with empty fields`() {
        opened().onTitleChange("Aufgabe 1")
        viewModel.onNoteChange("Notiz 1")
        viewModel.onDiscardConfirm()
        viewModel.open()
        assertEquals("", viewModel.state.title)
        assertEquals("", viewModel.state.note)
    }

    // --- typing ---

    @Test
    fun `pasted line breaks in the title become spaces at once`() {
        opened().onTitleChange("A\nB\r\nC")
        assertEquals("A B C", viewModel.state.title)
    }

    @Test
    fun `line breaks in the note stay`() {
        opened().onNoteChange("Zeile 1\nZeile 2")
        assertEquals("Zeile 1\nZeile 2", viewModel.state.note)
    }

    @Test
    fun `text above the limit is not cut off`() {
        opened().onTitleChange("a".repeat(250))
        viewModel.onNoteChange("b".repeat(2500))
        assertEquals(250, viewModel.state.title.length)
        assertEquals(2500, viewModel.state.note.length)
        assertTrue(viewModel.state.titleTooLong)
        assertTrue(viewModel.state.noteTooLong)
    }

    @Test
    fun `text far above the limit is cut at the protection limit without splitting a surrogate pair`() {
        opened().onNoteChange("b".repeat(50_000))
        assertEquals(40_000, viewModel.state.note.length)
        viewModel.onNoteChange("b".repeat(39_999) + "😀")
        assertEquals(39_999, viewModel.state.note.length)
        assertTrue(viewModel.state.note.none { it.isSurrogate() })
        viewModel.onTitleChange("a".repeat(5_000))
        assertEquals(4_000, viewModel.state.title.length)
        viewModel.onTitleChange("a".repeat(3_999) + "😀")
        assertEquals(3_999, viewModel.state.title.length)
    }

    @Test
    fun `the protection limit leaves room for 200 and 2000 emoji sequences`() {
        opened().onTitleChange(family.repeat(200))
        viewModel.onNoteChange(family.repeat(2000))
        assertEquals(200, viewModel.state.titleLength)
        assertEquals(2000, viewModel.state.noteLength)
    }

    @Test
    fun `the lengths are those of the domain rules`() {
        opened().onTitleChange("  " + family.repeat(200) + " ")
        viewModel.onNoteChange(" " + family.repeat(2000))
        assertEquals(TaskContent.titleLength(viewModel.state.title), viewModel.state.titleLength)
        assertEquals(200, viewModel.state.titleLength)
        assertEquals(2000, viewModel.state.noteLength)
        assertFalse(viewModel.state.titleTooLong || viewModel.state.noteTooLong)
        viewModel.onTitleChange(family.repeat(201))
        assertTrue(viewModel.state.titleTooLong)
    }

    @Test
    fun `the counters appear at 80 percent of the limits`() {
        opened().onTitleChange("a".repeat(159))
        viewModel.onNoteChange("b".repeat(1599))
        assertFalse(viewModel.state.titleCounterVisible || viewModel.state.noteCounterVisible)
        viewModel.onTitleChange("a".repeat(160))
        viewModel.onNoteChange("b".repeat(1600))
        assertTrue(viewModel.state.titleCounterVisible && viewModel.state.noteCounterVisible)
    }

    // --- saving: invalid ---

    @Test
    fun `a missing title is not saved, shows the hint and moves the focus to the title`() {
        for (empty in listOf("", "   ")) {
            val vm = TaskFormViewModel(repository, SavedStateHandle()).also { it.open() }
            vm.onFocusChange(TaskFormField.NOTE)
            vm.onTitleChange(empty)
            vm.onSave()
            assertTrue(vm.state.titleMissingShown)
            assertEquals(TaskFormField.TITLE, vm.state.focus)
            assertTrue(vm.state.open)
        }
        assertTrue(repository.added.isEmpty())
    }

    @Test
    fun `the missing title hint is not shown before the first save attempt`() {
        opened()
        assertFalse(viewModel.state.titleMissingShown)
        viewModel.onNoteChange("Notiz 1")
        assertFalse(viewModel.state.titleMissingShown)
    }

    @Test
    fun `the missing title hint disappears with the first character`() {
        opened().onSave()
        assertTrue(viewModel.state.titleMissingShown)
        viewModel.onTitleChange("A")
        assertFalse(viewModel.state.titleMissingShown)
    }

    @Test
    fun `repeated focus requests on the same field are distinguishable`() {
        opened().onSave()
        val first = viewModel.state.focusTick
        viewModel.onSave()
        assertTrue(viewModel.state.focusTick != first)
    }

    @Test
    fun `a title above the limit is not saved and the focus goes to the first faulty field`() {
        opened().onTitleChange("a".repeat(201))
        viewModel.onNoteChange("b".repeat(2001))
        viewModel.onFocusChange(TaskFormField.NOTE)
        viewModel.onSave()
        assertTrue(repository.added.isEmpty())
        assertEquals(TaskFormField.TITLE, viewModel.state.focus)
        viewModel.onTitleChange("a")
        viewModel.onSave()
        assertTrue(repository.added.isEmpty())
        assertEquals(TaskFormField.NOTE, viewModel.state.focus)
    }

    // --- saving: valid ---

    @Test
    fun `save stores the trimmed content, a blank note becomes none, and closes`() {
        opened().onTitleChange("  Aufgabe 1 ")
        viewModel.onNoteChange("  \n ")
        viewModel.onSave()
        assertEquals(1, repository.added.size)
        assertEquals("Aufgabe 1", repository.added[0].title)
        assertNull(repository.added[0].note)
        assertFalse(viewModel.state.open)
    }

    @Test
    fun `save keeps line breaks inside the note`() {
        opened().onTitleChange("Aufgabe 1")
        viewModel.onNoteChange("Zeile 1\nZeile 2")
        viewModel.onSave()
        assertEquals("Zeile 1\nZeile 2", repository.added.single().note)
    }

    @Test
    fun `exactly 200 and 2000 characters can be saved`() {
        opened().onTitleChange(family.repeat(200))
        viewModel.onNoteChange(family.repeat(2000))
        viewModel.onSave()
        assertEquals(1, repository.added.size)
    }

    @Test
    fun `a successful save reports the created id, announces it and clears the saved state`() {
        opened().onTitleChange("Aufgabe 1")
        viewModel.onSave()
        assertNotNull(viewModel.createdTaskId)
        assertTrue(viewModel.createdAnnouncement)
        assertTrue(handle.keys().isEmpty())
        viewModel.onCreatedHandled()
        assertNull(viewModel.createdTaskId)
    }

    @Test
    fun `saving twice quickly stores exactly one task`() {
        repository.gate = CompletableDeferred()
        opened().onTitleChange("Aufgabe 1")
        viewModel.onSave()
        viewModel.onSave()
        assertEquals(1, repository.added.size)
        repository.gate!!.complete(Unit)
        assertEquals(1, repository.added.size)
        assertFalse(viewModel.state.open)
    }

    @Test
    fun `a failed save keeps the input open with its text and shows the hint`() {
        repository.result = { WriteResult.Failure(WriteError.STORAGE_FAILURE) }
        opened().onTitleChange("Aufgabe 1")
        viewModel.onNoteChange("Notiz 1")
        viewModel.onSave()
        assertTrue(viewModel.state.open)
        assertTrue(viewModel.state.saveFailed)
        assertEquals("Aufgabe 1", viewModel.state.title)
        assertEquals("Notiz 1", viewModel.state.note)
        assertNull(viewModel.createdTaskId)
        assertFalse(viewModel.createdAnnouncement)
    }

    @Test
    fun `the save failed hint stays until the next attempt, which can succeed`() {
        var fail = true
        repository.result = { content ->
            if (fail) WriteResult.Failure(WriteError.STORAGE_FAILURE)
            else WriteResult.Success(Task(TaskId(1), content, Instant.EPOCH, TaskStatus.OPEN))
        }
        opened().onTitleChange("Aufgabe 1")
        viewModel.onSave()
        viewModel.onNoteChange("mehr")
        assertTrue(viewModel.state.saveFailed)
        fail = false
        viewModel.onSave()
        assertFalse(viewModel.state.open)
        assertFalse(viewModel.state.saveFailed)
        assertEquals(2, repository.added.size)
    }

    @Test
    fun `after a failed save the lock is released`() {
        repository.result = { WriteResult.Failure(WriteError.STORAGE_FAILURE) }
        opened().onTitleChange("Aufgabe 1")
        viewModel.onSave()
        viewModel.onSave()
        assertEquals(2, repository.added.size)
    }

    // --- closing ---

    @Test
    fun `close with empty or blank fields closes without asking`() {
        opened().onTitleChange("   ")
        viewModel.onNoteChange("\n ")
        viewModel.onCloseRequest()
        assertFalse(viewModel.state.open)
        assertFalse(viewModel.state.discardDialog)
        assertTrue(handle.keys().isEmpty())
    }

    @Test
    fun `close with text in either field asks first`() {
        opened().onNoteChange("Notiz 1")
        viewModel.onCloseRequest()
        assertTrue(viewModel.state.open)
        assertTrue(viewModel.state.discardDialog)
    }

    @Test
    fun `dismissing the dialog keeps the input and the text`() {
        opened().onTitleChange("Aufgabe 1")
        viewModel.onCloseRequest()
        viewModel.onDiscardDismiss()
        assertTrue(viewModel.state.open)
        assertFalse(viewModel.state.discardDialog)
        assertEquals("Aufgabe 1", viewModel.state.title)
    }

    @Test
    fun `a close request while the dialog is open closes only the dialog`() {
        opened().onTitleChange("Aufgabe 1")
        viewModel.onCloseRequest()
        viewModel.onCloseRequest()
        assertTrue(viewModel.state.open)
        assertFalse(viewModel.state.discardDialog)
        assertEquals("Aufgabe 1", viewModel.state.title)
    }

    @Test
    fun `confirming discards without saving and clears the saved state`() {
        opened().onTitleChange("Aufgabe 1")
        viewModel.onCloseRequest()
        viewModel.onDiscardConfirm()
        assertFalse(viewModel.state.open)
        assertTrue(repository.added.isEmpty())
        assertTrue(handle.keys().isEmpty())
        assertNull(viewModel.createdTaskId)
    }

    @Test
    fun `close and discard do nothing while saving`() {
        repository.gate = CompletableDeferred()
        opened().onTitleChange("Aufgabe 1")
        viewModel.onSave()
        viewModel.onCloseRequest()
        assertFalse(viewModel.state.discardDialog)
        viewModel.onDiscardConfirm()
        assertTrue(viewModel.state.open)
        repository.gate!!.complete(Unit)
        assertFalse(viewModel.state.open)
    }

    // --- process death ---

    @Test
    fun `a second view model with the same saved state has text, hints, dialog and focus`() {
        opened().onTitleChange("Aufgabe 1")
        viewModel.onNoteChange("Notiz 1\nZeile 2")
        viewModel.onFocusChange(TaskFormField.NOTE)
        viewModel.onCloseRequest()
        val restored = TaskFormViewModel(repository, handle).state
        assertTrue(restored.open)
        assertEquals("Aufgabe 1", restored.title)
        assertEquals("Notiz 1\nZeile 2", restored.note)
        assertTrue(restored.discardDialog)
        assertEquals(TaskFormField.NOTE, restored.focus)
    }

    @Test
    fun `hints survive a restore`() {
        opened().onSave()
        assertTrue(TaskFormViewModel(repository, handle).state.titleMissingShown)
        repository.result = { WriteResult.Failure(WriteError.STORAGE_FAILURE) }
        viewModel.onTitleChange("Aufgabe 1")
        viewModel.onSave()
        assertTrue(TaskFormViewModel(repository, handle).state.saveFailed)
    }

    @Test
    fun `a closed input leaves nothing in the saved state`() {
        opened().onTitleChange("Aufgabe 1")
        assertFalse(handle.keys().isEmpty())
        viewModel.onCloseRequest()
        viewModel.onDiscardConfirm()
        assertTrue(handle.keys().isEmpty())
        assertFalse(TaskFormViewModel(repository, handle).state.open)
    }

    // --- no content in text forms ---

    @Test
    fun `the text form of the state has neither title nor note`() {
        opened().onTitleChange("MARKER-Titel-91")
        viewModel.onNoteChange("MARKER-Notiz-91")
        val text = viewModel.state.toString()
        assertFalse(text.contains("MARKER"))
    }
}
