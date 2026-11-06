package de.uchilab.checks

/**
 * Checks resolved dependency coordinates (`group:name:version`) against a group allowlist and a denylist.
 * Pattern syntax per line: `group` matches exactly, `prefix.*` matches `prefix` and every subgroup,
 * `group:name` matches exactly one artifact.
 * The allowlist is the real gate; the denylist only produces a clear message for known trackers.
 */
class DependencyPolicy(private val allow: List<String>, private val deny: List<String>) {

    fun check(coordinates: Collection<String>): List<String> =
        coordinates.toSortedSet().mapNotNull { coordinate ->
            val group = coordinate.substringBefore(':')
            val artifact = coordinate.split(':').take(2).joinToString(":")
            when {
                deny.any { matches(it, group, artifact) } ->
                    "Forbidden dependency (denylist): $coordinate"
                allow.none { matches(it, group, artifact) } ->
                    "Dependency not on the allowlist: $coordinate (add group deliberately to tools/checks/allowlist.txt)"
                else -> null
            }
        }

    companion object {
        fun parse(allowlist: String, denylist: String) =
            DependencyPolicy(patterns(allowlist), patterns(denylist))

        private fun patterns(text: String) =
            text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }

        private fun matches(pattern: String, group: String, artifact: String): Boolean =
            if (':' in pattern) {
                pattern == artifact
            } else if (pattern.endsWith(".*")) {
                val prefix = pattern.removeSuffix(".*")
                group == prefix || group.startsWith("$prefix.")
            } else {
                group == pattern
            }
    }
}
