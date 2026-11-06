package de.uchilab.checks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRulesCheckTest {
    private val excludes = BackupRulesCheck.DOMAINS.joinToString("\n") { """<exclude domain="$it" />""" }

    private fun extraction(cloud: String? = excludes, transfer: String? = excludes) =
        "<data-extraction-rules>" +
            (cloud?.let { "<cloud-backup>$it</cloud-backup>" } ?: "") +
            (transfer?.let { "<device-transfer>$it</device-transfer>" } ?: "") +
            "</data-extraction-rules>"

    @Test
    fun `everything excluded is clean`() {
        assertEquals(emptyList<String>(), BackupRulesCheck.checkDataExtractionRules(extraction()))
        assertEquals(emptyList<String>(), BackupRulesCheck.checkFullBackupContent("<full-backup-content>$excludes</full-backup-content>"))
    }

    @Test
    fun `missing cloud-backup section is a violation`() {
        assertTrue(BackupRulesCheck.checkDataExtractionRules(extraction(cloud = null)).single().contains("cloud-backup"))
    }

    @Test
    fun `missing device-transfer section is a violation`() {
        assertTrue(BackupRulesCheck.checkDataExtractionRules(extraction(transfer = null)).single().contains("device-transfer"))
    }

    @Test
    fun `empty section backs up every domain`() {
        assertEquals(BackupRulesCheck.DOMAINS.size, BackupRulesCheck.checkDataExtractionRules(extraction(cloud = "")).size)
    }

    @Test
    fun `include is a violation`() {
        val xml = "<full-backup-content>$excludes<include domain=\"file\" path=\".\" /></full-backup-content>"
        assertTrue(BackupRulesCheck.checkFullBackupContent(xml).single().contains("include"))
    }

    @Test
    fun `exclude of a sub path does not count as excluded domain`() {
        val partial = excludes.replace("""<exclude domain="device_file" />""", """<exclude domain="device_file" path="cache" />""")
        assertTrue(BackupRulesCheck.checkFullBackupContent("<full-backup-content>$partial</full-backup-content>").single().contains("device_file"))
    }

    @Test
    fun `wrong root element and broken xml are violations`() {
        assertEquals(1, BackupRulesCheck.checkFullBackupContent(extraction()).size)
        assertEquals(1, BackupRulesCheck.checkDataExtractionRules("<data-extraction-rules>").size)
    }
}
