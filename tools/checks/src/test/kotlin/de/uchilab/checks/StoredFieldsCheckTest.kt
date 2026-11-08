package de.uchilab.checks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoredFieldsCheckTest {
    private companion object {
        const val HASH = "9824786632575b12485990a398c7635c"
    }

    private fun schema(vararg tables: Pair<String, List<String>>, hash: String = HASH): String = tables.joinToString(
        prefix = """{"formatVersion": 1, "database": {"version": 1, "identityHash": "$hash", "entities": [""",
        postfix = "]}}",
    ) { (table, columns) ->
        """{"tableName": "$table", "fields": [""" +
            columns.joinToString { """{"fieldPath": "x", "columnName": "$it", "affinity": "TEXT"}""" } + "]}"
    }

    private val list = "# comment\ntask.id\n\ntask.title   # trailing comment\n"
    private val tasks = schema("task" to listOf("id", "title"))

    /** Shape of Room's generated `<database>_Impl`, reduced to the part the check reads. */
    private fun impl(version: Int, hash: String = HASH) =
        """object : RoomOpenDelegate($version, "$hash", "0123456789abcdef0123456789abcdef") {"""

    private fun check(files: Map<String, String>, fieldList: String = list, version: Int = 1) =
        StoredFieldsCheck.check(mapOf("de.example.Db" to files), mapOf("de.example.Db" to impl(version)), fieldList)

    @Test
    fun `schema matching the list has no violations`() {
        assertEquals(emptyList<String>(), check(mapOf("1.json" to tasks)))
    }

    @Test
    fun `stored field missing in the list is a violation naming table and column`() {
        val violations = check(mapOf("1.json" to schema("task" to listOf("id", "title", "priority"))))
        assertEquals(1, violations.size)
        assertTrue(violations.single(), violations.single().startsWith("task.priority is stored but not in"))
    }

    @Test
    fun `new table is a violation for each of its columns`() {
        val violations = check(mapOf("1.json" to schema("task" to listOf("id", "title"), "probe" to listOf("id", "value"))))
        assertEquals(listOf("probe.id", "probe.value"), violations.map { it.substringBefore(' ') })
    }

    @Test
    fun `list entry missing in the schema is a violation`() {
        val violations = check(mapOf("1.json" to schema("task" to listOf("id"))))
        assertEquals(1, violations.size)
        assertTrue(violations.single(), violations.single().startsWith("task.title is in tools/checks/stored-fields.txt but not"))
    }

    @Test
    fun `same column name in another table does not count`() {
        val violations = check(mapOf("1.json" to schema("task" to listOf("id"), "other" to listOf("title"))))
        assertEquals(listOf("other.title", "task.title"), violations.map { it.substringBefore(' ') }.sorted())
    }

    @Test
    fun `no database directory is a violation`() {
        assertTrue(StoredFieldsCheck.check(emptyMap(), emptyMap(), list).single().startsWith("no Room schema found"))
    }

    @Test
    fun `database directory without schema file is a violation`() {
        val violations = check(mapOf("notes.txt" to "x", "v2.json" to tasks))
        assertTrue(violations.first(), violations.first().contains("no schema file 1.json"))
    }

    @Test
    fun `the version of the code counts, not the highest file`() {
        val old = schema("task" to listOf("id", "title", "old_column"))
        assertEquals(emptyList<String>(), check(mapOf("9.json" to old, "10.json" to tasks), version = 10))
        val violations = check(mapOf("9.json" to tasks, "10.json" to old), version = 10)
        assertEquals(listOf("task.old_column"), violations.map { it.substringBefore(' ') })
    }

    @Test
    fun `schema file newer than the code version is a violation, even if it looks harmless`() {
        // A stale 2.json without the new column must not hide the field that the current 1.json stores.
        val violations = check(mapOf("1.json" to schema("task" to listOf("id", "title", "probe")), "2.json" to tasks))
        assertEquals(2, violations.size)
        assertTrue(violations[0], violations[0].startsWith("de.example.Db/2.json is newer than database version 1"))
        assertTrue(violations[1], violations[1].startsWith("task.probe is stored but not in"))
    }

    @Test
    fun `schema with another identity hash than the code is a violation`() {
        val violations = check(mapOf("1.json" to schema("task" to listOf("id", "title"), hash = "ffff")))
        assertEquals(1, violations.size)
        assertTrue(violations.single(), violations.single().startsWith("de.example.Db/1.json does not match the code"))
    }

    @Test
    fun `database in the code without schema directory is a violation`() {
        val violations = StoredFieldsCheck.check(emptyMap(), mapOf("de.example.Db" to impl(1)), "")
        assertEquals(1, violations.size)
        assertTrue(violations.single(), violations.single().contains("no exported schema"))
    }

    @Test
    fun `schema directory without database in the code is a violation`() {
        val violations = StoredFieldsCheck.check(
            mapOf("de.example.Db" to mapOf("1.json" to tasks), "de.example.Gone" to mapOf("1.json" to tasks)),
            mapOf("de.example.Db" to impl(1)),
            list,
        )
        assertEquals(1, violations.size)
        assertTrue(violations.single(), violations.single().startsWith("de.example.Gone: schema in app/schemas but no Room database"))
    }

    @Test
    fun `unreadable generated code is a violation`() {
        val violations = StoredFieldsCheck.check(mapOf("de.example.Db" to mapOf("1.json" to tasks)), mapOf("de.example.Db" to "class X"), list)
        assertTrue(violations.first(), violations.first().contains("database version not found"))
    }

    @Test
    fun `fields of all databases are collected`() {
        val violations = StoredFieldsCheck.check(
            mapOf("de.example.A" to mapOf("1.json" to schema("task" to listOf("id"))),
                "de.example.B" to mapOf("1.json" to schema("task" to listOf("title")))),
            mapOf("de.example.A" to impl(1), "de.example.B" to impl(1)),
            list,
        )
        assertEquals(emptyList<String>(), violations)
    }

    @Test
    fun `invalid schema is a violation, not a crash`() {
        assertTrue(check(mapOf("1.json" to "{ not json")).first().contains("not valid JSON"))
        assertTrue(check(mapOf("1.json" to """{"database": {}}""")).first().contains("no database.entities"))
        assertTrue(check(mapOf("1.json" to """{"database": {"entities": [{"fields": []}]}}""")).first().contains("without tableName"))
        assertTrue(
            check(mapOf("1.json" to """{"database": {"entities": [{"tableName": "t", "fields": [{}]}]}}"""))
                .first().contains("field without columnName"),
        )
    }

    @Test
    fun `empty list with a stored field is a violation`() {
        assertEquals(listOf("task.id", "task.title"), check(mapOf("1.json" to tasks), "# nothing\n").map { it.substringBefore(' ') })
    }

}
