package de.uchilab.checks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
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
            // Everything the action reads is set here, at configuration time: Gradle 10 forbids Task.project
            // in a task action (UCH-68; CI runs with --warning-mode fail).
            task.buildDirectory.set(project.layout.buildDirectory)
            task.projectDirectory.set(project.layout.projectDirectory)
            task.rootDirectory.set(project.layout.settingsDirectory)
            // Resolution graphs of the classpaths as providers; resolved when the action reads them. all {}
            // also catches classpaths that the Android plugin creates later.
            project.configurations
                .matching { it.isCanBeResolved && CLASSPATH_NAME.matches(it.name) }
                .all { task.classpaths.put(it.name, it.incoming.resolutionResult.rootComponent) }
            // All source sets; SourceCheck itself drops the test source sets, so that rule has unit tests.
            task.appSources.from(project.fileTree("src") { it.include("**/*.kt", "**/*.java") })
            task.dependsOn("processDebugMainManifest", "processReleaseMainManifest")
        }
        project.tasks.register("r8Check", R8CheckTask::class.java) { task ->
            task.group = "verification"
            task.description = "Checks that R8 shrank and obfuscated the release build (run with assembleRelease)."
            // Lazy: if minify is off, the task does not exist and r8Check reports that instead of "task not found".
            // assembleRelease, not only the R8 task: mapping.txt is written after R8 by the Compose mapping
            // merge (mergeReleaseComposeMapping), so `r8Check` alone found no mapping.txt (UCH-68).
            task.dependsOn(project.provider {
                if (R8_TASK in project.tasks.names) listOf(project.tasks.named(RELEASE_TASK)) else emptyList()
            })
            task.minifyTaskPresent.set(project.provider { R8_TASK in project.tasks.names })
            task.mappingDir.set(project.layout.buildDirectory.dir("outputs/mapping/release"))
        }
    }

    companion object {
        const val R8_TASK = "minifyReleaseWithR8"
        const val RELEASE_TASK = "assembleRelease"
        /** Runtime and compile classpaths of all variants, including unit test variants. */
        val CLASSPATH_NAME = Regex("(debug|release)(UnitTest)?(Runtime|Compile)Classpath")
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
    /** Internal: missing merged manifests must be violations of the check, not task validation errors. */
    @get:Internal abstract val buildDirectory: DirectoryProperty
    /** Internal: the app's resource directories below it are looked up by the backup rule check. */
    @get:Internal abstract val projectDirectory: DirectoryProperty
    /** Internal: only used to print source paths relative to the repository root. */
    @get:Internal abstract val rootDirectory: DirectoryProperty
    /** Root of the resolution graph per classpath configuration name. */
    @get:Internal abstract val classpaths: MapProperty<String, ResolvedComponentResult>

    @TaskAction
    fun run() {
        val violations = mutableListOf<String>()
        val allowedPermissions = permissionAllowlist.get().asFile.readLines()
            .map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.toSet()
        MERGED_MANIFESTS.forEach { (variant, path) ->
            val file = buildDirectory.file(path).get().asFile
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
        val sources = appSources.files.associate { it.relativeTo(rootDirectory.get().asFile).invariantSeparatorsPath to it.readText() }
        violations += SourceCheck.check(sources).map { "[source] $it" }
        val classpaths = classpaths.get()
        if (classpaths.isEmpty()) throw GradleException("privacyCheck: no classpath configurations found")
        val deny = denylist.get().asFile.readText()
        val runtimeAllow = allowlist.get().asFile.readText()
        val runtimePolicy = DependencyPolicy.parse(runtimeAllow, deny)
        val testPolicy = DependencyPolicy.parse(runtimeAllow + "\n" + testAllowlist.get().asFile.readText(), deny)
        val checked = mutableSetOf<String>()
        classpaths.forEach { (name, root) ->
            val coordinates = allComponents(root)
                .mapNotNull { it.id as? ModuleComponentIdentifier }
                .map { "${it.group}:${it.module}:${it.version}" }
            checked += coordinates
            val policy = if (name.contains("UnitTest")) testPolicy else runtimePolicy
            violations += policy.check(coordinates).map { "[dependencies:$name] $it" }
        }
        if (violations.isEmpty()) {
            logger.lifecycle(
                "privacyCheck: OK (${checked.size} dependencies, ${MERGED_MANIFESTS.size} manifests, " +
                    "${sources.keys.count(SourceCheck::isShippedSource)} source files checked)",
            )
        } else {
            violations.forEach { logger.error(it) }
            throw GradleException("privacyCheck failed with ${violations.size} violation(s)")
        }
    }

    /** All components reachable from [root], like `ResolutionResult.allComponents` (root included). */
    private fun allComponents(root: ResolvedComponentResult): Set<ResolvedComponentResult> {
        val seen = linkedSetOf(root)
        val queue = ArrayDeque(listOf(root))
        while (queue.isNotEmpty()) {
            queue.removeFirst().dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { dependency ->
                if (seen.add(dependency.selected)) queue.addLast(dependency.selected)
            }
        }
        return seen
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
            val referenceViolations = ManifestCheck.ruleReferenceViolations(attribute, reference)
            if (referenceViolations.isNotEmpty()) return@flatMap referenceViolations
            val name = reference!!.removePrefix("@xml/")
            val files = listOf("main", variant).flatMap { sourceSet ->
                projectDirectory.dir("src/$sourceSet/res").get().asFile.listFiles { f -> f.isDirectory && f.name.matches(Regex("xml(-.+)?")) }
                    ?.map { it.resolve("$name.xml") }?.filter { it.isFile }.orEmpty()
            }
            if (files.isEmpty()) {
                listOf("$attribute: file res/xml/$name.xml not found")
            } else {
                files.flatMap { file -> check(file.readText()).map { "${file.relativeTo(projectDirectory.get().asFile)}: $it" } }
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
    }
}
