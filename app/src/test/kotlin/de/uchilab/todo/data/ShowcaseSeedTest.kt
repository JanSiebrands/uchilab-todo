package de.uchilab.todo.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.uchilab.todo.domain.TaskContent
import de.uchilab.todo.domain.TaskStatus
import de.uchilab.todo.domain.Validated
import de.uchilab.todo.domain.WriteResult
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Writes the readable seed databases for the screenshots of the feature pull request
 * (tools/checks/emulator-showcase.sh, plan UCH-81 section 4). Only active with the system property
 * `uchilab.showcaseOut` (Gradle: -Puchilab.showcaseOut=<dir>), otherwise skipped. Same production code as
 * EmulatorSeedTest (TaskDatabase.create + RoomTaskRepository). Invented tasks only, no names of real people, no
 * identifiers. Output per language: <dir>/<de|en>/tasks.db, titles.txt (titles in list order, newest first) and
 * tasks.json (title and note in list order, for the note check on the emulator).
 * Additionally (UCH-130, plan UCH-122 Z27-Z29) seeds with done tasks, marked done via TaskRepository.setStatus:
 * done-de/ and done-en/ (open and done mixed) and all-done-en/, all-done-de/ (only done tasks, UCH-168), same files.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShowcaseSeedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun writeShowcaseSeed() = runBlocking {
        val out = System.getProperty(SHOWCASE_OUT_PROPERTY)
        assumeTrue("only with -P$SHOWCASE_OUT_PROPERTY", !out.isNullOrBlank())
        val outDir = File(out!!)
        outDir.deleteRecursively()
        TASKS.forEach { (language, tasks) -> writeLanguage(File(outDir, language), tasks) }
        DONE_SEEDS.forEach { (seed, tasks) -> writeLanguage(File(outDir, seed), tasks.first, tasks.second) }
    }

    /** [tasks] in the order they are added, so the last one is on top of the list; titles in [done] are set done. */
    private suspend fun writeLanguage(dir: File, tasks: List<Pair<String, String?>>, done: Set<String> = emptySet()) {
        context.deleteDatabase(TaskDatabase.DATABASE_NAME)
        val db = TaskDatabase.create(context)
        val repository = RoomTaskRepository(db.taskDao())
        tasks.forEach { (title, note) ->
            assertTrue(repository.add((TaskContent.of(title, note) as Validated.Valid).content) is WriteResult.Success)
        }
        repository.observeTasks().first().filter { it.content.title in done }.forEach {
            assertTrue(repository.setStatus(it.id, TaskStatus.DONE) is WriteResult.Success)
        }
        val shown = repository.observeTasks().first()
        // Contract of the repository: newest first.
        assertEquals(tasks.reversed(), shown.map { it.content.title to it.content.note })
        assertEquals(done, shown.filter { it.status == TaskStatus.DONE }.map { it.content.title }.toSet())

        // Closing the last connection checkpoints the WAL into the main file; nothing may remain next to it.
        db.close()
        val file = context.getDatabasePath(TaskDatabase.DATABASE_NAME)
        val wal = File(file.path + "-wal")
        assertTrue("WAL not checkpointed", !wal.exists() || wal.length() == 0L)

        assertTrue(dir.mkdirs())
        file.copyTo(File(dir, TaskDatabase.DATABASE_NAME))
        File(dir, "titles.txt").writeText(shown.joinToString("\n", postfix = "\n") { it.content.title })
        val json = JSONArray()
        shown.forEach { json.put(JSONObject().put("title", it.content.title).put("note", it.content.note ?: JSONObject.NULL)) }
        File(dir, "tasks.json").writeText(json.toString(2) + "\n")
    }

    private companion object {
        const val SHOWCASE_OUT_PROPERTY = "uchilab.showcaseOut"

        // Invented everyday tasks: one note over two lines, one note on one line, two without a note.
        val TASKS = mapOf(
            "de" to listOf(
                "Fahrrad zur Werkstatt bringen" to "Hinterreifen verliert Luft\nVorher anrufen",
                "Balkonpflanzen gießen" to null,
                "Steuererklärung abgeben" to "Belege liegen im blauen Ordner",
                "Geschenk für den Geburtstag besorgen" to null,
            ),
            "en" to listOf(
                "Take the bike to the repair shop" to "Rear tyre keeps losing air\nCall ahead first",
                "Water the balcony plants" to null,
                "File the tax return" to "Receipts are in the blue folder",
                "Buy a birthday present" to null,
            ),
        )

        // Invented everyday tasks with done ones (Design UCH-119, screenshots): three open and two done, one done
        // task with a note; and only done tasks.
        val DONE_SEEDS = mapOf(
            "done-de" to Pair(
                listOf(
                    "Altglas wegbringen" to null,
                    "Zahnarzttermin vereinbaren" to "Vormittags passt am besten",
                    "Balkonpflanzen gießen" to null,
                    "Steuererklärung abgeben" to "Belege liegen im blauen Ordner",
                    "Geschenk für den Geburtstag besorgen" to null,
                ),
                setOf("Altglas wegbringen", "Zahnarzttermin vereinbaren"),
            ),
            "done-en" to Pair(
                listOf(
                    "Take the bottles to recycling" to null,
                    "Book a dentist appointment" to "Mornings work best",
                    "Water the balcony plants" to null,
                    "File the tax return" to "Receipts are in the blue folder",
                    "Buy a birthday present" to null,
                ),
                setOf("Take the bottles to recycling", "Book a dentist appointment"),
            ),
            "all-done-en" to Pair(
                listOf(
                    "Take the bottles to recycling" to null,
                    "Book a dentist appointment" to "Mornings work best",
                    "Water the balcony plants" to null,
                ),
                setOf("Take the bottles to recycling", "Book a dentist appointment", "Water the balcony plants"),
            ),
            "all-done-de" to Pair(
                listOf(
                    "Altglas wegbringen" to null,
                    "Zahnarzttermin vereinbaren" to "Vormittags passt am besten",
                    "Balkonpflanzen gießen" to null,
                ),
                setOf("Altglas wegbringen", "Zahnarzttermin vereinbaren", "Balkonpflanzen gießen"),
            ),
        )
    }
}
