package de.uchilab.todo.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.uchilab.todo.domain.TaskContent
import de.uchilab.todo.domain.TaskStatus
import de.uchilab.todo.domain.Validated
import de.uchilab.todo.domain.WriteResult
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Writes the seed database for the emulator check (tools/checks/emulator-tasks.sh, plan UCH-58 section 7).
 * Only active with the system property `uchilab.seedOut` (Gradle: -Puchilab.seedOut=<dir>), otherwise skipped.
 * The tasks are created through the production code of S2 (TaskDatabase.create + RoomTaskRepository) and are
 * invented. Output: <dir>/tasks.db and <dir>/titles.txt (expected titles in list order, one per line).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EmulatorSeedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun writeSeed() = runBlocking {
        val out = System.getProperty(SEED_OUT_PROPERTY)
        assumeTrue("only with -P$SEED_OUT_PROPERTY", !out.isNullOrBlank())
        val outDir = File(out!!)

        context.deleteDatabase(TaskDatabase.DATABASE_NAME)
        val db = TaskDatabase.create(context)
        val repository = RoomTaskRepository(db.taskDao())
        // Unique per run, so a stale database on the device can never pass as this seed.
        val runId = UUID.randomUUID().toString().take(8)
        val first = repository.add(content("Seed-1-$runId", "Erste Zeile der Notiz\nZweite Zeile der Notiz"))
        repository.add(content("Seed-2-$runId", null))
        val third = repository.add(content("Seed-3-$runId", "Erledigt"))
        assertTrue(first is WriteResult.Success)
        val doneId = (third as WriteResult.Success).value.id
        assertTrue(repository.setStatus(doneId, TaskStatus.DONE) is WriteResult.Success)

        // Contract of the repository: newest first. This is the order the list must show.
        val expected = listOf("Seed-3-$runId", "Seed-2-$runId", "Seed-1-$runId")
        assertEquals(expected, repository.observeTasks().first().map { it.content.title })

        // Closing the last connection checkpoints the WAL into the main file; nothing may remain next to it.
        db.close()
        val file = context.getDatabasePath(TaskDatabase.DATABASE_NAME)
        val wal = File(file.path + "-wal")
        assertTrue("WAL not checkpointed", !wal.exists() || wal.length() == 0L)

        outDir.deleteRecursively()
        assertTrue(outDir.mkdirs())
        file.copyTo(File(outDir, TaskDatabase.DATABASE_NAME))
        File(outDir, "titles.txt").writeText(expected.joinToString("\n", postfix = "\n"))
    }

    private fun content(title: String, note: String?): TaskContent =
        (TaskContent.of(title, note) as Validated.Valid).content

    private companion object {
        const val SEED_OUT_PROPERTY = "uchilab.seedOut"
    }
}
