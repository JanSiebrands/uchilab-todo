package de.uchilab.todo.data

import android.content.Context
import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import androidx.room3.withWriteTransaction
import androidx.test.core.app.ApplicationProvider
import de.uchilab.todo.domain.Task
import de.uchilab.todo.domain.TaskContent
import de.uchilab.todo.domain.TaskId
import de.uchilab.todo.domain.TaskStatus
import de.uchilab.todo.domain.Validated
import de.uchilab.todo.domain.WriteError
import de.uchilab.todo.domain.WriteResult
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Controllable clock; the repository must not depend on it for ordering. */
private class TestClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = now
}

/** Tests against a real database file created through the production factory. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TaskRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = TestClock(Instant.ofEpochMilli(1_700_000_000_000))
    private var db = TaskDatabase.create(context)
    private var repository = RoomTaskRepository(db.taskDao(), clock)

    @Before
    fun cleanFiles() {
        db.close()
        context.deleteDatabase(TaskDatabase.DATABASE_NAME)
        reopen()
    }

    @After
    fun closeDb() = db.close()

    private fun reopen() {
        db.close()
        db = TaskDatabase.create(context)
        repository = RoomTaskRepository(db.taskDao(), clock)
    }

    private fun content(title: String, note: String? = null): TaskContent =
        (TaskContent.of(title, note) as Validated.Valid).content

    private fun add(title: String, note: String? = null): Task =
        (runBlocking { repository.add(content(title, note)) } as WriteResult.Success).value

    private fun load(): List<Task> = runBlocking { withTimeout(10_000) { repository.observeTasks().first() } }

    private fun titles() = load().map { it.content.title }

    private fun failure(result: WriteResult<*>): WriteError = (result as WriteResult.Failure).error

    @Test
    fun `empty storage yields an empty list`() {
        assertEquals(emptyList<Task>(), load())
    }

    @Test
    fun `a new task gets the time and status open and comes back unchanged`() {
        val added = add("MARKER-7f3a-Einkauf", "Milch\nBrot")
        val loaded = load().single()
        assertEquals(added, loaded)
        assertEquals(clock.now, loaded.createdAt)
        assertEquals(TaskStatus.OPEN, loaded.status)
        assertEquals("MARKER-7f3a-Einkauf", loaded.content.title)
        assertEquals("Milch\nBrot", loaded.content.note)
    }

    @Test
    fun `tasks come newest first`() {
        add("eins")
        add("zwei")
        add("drei")
        assertEquals(listOf("drei", "zwei", "eins"), titles())
    }

    @Test
    fun `order stays when the clock is set back, the time zone changes or the millisecond is equal`() {
        val zone = TimeZone.getDefault()
        try {
            add("a")
            clock.now = clock.now.minusSeconds(86_400) // clock set back
            add("b")
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"))
            add("c")
            add("d") // same millisecond as c
            assertEquals(listOf("d", "c", "b", "a"), titles())
        } finally {
            TimeZone.setDefault(zone)
        }
    }

    @Test
    fun `changing content or status keeps creation time and position`() {
        add("a")
        val middle = add("b")
        add("c")
        clock.now = clock.now.plusSeconds(60)
        runBlocking {
            assertTrue(repository.updateContent(middle.id, content("b neu", "Notiz")) is WriteResult.Success)
            assertTrue(repository.setStatus(middle.id, TaskStatus.DONE) is WriteResult.Success)
        }
        val loaded = load()
        assertEquals(listOf("c", "b neu", "a"), loaded.map { it.content.title })
        val changed = loaded[1]
        assertEquals("Notiz", changed.content.note)
        assertEquals(TaskStatus.DONE, changed.status)
        assertEquals(middle.createdAt, changed.createdAt)
        assertEquals(middle.id, changed.id)
        runBlocking { repository.setStatus(middle.id, TaskStatus.OPEN) }
        assertEquals(TaskStatus.OPEN, load()[1].status)
    }

    @Test
    fun `deleting removes only that task`() {
        add("a")
        val b = add("b")
        add("c")
        assertTrue(runBlocking { repository.delete(b.id) } is WriteResult.Success)
        assertEquals(listOf("c", "a"), titles())
    }

    @Test
    fun `deleting removes the row physically`() {
        val marked = add("MARKER-9c1d-Loeschen", "MARKER-9c1d-Notiz")
        add("bleibt")
        runBlocking { repository.delete(marked.id) }
        val remaining = runBlocking {
            db.useReaderConnection { connection ->
                connection.usePrepared("SELECT count(*) FROM task WHERE title LIKE ? OR note LIKE ?") { statement ->
                    statement.bindText(1, "%MARKER-9c1d%")
                    statement.bindText(2, "%MARKER-9c1d%")
                    statement.step()
                    statement.getLong(0)
                }
            }
        }
        assertEquals(0L, remaining)
        assertEquals(listOf("bleibt"), titles())
    }

    @Test
    fun `ids are never reused after deleting the newest task`() {
        add("a")
        val b = add("b")
        runBlocking { repository.delete(b.id) }
        val c = add("c")
        assertTrue(c.id.value > b.id.value)
    }

    @Test
    fun `tasks survive closing the database and opening it again`() {
        add("MARKER-1", "Notiz")
        val b = add("zwei")
        runBlocking { repository.setStatus(b.id, TaskStatus.DONE) }
        val before = load()
        reopen()
        assertEquals(before, load())
    }

    @Test
    fun `an aborted write transaction stores nothing and leaves other tasks alone`() {
        add("vorher")
        try {
            runBlocking {
                db.withWriteTransaction {
                    db.taskDao().insert(TaskEntity(0, "MARKER-halb", null, 1, "open"))
                    throw IllegalStateException("abort")
                }
            }
        } catch (_: IllegalStateException) {
            // expected
        }
        assertEquals(listOf("vorher"), titles())
    }

    @Test
    fun `a failing write reports STORAGE_FAILURE, does not crash and keeps the data`() {
        val a = add("a")
        for (event in listOf("INSERT", "UPDATE", "DELETE")) {
            runBlocking {
                db.useWriterConnection {
                    it.usePrepared("CREATE TRIGGER fail_$event BEFORE $event ON task BEGIN SELECT RAISE(ABORT, 'test'); END") { st -> st.step() }
                }
            }
        }
        runBlocking {
            assertEquals(WriteError.STORAGE_FAILURE, failure(repository.add(content("b"))))
            assertEquals(WriteError.STORAGE_FAILURE, failure(repository.updateContent(a.id, content("neu"))))
            assertEquals(WriteError.STORAGE_FAILURE, failure(repository.setStatus(a.id, TaskStatus.DONE)))
            assertEquals(WriteError.STORAGE_FAILURE, failure(repository.delete(a.id)))
        }
        assertEquals(listOf(a), load())
    }

    @Test
    fun `changing or deleting a missing task reports NOT_FOUND`() {
        val missing = TaskId(4711)
        runBlocking {
            assertEquals(WriteError.NOT_FOUND, failure(repository.updateContent(missing, content("x"))))
            assertEquals(WriteError.NOT_FOUND, failure(repository.setStatus(missing, TaskStatus.DONE)))
            assertEquals(WriteError.NOT_FOUND, failure(repository.delete(missing)))
        }
    }

    @Test
    fun `observers learn about every change without loading again`() = runBlocking {
        withTimeout(20_000) {
            val updates = repository.observeTasks().produceIn(this)
            assertEquals(emptyList<Task>(), updates.receive())
            val added = (repository.add(content("a")) as WriteResult.Success).value
            assertEquals(listOf("a"), updates.receive().map { it.content.title })
            repository.updateContent(added.id, content("b"))
            assertEquals(listOf("b"), updates.receive().map { it.content.title })
            repository.setStatus(added.id, TaskStatus.DONE)
            assertEquals(TaskStatus.DONE, updates.receive().single().status)
            repository.delete(added.id)
            assertEquals(emptyList<Task>(), updates.receive())
            updates.cancel()
        }
    }

    @Test
    fun `special characters come back unchanged`() {
        val samples = listOf(
            "Äpfel, Öl und Übung ß",
            "👨‍👩‍👧 👍🏽",
            "שלום עולם", "مرحبا بالعالم",
            "é ä ñ",
            "it's \"quoted\" `tick` back\\slash",
            "100% fertig_ja; DROP TABLE task; --", "/* kommentar */", "NULL",
            "x\u0000y",
        )
        for (sample in samples) {
            add(sample, "$sample\nZeile 2\r\nZeile 3")
        }
        val loaded = load().reversed()
        assertEquals(samples, loaded.map { it.content.title })
        assertEquals(samples.map { "$it\nZeile 2\r\nZeile 3" }, loaded.map { it.content.note })
    }

    @Test
    fun `stored and loaded values are trimmed, also when changing`() {
        val added = add("  Milch ", "  Notiz \n")
        assertEquals("Milch", load().single().content.title)
        assertEquals("Notiz", load().single().content.note)
        runBlocking { repository.updateContent(added.id, content("  Brot\nButter ", "   ")) }
        val changed = load().single()
        assertEquals("Brot Butter", changed.content.title)
        assertEquals(null, changed.content.note)
    }

    @Test
    fun `1000 tasks can be stored and loaded in order`() {
        repeat(1000) { add("Aufgabe $it") }
        val loaded = load()
        assertEquals(1000, loaded.size)
        assertEquals((999 downTo 0).map { "Aufgabe $it" }, loaded.map { it.content.title })
    }

    @Test
    fun `the database lives in the databases folder and nowhere else`() {
        add("MARKER-pfad")
        val file = context.getDatabasePath(TaskDatabase.DATABASE_NAME)
        assertTrue(file.exists())
        assertEquals(File(context.dataDir, "databases"), file.parentFile)
        assertFalse(TaskDatabase.DATABASE_NAME.contains("/"))
        val others = listOfNotNull(
            context.filesDir, context.cacheDir, context.noBackupFilesDir,
            context.externalCacheDir, context.getExternalFilesDir(null),
        )
        for (dir in others) {
            val found = dir.walkTopDown().filter { it.isFile && it.name.startsWith("tasks") }.toList()
            assertTrue("unexpected database file outside databases/", found.isEmpty())
        }
    }

    @Test
    fun `the field names exist only once in the table`() {
        add("a")
        val columns = runBlocking {
            db.useReaderConnection { connection ->
                connection.usePrepared("SELECT name FROM pragma_table_info('task') ORDER BY cid") { statement ->
                    buildList { while (statement.step()) add(statement.getText(0)) }
                }
            }
        }
        assertEquals(listOf("id", "title", "note", "created_at", "status"), columns)
    }

    @Test
    fun `toString of a stored entity shows no title or note`() {
        val entity = TaskEntity(1, "MARKER-title", "MARKER-note", 0, "open")
        assertFalse(entity.toString().contains("MARKER"))
    }
}
