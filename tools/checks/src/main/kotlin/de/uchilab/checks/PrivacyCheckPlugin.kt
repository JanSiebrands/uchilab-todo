package de.uchilab.checks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

interface PrivacyCheckExtension {
    val allowlist: RegularFileProperty
    /** Additional groups that are only allowed on test classpaths. */
    val testAllowlist: RegularFileProperty
    val denylist: RegularFileProperty
    /** Permissions allowed in the merged manifests, one per line; may be empty. */
    val permissionAllowlist: RegularFileProperty
    /** Fields stored on the device, one `table.column` per line (DSGVO P4). */
    val storedFields: RegularFileProperty
    /** Room schema export directory (`room3 { schemaDirectory(...) }`), one subdirectory per database. */
    val schemaDirectory: DirectoryProperty
}

/**
 * Registers `privacyCheck` on an Android application project: merged manifests (debug and release)
 * the app's own sources (without tests) and all resolved classpaths (runtime and test) must follow the
 * privacy rules of this company.
 */
class PrivacyCheckPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("privacyCheck", PrivacyCheckExtension::class.java)
        project.tasks.register("privacyCheck", PrivacyCheckTask::class.java) { task ->
            task.group = "verification"
            task.description = "Checks merged manifests, app sources and dependencies against the privacy rules."
            task.allowlist.set(extension.allowlist)
            task.testAllowlist.set(extension.testAllowlist)
            task.denylist.set(extension.denylist)
            task.permissionAllowlist.set(extension.permissionAllowlist)
            task.storedFields.set(extension.storedFields)
            task.schemaDirectory.set(extension.schemaDirectory)
            // Room's generated <database>_Impl carries the database version and schema identity hash of the code.
            task.generatedRoomCode.from(
                project.fileTree(project.layout.buildDirectory.dir(KSP_DEBUG_SOURCES)) { it.include("**/*_Impl.kt") },
            )
            task.generatedRoomRoot.set(project.layout.buildDirectory.dir(KSP_DEBUG_SOURCES))
            // All source sets; SourceCheck itself drops the test source sets, so that rule has unit tests.
            task.appSources.from(project.fileTree("src") { it.include("**/*.kt", "**/*.java") })
            // KSP generates the Room schema from the current code; only if it differs from the checked-in one,
            // Room writes it and the finalizer copyRoomSchemas copies it into schemaDirectory (otherwise
            // NO-SOURCE). Alone copyRoomSchemas has no input, so privacyCheck depends on the KSP tasks of both
            // variants as well. The field list is thus checked
            // against the code, not against a stale file; CI then checks that this left the checked-in schema
            // unchanged (tools/checks/schema-check.sh).
            task.dependsOn("processDebugMainManifest", "processReleaseMainManifest")
            task.dependsOn(KSP_TASKS + ROOM_SCHEMA_TASK)
        }
        project.tasks.register("r8Check", R8CheckTask::class.java) { task ->
            task.group = "verification"
            task.description = "Checks that R8 shrank and obfuscated the release build (run with assembleRelease)."
            // Lazy: if minify is off, the task does not exist and r8Check reports that instead of "task not found".
            task.dependsOn(project.provider {
                if (R8_TASK in project.tasks.names) listOf(project.tasks.named(R8_TASK)) else emptyList()
            })
            task.minifyTaskPresent.set(project.provider { R8_TASK in project.tasks.names })
            task.mappingDir.set(project.layout.buildDirectory.dir("outputs/mapping/release"))
        }
    }

    companion object {
        const val R8_TASK = "minifyReleaseWithR8"
        const val ROOM_SCHEMA_TASK = "copyRoomSchemas"
        val KSP_TASKS = listOf("kspDebugKotlin", "kspReleaseKotlin")
        const val KSP_DEBUG_SOURCES = "generated/ksp/debug/kotlin"
    }
}

abstract class R8CheckTask : DefaultTask() {
    @get:Input abstract val minifyTaskPresent: Property<Boolean>
    @get:Internal abstract val mappingDir: DirectoryProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun run() {
        val dir = mappingDir.get().asFile
        fun read(name: String) = dir.resolve(name).takeIf { it.isFile }?.readText()
        val violations = R8Check.check(
            minifyTaskPresent.get(), read("configuration.txt"), read("mapping.txt"), read("usage.txt"),
        )
        if (violations.isEmpty()) {
            logger.lifecycle("r8Check: OK (R8 shrank and obfuscated the release build)")
        } else {
            violations.forEach { logger.error("r8Check: $it") }
            throw GradleException("r8Check failed with ${violations.size} violation(s)")
        }
    }
}

abstract class PrivacyCheckTask : DefaultTask() {
    @get:InputFile abstract val allowlist: RegularFileProperty
    @get:InputFile abstract val testAllowlist: RegularFileProperty
    @get:InputFile abstract val denylist: RegularFileProperty
    @get:InputFile abstract val permissionAllowlist: RegularFileProperty
    @get:InputFiles abstract val appSources: ConfigurableFileCollection
    @get:InputFile abstract val storedFields: RegularFileProperty
    /** Internal: a missing directory must be a violation of the check, not a task validation error. */
    @get:Internal abstract val schemaDirectory: DirectoryProperty
    @get:InputFiles abstract val generatedRoomCode: ConfigurableFileCollection
    @get:Internal abstract val generatedRoomRoot: DirectoryProperty

