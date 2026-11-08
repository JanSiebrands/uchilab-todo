package de.uchilab.checks

/**
 * Text rules for the app's own Kotlin and Java sources that ship with the APK: no logging (Security L2,
 * DSGVO V5) and no storage outside the app's internal, credential encrypted storage (Security D2, D3,
 * DSGVO V1), and no code path that deletes or replaces the task database (ADR 0003, UCH-78). Matches in
 * comments and strings count; there is deliberately no exception mechanism.
 */
object SourceCheck {
    data class Rule(val id: String, val reference: String, val pattern: Regex)

    const val MAIN_SOURCE_DIR = "app/src/main/"
    private const val SOURCE_ROOT = "app/src/"
    private val testSourceSetPrefixes = listOf("test", "androidTest", "testFixtures")
    private val sourceExtensions = setOf("kt", "java")

    // Change only after asking Security and DSGVO (Security L2/section 6, DSGVO V7 point 4); for the rules of
    // ADR 0003/UCH-78 they agree in their review stages of UCH-86.
    // Removing a rule is a code change with a test change and therefore visible in review.
    val rules: List<Rule> = listOf(
        Rule("android.util.Log", "Security L2, DSGVO V5",
            Regex("""\bandroid\.util\.Log\b|\bLog\s*\.\s*(v|d|i|w|e|wtf|println)\s*\(""")),
        Rule("println/print", "Security L2, DSGVO V5", Regex("""\bprintln\s*\(|\bprint\s*\(""")),
        Rule("System.out/System.err", "Security L2", Regex("""\bSystem\s*\.\s*(out|err)\b""")),
        Rule("printStackTrace", "Security L2, DSGVO V5", Regex("""\bprintStackTrace\s*\(""")),
        Rule("logging library", "Security L2, DSGVO V5",
            Regex("""\btimber\.log\b|\bTimber\s*\.|\bjava\.util\.logging\b|\borg\.slf4j\b""")),
        Rule("Room query log", "Security L2", Regex("""\bsetQueryCallback\b""")),
        Rule("external storage", "Security D2, DSGVO V1",
            Regex("""(?i)\b(get)?external(files|cache|media)dirs?\b|\bgetExternalStorage\w*|\bMediaStore\b""")),
        Rule("world readable files", "Security D2", Regex("""\bMODE_WORLD_(READABLE|WRITEABLE)\b""")),
        Rule("Storage Access Framework", "DSGVO V1",
            Regex("""\bACTION_(CREATE|OPEN)_DOCUMENT(_TREE)?\b|\bDocumentsContract\b|""" +
                """\b(CreateDocument|OpenDocument|OpenDocumentTree|OpenMultipleDocuments)\b""")),
        Rule("Direct Boot storage", "Security D3, DSGVO V1", Regex("""\bcreateDeviceProtectedStorageContext\b""")),
        Rule("cache and temp files", "DSGVO V1", Regex("""\b(get)?[cC]acheDir\b|\bcreateTempFile\b""")),
        // A damaged or outdated database is never deleted or replaced (ADR 0003, UCH-78): no destructive
        // migration, no prepackaged database (Room deletes the file to copy it), no deleting the file, and no
        // way back to Android's deleting standard error handler or the standard driver that uses it.
        // AndroidSQLiteConnection (used by the own driver) is allowed.
        Rule("destructive migration", "ADR 0003, UCH-78",
            Regex("""\bfallbackToDestructiveMigration\w*|\ballowDataLossOnRecovery\b|\bdropAllTables\b""")),
        Rule("prepackaged database", "ADR 0003, UCH-78", Regex("""\bcreateFrom(Asset|File|InputStream)\b""")),
        Rule("delete database", "ADR 0003, UCH-78", Regex("""\bdeleteDatabase\b""")),
        Rule("deleting error handler", "ADR 0003, UCH-78",
            Regex("""\bDefaultDatabaseErrorHandler\b|\bopenOrCreateDatabase\b|\bAndroidSQLiteDriver\b""")),
    )

    /**
     * @param files path relative to the repository root (e.g. `app/src/main/kotlin/X.kt`) to file content.
     *   Files outside `app/src/`, of test source sets or with other extensions are ignored.
     * @return one violation per rule and line, also several per file
     */
    fun check(files: Map<String, String>): List<String> {
        val shipped = files.filterKeys(::isShippedSource)
        if (shipped.keys.none { it.startsWith(MAIN_SOURCE_DIR) }) {
            return listOf("no app sources found under $MAIN_SOURCE_DIR")
        }
        return shipped.toSortedMap().flatMap { (path, content) ->
            content.lines().flatMapIndexed { index, line ->
                rules.mapNotNull { rule ->
                    rule.pattern.find(line)?.let { match ->
                        val found = match.value.replace(Regex("""\s+"""), "")
                        "$path:${index + 1}: $found is not allowed, rule \"${rule.id}\" (${rule.reference})"
                    }
                }
            }
        }
    }

    /** Kotlin or Java file in a source set under `app/src/` that is not a test source set. */
    fun isShippedSource(path: String): Boolean {
        if (!path.startsWith(SOURCE_ROOT) || path.substringAfterLast('.') !in sourceExtensions) return false
        val sourceSet = path.removePrefix(SOURCE_ROOT).substringBefore('/')
        return sourceSet.isNotEmpty() && path.removePrefix(SOURCE_ROOT).contains('/') &&
            testSourceSetPrefixes.none { sourceSet.startsWith(it) }
    }
}
