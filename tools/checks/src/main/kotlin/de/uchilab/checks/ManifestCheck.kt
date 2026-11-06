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

    /**
     * @param allowedPermissions every `uses-permission` must be on this list (may be empty);
     *   the forbidden permissions stay forbidden even if they are on it
     */
    fun check(
        manifestXml: String,
        extraExported: Set<String> = emptySet(),
        allowedPermissions: Set<String> = emptySet(),
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
        violations += componentViolations(application, extraExported)
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