    @TaskAction
    fun run() {
        val violations = mutableListOf<String>()
        val allowedPermissions = permissionAllowlist.get().asFile.readLines()
            .map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.toSet()
        MERGED_MANIFESTS.forEach { (variant, path) ->
            val file = project.layout.buildDirectory.file(path).get().asFile
            if (!file.exists()) {
                violations += "[$variant manifest] merged manifest not found: ${file.path}"
            } else {
                val extra = if (variant == "debug") DEBUG_ONLY_EXPORTED else emptySet()
                val manifest = file.readText()
                violations += ManifestCheck.check(manifest, extra, allowedPermissions, release = variant == "release")
                    .map { "[$variant manifest] $it" }
                violations += backupViolations(variant, manifest).map { "[$variant backup] $it" }
            }
        }
        val sources = appSources.files.associate { it.relativeTo(project.rootDir).invariantSeparatorsPath to it.readText() }
        violations += SourceCheck.check(sources).map { "[source] $it" }
        val databases = schemaDirectory.get().asFile.listFiles { f -> f.isDirectory }.orEmpty().associate { dir ->
            dir.name to dir.listFiles { f -> f.isFile }.orEmpty().associate { it.name to it.readText() }
        }
        val generatedRoot = generatedRoomRoot.get().asFile
        val generated = generatedRoomCode.files.map { it to it.readText() }
            .filter { (_, code) -> "RoomOpenDelegate(" in code }
            .associate { (file, code) ->
                file.relativeTo(generatedRoot).invariantSeparatorsPath.removeSuffix("_Impl.kt").replace('/', '.') to code
            }
        violations += StoredFieldsCheck.check(databases, generated, storedFields.get().asFile.readText())
            .map { "[stored fields] $it" }
        val classpaths = project.configurations.filter {
            it.isCanBeResolved && CLASSPATH_NAME.matches(it.name)
        }
        if (classpaths.isEmpty()) throw GradleException("privacyCheck: no classpath configurations found")
        val deny = denylist.get().asFile.readText()
        val runtimeAllow = allowlist.get().asFile.readText()
        val runtimePolicy = DependencyPolicy.parse(runtimeAllow, deny)
        val testPolicy = DependencyPolicy.parse(runtimeAllow + "\n" + testAllowlist.get().asFile.readText(), deny)
        val checked = mutableSetOf<String>()
        classpaths.forEach { configuration ->
            val coordinates = configuration.incoming.resolutionResult.allComponents
                .mapNotNull { it.id as? ModuleComponentIdentifier }
                .map { "${it.group}:${it.module}:${it.version}" }
            checked += coordinates
            val policy = if (configuration.name.contains("UnitTest")) testPolicy else runtimePolicy
            violations += policy.check(coordinates).map { "[dependencies:${configuration.name}] $it" }
        }
        if (violations.isEmpty()) {
            logger.lifecycle(
                "privacyCheck: OK (${checked.size} dependencies, ${MERGED_MANIFESTS.size} manifests, " +
                    "${sources.keys.count(SourceCheck::isShippedSource)} source files, " +
                    "${databases.size} database schema(s) checked)",
            )
        } else {
            violations.forEach { logger.error(it) }
            throw GradleException("privacyCheck failed with ${violations.size} violation(s)")
        }
    }

    /**
     * The files referenced from the merged manifest must exist in the app's own resources (main or the
     * build type source set, any `res/xml*` qualifier) and every copy must switch backup and transfer off.
     * App resources take precedence over library resources in the merge, so these are the files that ship.
     */
    private fun backupViolations(variant: String, manifest: String): List<String> {
        val (extraction, fullBackup) = ManifestCheck.backupRuleReferences(manifest)
        val checks = listOf(
            Triple("android:dataExtractionRules", extraction, BackupRulesCheck::checkDataExtractionRules),
            Triple("android:fullBackupContent", fullBackup, BackupRulesCheck::checkFullBackupContent),
        )
        return checks.flatMap { (attribute, reference, check) ->
            if (reference == null || !reference.startsWith("@xml/")) {
                return@flatMap listOf("$attribute must reference an @xml resource (found: ${reference ?: "missing"})")
            }
            val name = reference.removePrefix("@xml/")
            val files = listOf("main", variant).flatMap { sourceSet ->
                project.file("src/$sourceSet/res").listFiles { f -> f.isDirectory && f.name.matches(Regex("xml(-.+)?")) }
                    ?.map { it.resolve("$name.xml") }?.filter { it.isFile }.orEmpty()
            }
            if (files.isEmpty()) {
                listOf("$attribute: file res/xml/$name.xml not found")
            } else {
                files.flatMap { file -> check(file.readText()).map { "${file.relativeTo(project.projectDir)}: $it" } }
            }
        }
    }

    private companion object {
        /** Test helper activity of ui-test-manifest; only present in the debug variant, never shipped. */
        val DEBUG_ONLY_EXPORTED = setOf("androidx.activity.ComponentActivity")
        val MERGED_MANIFESTS = mapOf(
            "debug" to "intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml",
            "release" to "intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml",
        )
        /** Runtime and compile classpaths of all variants, including unit test variants. */
        val CLASSPATH_NAME = Regex("(debug|release)(UnitTest)?(Runtime|Compile)Classpath")
    }
}
