package de.uchilab.checks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestCheckTest {

    private fun manifest(
        permissions: String = "",
        applicationAttributes: String = """android:allowBackup="false"""",
        components: String = LAUNCHER,
    ) = """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="de.uchilab.todo">
            $permissions
            <application $applicationAttributes>
                $components
            </application>
        </manifest>
    """.trimIndent()

    @Test
    fun `clean manifest has no violations`() {
        assertEquals(emptyList<String>(), ManifestCheck.check(manifest()))
    }

    @Test
    fun `INTERNET permission is a violation`() {
        val violations = ManifestCheck.check(
            manifest(permissions = """<uses-permission android:name="android.permission.INTERNET"/>"""),
        )
        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("android.permission.INTERNET"))
    }

    @Test
    fun `ACCESS_NETWORK_STATE permission is a violation`() {
        val violations = ManifestCheck.check(
            manifest(permissions = """<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>"""),
        )
        assertTrue(violations.single().contains("ACCESS_NETWORK_STATE"))
    }

    @Test
    fun `cleartext traffic is a violation`() {
        val violations = ManifestCheck.check(
            manifest(applicationAttributes = """android:allowBackup="false" android:usesCleartextTraffic="true""""),
        )
        assertTrue(violations.single().contains("usesCleartextTraffic"))
    }

    @Test
    fun `allowBackup true is a violation`() {
        val violations = ManifestCheck.check(
            manifest(applicationAttributes = """android:allowBackup="true""""),
        )
        assertTrue(violations.single().contains("allowBackup"))
    }

    @Test
    fun `missing allowBackup is a violation because the default is true`() {
        val violations = ManifestCheck.check(manifest(applicationAttributes = ""))
        assertTrue(violations.single().contains("allowBackup"))
    }

    @Test
    fun `additional exported component is a violation`() {
        // receiver, and provider and service (former counterprobes 12a and 12b, plan UCH-125 section 6).
        listOf("receiver", "provider", "service").forEach { tag ->
            val violations = ManifestCheck.check(
                manifest(components = LAUNCHER + """<$tag android:name="com.example.Evil" android:exported="true"/>"""),
            )
            assertTrue("$tag -> $violations", violations.single().contains("com.example.Evil"))
        }
    }

    @Test
    fun `component with intent filter but without exported is a violation`() {
        val violations = ManifestCheck.check(
            manifest(
                components = LAUNCHER + """
                    <service android:name="com.example.Svc">
                        <intent-filter><action android:name="x"/></intent-filter>
                    </service>
                """.trimIndent(),
            ),
        )
        assertTrue(violations.single().contains("com.example.Svc"))
    }

    @Test
    fun `non exported components are fine`() {
        val violations = ManifestCheck.check(
            manifest(components = LAUNCHER + """<provider android:name="x.P" android:exported="false"/>"""),
        )
        assertEquals(emptyList<String>(), violations)
    }

    @Test
    fun `exported component protected by DUMP permission is accepted`() {
        // androidx.profileinstaller adds such a receiver; only the system can trigger it.
        val violations = ManifestCheck.check(
            manifest(
                components = LAUNCHER + """
                    <receiver android:name="androidx.profileinstaller.ProfileInstallReceiver"
                        android:exported="true" android:permission="android.permission.DUMP"/>
                """.trimIndent(),
            ),
        )
        assertEquals(emptyList<String>(), violations)
    }

    @Test
    fun `explicitly allowed extra exported component is accepted, others still fail`() {
        val xml = manifest(
            components = LAUNCHER + """
                <activity android:name="x.Helper" android:exported="true"/>
                <activity android:name="x.Other" android:exported="true"/>
            """.trimIndent(),
        )
        val violations = ManifestCheck.check(xml, extraExported = setOf("x.Helper"))
        assertTrue(violations.single().contains("x.Other"))
    }

    @Test
    fun `permission not on the allowlist is a violation`() {
        val violations = ManifestCheck.check(
            manifest(permissions = """<uses-permission android:name="android.permission.READ_CONTACTS"/>"""),
        )
        assertTrue(violations.single().contains("android.permission.READ_CONTACTS"))
    }

    @Test
    fun `allowlisted permission is accepted, also as uses-permission-sdk-23`() {
        val xml = manifest(
            permissions = """
                <uses-permission android:name="x.ALLOWED"/>
                <uses-permission-sdk-23 android:name="x.ALLOWED"/>
                <uses-permission-sdk-23 android:name="x.OTHER"/>
            """.trimIndent(),
        )
        val violations = ManifestCheck.check(xml, allowedPermissions = setOf("x.ALLOWED"))
        assertTrue(violations.single().contains("x.OTHER"))
    }

    @Test
    fun `forbidden permission stays forbidden even if allowlisted`() {
        val violations = ManifestCheck.check(
            manifest(permissions = """<uses-permission android:name="android.permission.INTERNET"/>"""),
            allowedPermissions = setOf("android.permission.INTERNET"),
        )
        assertTrue(violations.single().startsWith("Forbidden permission"))
    }

    @Test
    fun `backup rule references are read from the application element`() {
        val xml = manifest(
            applicationAttributes = """android:allowBackup="false" android:dataExtractionRules="@xml/a" android:fullBackupContent="@xml/b"""",
        )
        assertEquals("@xml/a" to "@xml/b", ManifestCheck.backupRuleReferences(xml))
        assertEquals(null to null, ManifestCheck.backupRuleReferences(manifest()))
    }

    @Test
    fun `debuggable is a violation in the release manifest only`() {
        val xml = manifest(applicationAttributes = """android:allowBackup="false" android:debuggable="true"""")
        assertTrue(ManifestCheck.check(xml, release = true).single().contains("Security R1"))
        assertEquals(emptyList<String>(), ManifestCheck.check(xml, release = false))
        val notDebuggable = manifest(applicationAttributes = """android:allowBackup="false" android:debuggable="false"""")
        assertEquals(emptyList<String>(), ManifestCheck.check(notDebuggable, release = true))
    }

    @Test
    fun `backupAgent is a violation on application and on a component, in debug and release`() {
        val onApplication = manifest(applicationAttributes = """android:allowBackup="false" android:backupAgent=".Agent"""")
        val onService = manifest(
            components = LAUNCHER + """<service android:name="x.S" android:exported="false" android:backupAgent="x.A"/>""",
        )
        listOf(true, false).forEach { release ->
            assertTrue(ManifestCheck.check(onApplication, release = release).single().contains("Security B2"))
            assertTrue(ManifestCheck.check(onService, release = release).single().contains("service x.S"))
        }
    }

    @Test
    fun `hasFragileUserData true is a violation, false is fine`() {
        val fragile = manifest(applicationAttributes = """android:allowBackup="false" android:hasFragileUserData="true"""")
        assertTrue(ManifestCheck.check(fragile).single().contains("Security B3"))
        val notFragile = manifest(applicationAttributes = """android:allowBackup="false" android:hasFragileUserData="false"""")
        assertEquals(emptyList<String>(), ManifestCheck.check(notFragile))
    }

    @Test
    fun `directBootAware true is a violation on application and on a receiver, false is fine`() {
        val onApplication = manifest(applicationAttributes = """android:allowBackup="false" android:directBootAware="true"""")
        assertTrue(ManifestCheck.check(onApplication).single().contains("Security D3"))
        val onReceiver = manifest(
            components = LAUNCHER + """<receiver android:name="x.R" android:exported="false" android:directBootAware="true"/>""",
        )
        assertTrue(ManifestCheck.check(onReceiver, release = true).single().contains("receiver x.R"))
        // androidx.profileinstaller ships its receiver with directBootAware="false".
        val libraryFalse = manifest(
            components = LAUNCHER + """<receiver android:name="x.R" android:exported="false" android:directBootAware="false"/>""",
        )
        assertEquals(emptyList<String>(), ManifestCheck.check(libraryFalse, release = true))
    }

    @Test
    fun `storage attributes are violations on every component type`() {
        val attributes = listOf(
            """android:backupAgent="x.A"""" to "Security B2",
            """android:hasFragileUserData="true"""" to "Security B3",
            """android:directBootAware="true"""" to "Security D3",
        )
        listOf("activity", "activity-alias", "service", "receiver", "provider").forEach { tag ->
            attributes.forEach { (attribute, reference) ->
                val xml = manifest(components = LAUNCHER + """<$tag android:name="x.C" android:exported="false" $attribute/>""")
                val violation = ManifestCheck.check(xml, release = true).single()
                assertTrue("$tag $attribute -> $violation", violation.contains("$tag x.C") && violation.contains(reference))
            }
        }
    }

    @Test
    fun `missing launcher activity is a violation`() {
        val violations = ManifestCheck.check(
            manifest(components = LAUNCHER.replace("ui.MainActivity", "ui.RenamedActivity")),
        )
        assertTrue(violations.any { it.contains("launcher activity de.uchilab.todo.ui.MainActivity not found") })
    }

    @Test
    fun `second intent filter on the launcher activity is a violation`() {
        val launcher = LAUNCHER.replace(
            "</activity>",
            """<intent-filter><action android:name="android.intent.action.VIEW"/></intent-filter></activity>""",
        )
        assertTrue(ManifestCheck.check(manifest(components = launcher)).single().contains("Security E2"))
    }

    @Test
    fun `SEND action in the launcher intent filter is a violation`() {
        val launcher = LAUNCHER.replace(
            "<category",
            """<action android:name="android.intent.action.SEND"/><category""",
        )
        assertTrue(ManifestCheck.check(manifest(components = launcher)).single().contains("Security E2"))
    }

    @Test
    fun `additional category in the launcher intent filter is a violation`() {
        val launcher = LAUNCHER.replace(
            "</intent-filter>",
            """<category android:name="android.intent.category.DEFAULT"/></intent-filter>""",
        )
        assertTrue(ManifestCheck.check(manifest(components = launcher)).single().contains("Security E2"))
    }

    @Test
    fun `data in the launcher intent filter is a violation`() {
        val launcher = LAUNCHER.replace("</intent-filter>", """<data android:mimeType="text/plain"/></intent-filter>""")
        assertTrue(ManifestCheck.check(manifest(components = launcher)).single().contains("Security E2"))
    }

    @Test
    fun `malformed xml is reported instead of crashing`() {
        val violations = ManifestCheck.check("<manifest")
        assertTrue(violations.single().contains("parse"))
    }

    private companion object {
        const val LAUNCHER = """
            <activity android:name="de.uchilab.todo.ui.MainActivity" android:exported="true">
                <intent-filter>
                    <action android:name="android.intent.action.MAIN"/>
                    <category android:name="android.intent.category.LAUNCHER"/>
                </intent-filter>
            </activity>
        """
    }
}
