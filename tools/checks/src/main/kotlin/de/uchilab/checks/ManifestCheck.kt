package de.uchilab.checks

import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader

/** Privacy rules for the *merged* Android manifest. Returns human readable violations. */
object ManifestCheck {
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val LAUNCHER_ACTIVITY = "de.uchilab.todo.ui.MainActivity"
    private const val DUMP_PERMISSION = "android.permission.DUMP"

    private val forbiddenPermissions = setOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
    )
    private val componentTags = listOf("activity", "activity-alias", "service", "receiver", "provider")
    private const val ACTION_MAIN = "android.intent.action.MAIN"
    private const val CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER"

    /**
     * @param allowedPermissions every `uses-permission` must be on this list (may be empty);
     *   the forbidden permissions stay forbidden even if they are on it
     * @param release true for the release manifest; only there `android:debuggable` is forbidden
     *   (AGP always sets it in the debug build)
     */
    fun check(
        manifestXml: String,
        extraExported: Set<String> = emptySet(),
        allowedPermissions: Set<String> = emptySet(),
        release: Boolean = false,
    ): List<String> {
        val root = try {
            parse(manifestXml)
        } catch (e: Exception) {
            return listOf("Manifest could not be parsed: ${e.message}")
        }
        val violations = mutableListOf<String>()
        violations += permissionViolations(root, allowedPermissions)
        val application = root.getElementsByTagName("application").item(0) as? Element
        if (application == null) {
            violations += "Manifest has no <application> element"
            return violations
        }
        if (application.androidAttr("allowBackup") != "false") {
            violations += "android:allowBackup must be \"false\" (found: ${application.androidAttr("allowBackup") ?: "unset, default is true"})"
        }
        if (application.androidAttr("usesCleartextTraffic") == "true") {
            violations += "android:usesCleartextTraffic must not be \"true\""
        }
        if (release && application.androidAttr("debuggable") == "true") {
            violations += "android:debuggable must not be \"true\" in the release build (Security R1)"
        }
        violations += storageAttributeViolations(application)
        violations += componentViolations(application, extraExported)
        violations += launcherViolations(application)
        return violations
    }

    /**
     * Resource references of `android:dataExtractionRules` and `android:fullBackupContent`
     * (e.g. `@xml/data_extraction_rules`), null if the attribute is missing.
     */
    fun backupRuleReferences(manifestXml: String): Pair<String?, String?> {
        val application = runCatching { parse(manifestXml) }.getOrNull()
            ?.getElementsByTagName("application")?.item(0) as? Element ?: return null to null
        return application.androidAttr("dataExtractionRules") to application.androidAttr("fullBackupContent")
    }

    private fun permissionViolations(root: Element, allowed: Set<String>): List<String> =
        listOf("uses-permission", "uses-permission-sdk-23").flatMap { tag ->
            val nodes = root.getElementsByTagName(tag)
            (0 until nodes.length).mapNotNull { index ->
                val name = (nodes.item(index) as Element).androidAttr("name") ?: "(unnamed)"
                when {
                    name in forbiddenPermissions -> "Forbidden permission: $name"
                    name !in allowed -> "Permission not on the allowlist: $name (add it deliberately to tools/checks/permissions-allowlist.txt)"
                    else -> null
                }
            }
        }

    private fun componentViolations(application: Element, extraExported: Set<String>): List<String> =
        componentTags.flatMap { tag ->
            val nodes = application.getElementsByTagName(tag)
            (0 until nodes.length).mapNotNull { index ->
                val element = nodes.item(index) as Element
                val name = element.androidAttr("name") ?: return@mapNotNull null
                val hasFilter = element.getElementsByTagName("intent-filter").length > 0
                val exported = element.androidAttr("exported")?.let { it == "true" } ?: hasFilter
                val allowed = name == LAUNCHER_ACTIVITY || name in extraExported ||
                    element.androidAttr("permission") == DUMP_PERMISSION
                if (exported && !allowed) "Exported $tag not allowed: $name" else null
            }
        }

    /** Security B2, B3, D3: attributes that store data outside the rules, on application and every component. */
    private fun storageAttributeViolations(application: Element): List<String> {
        val elements = listOf(application) + componentTags.flatMap { tag ->
            val nodes = application.getElementsByTagName(tag)
            (0 until nodes.length).map { nodes.item(it) as Element }
        }
        return elements.flatMap { element ->
            val where = listOfNotNull(element.tagName, element.androidAttr("name")).joinToString(" ")
            listOfNotNull(
                element.androidAttr("backupAgent")?.let { "android:backupAgent is not allowed on $where (Security B2)" },
                if (element.androidAttr("hasFragileUserData") == "true") {
                    "android:hasFragileUserData=\"true\" is not allowed on $where (Security B3)"
                } else null,
                if (element.androidAttr("directBootAware") == "true") {
                    "android:directBootAware=\"true\" is not allowed on $where (Security D3, DSGVO V1)"
                } else null,
            )
        }
    }

    /**
     * Security E1-E3: the launcher activity must exist (otherwise a rename would silently skip this rule) and
     * its only intent filter is exactly MAIN/LAUNCHER without data.
     */
    private fun launcherViolations(application: Element): List<String> {
        val nodes = application.getElementsByTagName("activity")
        val launcher = (0 until nodes.length).map { nodes.item(it) as Element }
            .firstOrNull { it.androidAttr("name") == LAUNCHER_ACTIVITY }
            ?: return listOf("launcher activity $LAUNCHER_ACTIVITY not found in the merged manifest (Security E2)")
        val filters = launcher.childElements("intent-filter")
        val onlyMainLauncher = filters.size == 1 && filters.single().let { filter ->
            filter.childElements("action").map { it.androidAttr("name") } == listOf(ACTION_MAIN) &&
                filter.childElements("category").map { it.androidAttr("name") } == listOf(CATEGORY_LAUNCHER) &&
                filter.childElements("data").isEmpty() &&
                filter.childElements().all { it.tagName in setOf("action", "category") }
        }
        return if (onlyMainLauncher) emptyList()
        else listOf("launcher activity must only have the MAIN/LAUNCHER intent filter (Security E2)")
    }

    private fun Element.childElements(tag: String? = null): List<Element> =
        (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
            .filter { tag == null || it.tagName == tag }

    private fun Element.androidAttr(name: String): String? =
        getAttributeNS(ANDROID_NS, name).takeIf { it.isNotEmpty() }

    private fun parse(xml: String): Element {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        return factory.newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement
    }
}
