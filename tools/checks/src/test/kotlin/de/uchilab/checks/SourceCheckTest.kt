package de.uchilab.checks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceCheckTest {

    private fun check(line: String, path: String = MAIN_FILE) = SourceCheck.check(
        mapOf(MAIN_FILE to "package x\n", path to "package x\n\nfun f() {\n    $line\n}\n"),
    )

    /**
     * Every alternative of every rule (plan UCH-49 section 3): offending line, the rule id it must report,
     * and a similar line that must stay green.
     */
    private val cases = listOf(
        Triple("""android.util.Log.d("tag", "msg")""", "android.util.Log", """Logo.d("tag", "msg")"""),
        Triple("""Log.e("tag", "msg")""", "android.util.Log", """dialog.e("tag")"""),
        Triple("""Log.v("tag", "msg")""", "android.util.Log", """Log.verbose"""),
        Triple("""Log.d("tag", "msg")""", "android.util.Log", """Log.debugLevel"""),
        Triple("""Log.i("tag", "msg")""", "android.util.Log", """Log.info"""),
        Triple("""Log.w("tag", "msg")""", "android.util.Log", """Log.warnings"""),
        Triple("""Log.wtf("tag", "msg")""", "android.util.Log", """Log.wtfCount"""),
        Triple("""Log.println(Log.INFO, "tag", "msg")""", "android.util.Log", """Log.printlnCount"""),
        Triple("""import android.util.Log as L""", "android.util.Log", """import android.util.Logger"""),
        Triple("""println("x")""", "println/print", """printlnLater("x")"""),
        Triple("""print("x")""", "println/print", """blueprint("x")"""),
        Triple("""System.err.write(1)""", "System.out/System.err", """System.errorCount()"""),
        Triple("""System.out.flush()""", "System.out/System.err", """System.output()"""),
        Triple("""e.printStackTrace()""", "printStackTrace", """e.stackTrace"""),
        Triple("""Timber.d("x")""", "logging library", """TimberYard.d("x")"""),
        Triple("""import timber.log.Tree""", "logging library", """import timberx.logx.Tree"""),
        Triple("""import org.slf4j.LoggerFactory""", "logging library", """import org.slf4jx.Foo"""),
        Triple("""import java.util.logging.Logger""", "logging library", """import java.util.loggingx.Foo"""),
        Triple("""builder.setQueryCallback(cb, executor)""", "Room query log", """builder.setQueryCallbackCount(1)"""),
        Triple("""context.getExternalFilesDir(null)""", "external storage", """context.getInternalFilesDir(null)"""),
        Triple("""val d = context.externalCacheDir""", "external storage", """val d = context.internalFilesDir"""),
        Triple("""val d = context.externalMediaDirs""", "external storage", """val d = context.internalMediaDirs"""),
        Triple("""context.getExternalMediaDirs()""", "external storage", """context.getInternalMediaDirs()"""),
        Triple("""context.getExternalFilesDirs(null)""", "external storage", """context.getExternalFilesDirectory(null)"""),
        Triple("""context.getExternalCacheDirs()""", "external storage", """context.getExternalCacheDirectory()"""),
        Triple("""Environment.getExternalStorageDirectory()""", "external storage", """getExternalStor()"""),
        Triple("""MediaStore.Images.Media.EXTERNAL_CONTENT_URI""", "external storage", """MediaStorage.x"""),
        Triple("""openFileOutput("a", MODE_WORLD_READABLE)""", "world readable files", """openFileOutput("a", MODE_PRIVATE)"""),
        Triple("""openFileOutput("a", MODE_WORLD_WRITEABLE)""", "world readable files", """MODE_WORLD_WRITEABLE_X"""),
        Triple("""Intent(Intent.ACTION_CREATE_DOCUMENT)""", "Storage Access Framework", """Intent(Intent.ACTION_GET_CONTENT)"""),
        Triple("""Intent(Intent.ACTION_OPEN_DOCUMENT)""", "Storage Access Framework", """Intent(Intent.ACTION_OPEN_DOCUMENTS)"""),
        Triple("""Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)""", "Storage Access Framework", """Intent(Intent.ACTION_OPEN_DOCUMENT_TREES)"""),
        Triple("""Intent(ACTION_CREATE_DOCUMENT_TREE)""", "Storage Access Framework", """Intent(ACTION_CREATE_DOCUMENT_TREEX)"""),
        Triple("""DocumentsContract.buildDocumentUri(a, b)""", "Storage Access Framework", """MyDocumentsContracts.x"""),
        Triple("""registerForActivityResult(CreateDocument("text/plain"))""", "Storage Access Framework", """CreateDocumentation()"""),
        Triple("""registerForActivityResult(OpenDocument())""", "Storage Access Framework", """OpenDocumentation()"""),
        Triple("""registerForActivityResult(OpenDocumentTree())""", "Storage Access Framework", """OpenDocumentTrees()"""),
        Triple("""registerForActivityResult(OpenMultipleDocuments())""", "Storage Access Framework", """OpenMultipleDocumentsX()"""),
        Triple("""context.createDeviceProtectedStorageContext()""", "Direct Boot storage", """context.createDeviceProtectedStorage()"""),
        Triple("""File(context.cacheDir, "a")""", "cache and temp files", """File(context.catalogDir, "a")"""),
        Triple("""File(context.getCacheDir(), "a")""", "cache and temp files", """File(context.getCacheDirectory(), "a")"""),
        Triple("""File(context.CacheDir, "a")""", "cache and temp files", """File(context.CacheDirs, "a")"""),
        Triple("""File.createTempFile("a", "b")""", "cache and temp files", """File.createTemporary("a", "b")"""),
        Triple("""builder.fallbackToDestructiveMigration(true)""", "destructive migration", """builder.fallbackToMigration(true)"""),
        Triple("""builder.fallbackToDestructiveMigrationOnDowngrade(true)""", "destructive migration", """noFallbackToDestructiveMigration()"""),
        Triple("""builder.fallbackToDestructiveMigrationFrom(true, 1)""", "destructive migration", """fallbackToDestructive(1)"""),
        Triple("""MigrationStrategy(allowDataLossOnRecovery = true)""", "destructive migration", """MigrationStrategy(allowDataLoss = true)"""),
        Triple("""dropAllTables(connection)""", "destructive migration", """dropAllTablesLater(connection)"""),
        Triple("""builder.createFromAsset("tasks.db")""", "prepackaged database", """builder.createFromAssets("tasks.db")"""),
        Triple("""builder.createFromFile(file)""", "prepackaged database", """builder.createFromFiles(file)"""),
        Triple("""builder.createFromInputStream { stream }""", "prepackaged database", """builder.createFromStream { stream }"""),
        Triple("""context.deleteDatabase("tasks.db")""", "delete database", """context.deleteDatabaseFiles("tasks.db")"""),
        Triple("""SQLiteDatabase.deleteDatabase(file)""", "delete database", """SQLiteDatabase.deleteDatabases(file)"""),
        Triple("""val h = DefaultDatabaseErrorHandler()""", "deleting error handler", """val h = KeepFileOnCorruption()"""),
        Triple("""SQLiteDatabase.openOrCreateDatabase(path, null)""", "deleting error handler", """SQLiteDatabase.openDatabase(path, null, 0, h)"""),
        Triple("""context.openOrCreateDatabase("tasks.db", 0, null)""", "deleting error handler", """context.openOrCreateDatabases("tasks.db")"""),
        Triple("""builder.setDriver(AndroidSQLiteDriver())""", "deleting error handler", """builder.setDriver(KeepFileSQLiteDriver())"""),
        Triple("""import androidx.sqlite.driver.AndroidSQLiteDriver""", "deleting error handler", """import androidx.sqlite.driver.AndroidSQLiteConnection"""),
    )

    @Test
    fun `every rule has a red case and a similar green case`() {
        cases.forEach { (bad, ruleId, good) ->
            val violations = check(bad)
            // Log.println also hits the println rule; what counts is the rule of this case.
            assertEquals("for: $bad -> $violations", 1, violations.count { it.contains("rule \"$ruleId\"") })
            assertEquals("for: $good", emptyList<String>(), check(good))
        }
        assertEquals(SourceCheck.rules.map { it.id }.toSet(), cases.map { it.second }.toSet())
    }

    @Test
    fun `the connection class of the own driver is allowed`() {
        assertEquals(emptyList<String>(), check("""private fun c(db: SQLiteDatabase) = AndroidSQLiteConnection(db)"""))
    }

    @Test
    fun `violation names file, line, rule and reference`() {
        val violation = check("""android.util.Log.d("tag", "msg")""").single()
        assertEquals(
            "$MAIN_FILE:4: android.util.Log is not allowed, rule \"android.util.Log\" (Security L2, DSGVO V5)",
            violation,
        )
    }

    @Test
    fun `match in a comment counts`() {
        assertTrue(check("""// context.getExternalFilesDir(null)""").single().contains("external storage"))
        assertTrue(check("""/* println("x") */""").single().contains("println"))
    }

    @Test
    fun `several violations in one file are all reported`() {
        val violations = SourceCheck.check(mapOf(MAIN_FILE to "println(1)\nval a = 1\nfun g(c: C) = c.cacheDir\n"))
        assertEquals(listOf("$MAIN_FILE:1:", "$MAIN_FILE:3:"), violations.map { it.substringBefore(" ") })
    }

    @Test
    fun `shipped source sets are checked, also java and build type source sets`() {
        assertEquals(1, check("println(1)", "app/src/debug/kotlin/x/D.kt").size)
        assertEquals(1, check("println(1)", "app/src/release/java/x/R.java").size)
    }

    @Test
    fun `test source sets and other files are not checked`() {
        listOf(
            "app/src/test/kotlin/x/T.kt",
            "app/src/testDebug/kotlin/x/T.kt",
            "app/src/androidTest/kotlin/x/T.kt",
            "app/src/testFixtures/kotlin/x/T.kt",
            "app/src/main/res/values/strings.xml",
            "tools/checks/src/main/kotlin/X.kt",
        ).forEach { path -> assertEquals(path, emptyList<String>(), check("println(1)", path)) }
    }

    @Test
    fun `no sources under app src main is a violation`() {
        assertTrue(SourceCheck.check(emptyMap()).single().contains("no app sources found under app/src/main/"))
        val onlyTests = mapOf("app/src/test/kotlin/x/T.kt" to "package x\n")
        assertTrue(SourceCheck.check(onlyTests).single().contains("no app sources found"))
        // Another shipped source set does not replace main.
        val onlyDebug = mapOf("app/src/debug/kotlin/x/D.kt" to "package x\n")
        assertTrue(SourceCheck.check(onlyDebug).single().contains("no app sources found"))
    }

    private companion object {
        const val MAIN_FILE = "app/src/main/kotlin/de/uchilab/todo/Main.kt"
    }
}
