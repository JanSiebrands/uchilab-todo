package de.uchilab.checks

/**
 * Checks that R8 really shrank and obfuscated the release build, based on its output files
 * (not only on the build DSL). Returns human readable violations.
 *
 * Not checked on purpose: partially broad keep rules (e.g. a whole package). They cannot be detected
 * without arbitrary thresholds and stay a review topic.
 */
object R8Check {
    private val disablingFlags = setOf("-dontshrink", "-dontobfuscate", "-dontoptimize")

    /** Class line of mapping.txt: `original.Name -> new.Name:` (member lines are indented). */
    private val classLine = Regex("""^(\S+) -> (\S+):$""")

    /**
     * @param minifyTaskPresent whether the release variant has an R8 task (false if minify is off or unset)
     * @param configuration full R8 configuration incl. consumer rules of all dependencies, null if missing
     * @param mapping R8 mapping file, null if missing
     * @param usage list of code removed by R8, null if missing
     */
    fun check(minifyTaskPresent: Boolean, configuration: String?, mapping: String?, usage: String?): List<String> {
        if (!minifyTaskPresent) {
            return listOf("R8 is not active in the release build: no minifyReleaseWithR8 task (isMinifyEnabled must be true)")
        }
        val violations = mutableListOf<String>()
        if (configuration == null) {
            violations += "R8 configuration.txt not found (run assembleRelease first)"
        } else {
            configuration.lines()
                .map { it.substringBefore('#').trim().split(Regex("\\s+")).first() }
                .filter { it in disablingFlags }
                .toSortedSet()
                .forEach { violations += "R8 is partially disabled by $it in the release configuration" }
        }
        if (mapping == null) {
            violations += "R8 mapping.txt not found (run assembleRelease first)"
        } else if (mapping.lines().none { line -> classLine.find(line)?.let { it.groupValues[1] != it.groupValues[2] } == true }) {
            violations += "R8 did not rename any class (obfuscation is effectively off, e.g. by a keep-all rule)"
        }
        if (usage == null) {
            violations += "R8 usage.txt not found (run assembleRelease first)"
        } else if (usage.isBlank()) {
            violations += "R8 did not remove any code (shrinking is effectively off, e.g. by a keep-all rule)"
        }
        return violations
    }
}
