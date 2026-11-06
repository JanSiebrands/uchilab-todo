package de.uchilab.checks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

interface PrivacyCheckExtension {
    val allowlist: RegularFileProperty
    /** Additional groups that are only allowed on test classpaths. */
    val testAllowlist: RegularFileProperty
    val denylist: RegularFileProperty
}

/**
 * Registers `privacyCheck` on an Android application project: merged manifests (debug and release)
 * and all resolved classpaths (runtime and test) must follow the privacy rules of this company.
 */
class PrivacyCheckPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("privacyCheck", PrivacyCheckExtension::class.java)
        project.tasks.register("privacyCheck", PrivacyCheckTask::class.java) { task ->
            task.group = "verification"
            task.description = "Checks merged manifests and dependencies against the privacy rules."
            task.allowlist.set(extension.allowlist)
            task.testAllowlist.set(extension.testAllowlist)
            task.denylist.set(extension.denylist)
            task.dependsOn("processDebugMainManifest", "processReleaseMainManifest")
        }
    }
}

abstract class PrivacyCheckTask : DefaultTask() {
    @get:InputFile abstract val allowlist: RegularFileProperty
    @get:InputFile abstract val testAllowlist: RegularFileProperty
    @get:InputFile abstract val denylist: RegularFileProperty

    @TaskAction
    fun run() {
        val violations = mutableListOf<String>()
        MERGED_MANIFESTS.forEach { (variant, path) ->
            val file = project.layout.buildDirectory.file(path).get().asFile
            if (!file.exists()) {
                violations += "[$variant manifest] merged manifest not found: ${file.path}"
            } else {
                val extra = if (variant == "debug") DEBUG_ONLY_EXPORTED else emptySet()
                violations += ManifestCheck.check(file.readText(), extra).map { "[$variant manifest] $it" }
            }
        }
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
            logger.lifecycle("privacyCheck: OK (${checked.size} dependencies, ${MERGED_MANIFESTS.size} manifests checked)")
        } else {
            violations.forEach { logger.error(it) }
            throw GradleException("privacyCheck failed with ${violations.size} violation(s)")
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
