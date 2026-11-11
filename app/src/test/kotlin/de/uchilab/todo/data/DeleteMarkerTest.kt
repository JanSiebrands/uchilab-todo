package de.uchilab.todo.data

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import de.uchilab.todo.domain.ListSettings
import de.uchilab.todo.domain.Task
import de.uchilab.todo.domain.TaskContent
import de.uchilab.todo.domain.Validated
import de.uchilab.todo.domain.WriteResult
import de.uchilab.todo.ui.taskform.TaskFormViewModel
import de.uchilab.todo.ui.tasklist.ListNotice
import de.uchilab.todo.ui.tasklist.TaskListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Privacy V4: a deleted task is gone from the file at once, undo only re-inserts from the copy that lives in the
 * list view model. Real Room file, real repository, the two view models wired as in `MainActivity`.
 * Marker texts are invented.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DeleteMarkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var db = TaskDatabase.create(context)
    private var repository = RoomTaskRepository(db.taskDao())
    private val titleMarker = "MARKER-t9x2-Titel"
    private val noteMarker = "MARKER-n4k8-Notiz"
    private lateinit var list: TaskListViewModel
    private lateinit var form: TaskFormViewModel

    private val settings = object : ListSettings {
        override val hideDone: Flow<Boolean> = flowOf(false)
        override suspend fun setHideDone(hide: Boolean): WriteResult<Unit> = WriteResult.Success(Unit)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db.close()
        context.deleteDatabase(TaskDatabase.DATABASE_NAME)
        reopen()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun reopen() {
        db.close()
        db = TaskDatabase.create(context)
        repository = RoomTaskRepository(db.taskDao())
        list = TaskListViewModel(repository, settings)
        form = TaskFormViewModel(repository, SavedStateHandle(), list::onTaskDeleted)
    }

    private fun add(title: String, note: String?): Task {
        val content = (TaskContent.of(title, note) as Validated.Valid).content
        return (runBlocking { repository.add(content) } as WriteResult.Success).value
    }

    private fun stored(): List<Task> = runBlocking { withTimeout(10_000) { repository.observeTasks().first() } }

    private fun awaitUntil(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "condition not reached" }
            Thread.sleep(10)
        }
    }

    private fun deleteThroughForm(task: Task): ListNotice {
        form.openEdit(task)
        form.onDelete()
        awaitUntil { !form.state.open }
        return list.notice.value!!
    }

    /** Asks the file itself: every table, every column, for the id and for both markers. */
    private fun assertNothingFound(task: Task) {
        val path = context.getDatabasePath(TaskDatabase.DATABASE_NAME).path
        val raw = android.database.sqlite.SQLiteDatabase.openDatabase(path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
        try {
            val tables = mutableListOf<String>()
            raw.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { while (it.moveToNext()) tables += it.getString(0) }
            assertTrue("no table found", "task" in tables)
            for (table in tables) {
                val columns = mutableListOf<String>()
                raw.rawQuery("PRAGMA table_info(\"$table\")", null).use { while (it.moveToNext()) columns += it.getString(1) }
                if ("id" in columns) {
                    raw.rawQuery("SELECT COUNT(*) FROM \"$table\" WHERE id = ?", arrayOf(task.id.value.toString())).use {
                        it.moveToFirst()
                        assertEquals("id still in $table", 0, it.getInt(0))
                    }
                }
                for (column in columns) for (marker in listOf(titleMarker, noteMarker)) {
                    raw.rawQuery("SELECT COUNT(*) FROM \"$table\" WHERE CAST(\"$column\" AS TEXT) LIKE ?", arrayOf("%$marker%")).use {
                        it.moveToFirst()
                        assertEquals("marker still in $table.$column", 0, it.getInt(0))
                    }
                }
            }
        } finally {
            raw.close()
        }
    }

    // Z4
    @Test
    fun `right after deleting no query finds the id or the markers`() {
        val task = add(titleMarker, noteMarker)
        add("Aufgabe 2", null)

        val notice = deleteThroughForm(task)

        assertTrue(notice is ListNotice.Deleted)
        assertNothingFound(task)
    }

    // Z5
    @Test
    fun `undo puts the task back with the same content`() {
        val task = add(titleMarker, noteMarker)
        val notice = deleteThroughForm(task)

        list.onUndo(notice)
        awaitUntil { stored().isNotEmpty() }

        assertEquals(listOf(task), stored())
    }

    // Z6
    @Test
    fun `when the offer ends the file has nothing and a late undo does nothing`() {
        val task = add(titleMarker, noteMarker)
        deleteThroughForm(task).also { list.onUndo(it) }
        awaitUntil { stored().isNotEmpty() }

        val notice = deleteThroughForm(stored().single())
        list.onNoticeShown(notice)
        assertNull(list.notice.value)
        list.onUndo(notice)
        Thread.sleep(300)

        assertTrue(stored().isEmpty())
        assertNothingFound(task)
    }

    // Z7
    @Test
    fun `after closing and opening the database the task is still gone`() {
        val task = add(titleMarker, noteMarker)
        val notice = deleteThroughForm(task)
        list.onNoticeShown(notice)

        reopen()

        assertTrue(stored().isEmpty())
        assertNothingFound(task)
    }
}
