package de.uchilab.checks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoredFieldsCheckTest {
    private fun schema(vararg tables: Pair<String, List<String>>): String = tables.joinToString(
        prefix = """{"formatVersion": 1, "database": {"version": 1, "entities": [""",
        postfix = "]}}",
    ) { (table, columns) ->
        """{"tableName": "$table", "fields": [""" +
            columns.joinToString { """{"fieldPath": "x", "columnName": "$it", "affinity": "TEXT"}""" } + "]}"
    }

    private val list = "# comment\ntask.id\n\ntask.title   # trailing comment\n"
    private val tasks = schema("task" to listOf("id", "title"))

    private fun check(files: Map<String, String>, fieldList: String = list) =
        StoredFieldsCheck.check(mapOf("de.example.Db" to files), fieldList)

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
        assertTrue(StoredFieldsCheck.check(emptyMap(), list).single().startsWith("no Room schema found"))
    }

    @Test
    fun `database directory without schema file is a violation`() {
        val violations = check(mapOf("notes.txt" to "x", "v2.json" to tasks))
        assertTrue(violations.first(), violations.first().contains("no schema file"))
    }

    @Test
    fun `only the highest version counts, compared as number`() {
        val old = schema("task" to listOf("id", "title", "old_column"))
        assertEquals(emptyList<String>(), check(mapOf("9.json" to old, "10.json" to tasks)))
        val violations = check(mapOf("9.json" to tasks, "10.json" to old))
        assertEquals(listOf("task.old_column"), violations.map { it.substringBefore(' ') })
    }

    @Test
    fun `fields of all databases are collected`() {
        val violations = StoredFieldsCheck.check(
            mapOf("de.example.A" to mapOf("1.json" to schema("task" to listOf("id"))),
                "de.example.B" to mapOf("1.json" to schema("task" to listOf("title")))),
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
