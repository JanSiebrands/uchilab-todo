package de.uchilab.checks

import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader

/**
 * "Local only" also covers Android Auto Backup: nothing may be backed up to the cloud or transferred
 * to a new device or to iOS. Checks the contents of the XML files referenced by
 * `android:dataExtractionRules` (Android 12+) and `android:fullBackupContent` (Android 11 and older).
 */
object BackupRulesCheck {
    /** All domains of Auto Backup, including device-protected storage. */
    val DOMAINS = listOf(
        "root", "file", "database", "sharedpref", "external",
        "device_root", "device_file", "device_database", "device_sharedpref",
    )

    /** Rules for `<data-extraction-rules>`: both sections must exist (missing section = default = backup). */
    fun checkDataExtractionRules(xml: String): List<String> {
        val root = parseOrNull(xml) ?: return listOf("data extraction rules could not be parsed")
        if (root.tagName != "data-extraction-rules") {
            return listOf("data extraction rules: root element must be <data-extraction-rules>, found <${root.tagName}>")
        }
        val androidSections = listOf("cloud-backup", "device-transfer").flatMap { section ->
            val element = root.childElements().singleOrNull { it.tagName == section }
            if (element == null) {
                listOf("data extraction rules: <$section> is missing or duplicated, so $section is on by default")
            } else {
                sectionViolations(element, "data extraction rules <$section>")
            }
        }
        // Transfer to iOS: the section must exist for platform "ios" (missing section = default = transfer).
        val ios = root.childElements()
            .singleOrNull { it.tagName == "cross-platform-transfer" && it.getAttribute("platform") == "ios" }
        val iosSection = if (ios == null) {
            listOf(
                "data extraction rules: <cross-platform-transfer platform=\"ios\"> is missing or duplicated, " +
                    "so transfer to iOS is on by default",
            )
        } else {
            sectionViolations(ios, "data extraction rules <cross-platform-transfer platform=\"ios\">")
        }
        return androidSections + iosSection
    }

    /** Rules for `<full-backup-content>`. */
    fun checkFullBackupContent(xml: String): List<String> {
        val root = parseOrNull(xml) ?: return listOf("full backup content could not be parsed")
        if (root.tagName != "full-backup-content") {
            return listOf("full backup content: root element must be <full-backup-content>, found <${root.tagName}>")
        }
        return sectionViolations(root, "full backup content")
    }

    private fun sectionViolations(section: Element, label: String): List<String> {
        val violations = mutableListOf<String>()
        if (section.getElementsByTagName("include").length > 0) violations += "$label: <include> is not allowed"
        val excluded = section.childElements()
            .filter { it.tagName == "exclude" && it.getAttribute("path").let { p -> p.isEmpty() || p == "." } }
            .map { it.getAttribute("domain") }
            .toSet()
        DOMAINS.filterNot { it in excluded }.forEach { violations += "$label: domain $it is not excluded" }
        return violations
    }

    private fun Element.childElements(): List<Element> =
        (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }

    private fun parseOrNull(xml: String): Element? = try {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        factory.newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement
    } catch (e: Exception) {
        null
    }
}
