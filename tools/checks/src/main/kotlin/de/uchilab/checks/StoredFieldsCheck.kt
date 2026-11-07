package de.uchilab.checks

import groovy.json.JsonSlurper

/**
 * DSGVO P4 (V2): the fields stored on the device must match the field list exactly. Source of truth for what
 * is stored is the Room schema exported to `app/schemas/<database>/<version>.json`; only the highest version
 * of each database counts. Gradle-free apart from Groovy's JSON parser, so the rules have unit tests.
 */
object StoredFieldsCheck {
    private val SCHEMA_FILE = Regex("(\\d+)\\.json")

    /**
     * @param databases schema files per database directory: directory name -> (file name -> content)
     * @param fieldList content of the field list, one `table.column` per line, `#` starts a comment
     */
    fun check(databases: Map<String, Map<String, String>>, fieldList: String): List<String> {
        if (databases.isEmpty()) return listOf("no Room schema found (expected app/schemas/<database>/<version>.json)")
        val violations = mutableListOf<String>()
        val stored = mutableSetOf<String>()
        databases.toSortedMap().forEach { (database, files) ->
            val latest = files.keys.mapNotNull { name -> SCHEMA_FILE.matchEntire(name)?.let { it.groupValues[1].toInt() to name } }
                .maxByOrNull { it.first }
            if (latest == null) {
                violations += "$database: no schema file <version>.json found"
                return@forEach
            }
            try {
                stored += fields(files.getValue(latest.second))
            } catch (e: IllegalArgumentException) {
                violations += "$database/${latest.second}: ${e.message}"
            }
        }
        val listed = parseList(fieldList)
        (stored - listed).sorted().forEach {
            violations += "$it is stored but not in tools/checks/stored-fields.txt (new stored field: ask DSGVO first, V2/V7)"
        }
        (listed - stored).sorted().forEach {
            violations += "$it is in tools/checks/stored-fields.txt but not in the Room schema (the list must match exactly)"
        }
        return violations
    }

    /** All `table.column` of one exported Room schema. */
    fun fields(json: String): Set<String> {
        val root = try {
            JsonSlurper().parseText(json)
        } catch (e: RuntimeException) {
            throw IllegalArgumentException("schema is not valid JSON")
        }
        val entities = ((root as? Map<*, *>)?.get("database") as? Map<*, *>)?.get("entities") as? List<*>
            ?: throw IllegalArgumentException("schema has no database.entities")
        return entities.flatMap { entity ->
            val table = (entity as? Map<*, *>)?.get("tableName") as? String
                ?: throw IllegalArgumentException("entity without tableName")
            val fields = entity["fields"] as? List<*> ?: throw IllegalArgumentException("$table: entity without fields")
            fields.map { field ->
                val column = (field as? Map<*, *>)?.get("columnName") as? String
                    ?: throw IllegalArgumentException("$table: field without columnName")
                "$table.$column"
            }
        }.toSet()
    }

    fun parseList(text: String): Set<String> =
        text.lines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.toSet()
}
