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
        val violations = ManifestCheck.check(
            manifest(
                components = LAUNCHER + """<receiver android:name="com.example.Evil" android:exported="true"/>""",
            ),
        )
        assertTrue(violations.single().contains("com.example.Evil"))
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
