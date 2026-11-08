package de.uchilab.checks

import groovy.json.JsonSlurper

/**
 * DSGVO P4 (V2): the fields stored on the device must match the field list exactly. Source of truth for what
 * is stored is the Room schema exported to `app/schemas/<database>/<version>.json` for the database version
 * of the current code. That version and the schema's identity hash are taken from the code Room/KSP generated
 * (`<database>_Impl`), so a stale schema file with a higher version cannot hide a new field. Gradle-free apart
 * from Groovy's JSON parser, so the rules have unit tests.
 */
object StoredFieldsCheck {
    private val SCHEMA_FILE = Regex("(\\d+)\\.json")
    private val OPEN_DELEGATE = Regex("RoomOpenDelegate\\(\\s*(\\d+)\\s*,\\s*\"([0-9a-fA-F]+)\"")

    /**
     * @param databases schema files per database directory: directory name (database class) -> (file name -> content)
     * @param generated source of the Room generated `<database>_Impl` per database class name
     * @param fieldList content of the field list, one `table.column` per line, `#` starts a comment
     */
    fun check(databases: Map<String, Map<String, String>>, generated: Map<String, String>, fieldList: String): List<String> {
        if (databases.isEmpty() && generated.isEmpty()) {
            return listOf("no Room schema found (expected app/schemas/<database>/<version>.json)")
        }
        val violations = mutableListOf<String>()
        val stored = mutableSetOf<String>()
        (databases.keys + generated.keys).sorted().forEach { database ->
            val files = databases[database]
            val code = generated[database]
            if (files == null) {
                violations += "$database: Room database in the code but no exported schema in app/schemas"
                return@forEach
            }
            if (code == null) {
                violations += "$database: schema in app/schemas but no Room database in the code (remove the stale schema)"
                return@forEach
            }
            val delegate = OPEN_DELEGATE.find(code)
            if (delegate == null) {
                violations += "$database: database version not found in the Room generated code (RoomOpenDelegate)"
                return@forEach
            }
            val version = delegate.groupValues[1].toInt()
            val identityHash = delegate.groupValues[2]
            val versions = files.keys.mapNotNull { name -> SCHEMA_FILE.matchEntire(name)?.let { it.groupValues[1].toInt() } }
            versions.filter { it > version }.sorted().forEach {
                violations += "$database/$it.json is newer than database version $version in the code (stale schema, remove it)"
            }
            val current = files["$version.json"]
            if (current == null) {
                violations += "$database: no schema file $version.json for database version $version in the code"
                return@forEach
            }
            try {
                val (hash, fields) = parse(current)
                if (hash != identityHash) {
                    violations += "$database/$version.json does not match the code (identityHash $hash, code $identityHash)"
                }
                stored += fields
            } catch (e: IllegalArgumentException) {
                violations += "$database/$version.json: ${e.message}"
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

    /** Identity hash and all `table.column` of one exported Room schema. */
    private fun parse(json: String): Pair<String?, Set<String>> {
        val root = try {
            JsonSlurper().parseText(json)
        } catch (e: RuntimeException) {
            throw IllegalArgumentException("schema is not valid JSON")
        }
        val database = (root as? Map<*, *>)?.get("database") as? Map<*, *>
        val entities = database?.get("entities") as? List<*>
            ?: throw IllegalArgumentException("schema has no database.entities")
        return database["identityHash"] as? String to entities.flatMap { entity ->
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
