package de.uchilab.checks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DependencyPolicyTest {

    private val policy = DependencyPolicy.parse(
        allowlist = """
            # comment
            androidx.*
            junit
            org.jetbrains.kotlin.*
        """.trimIndent(),
        denylist = """
            com.google.firebase.*
            com.google.android.gms.*
        """.trimIndent(),
    )

    @Test
    fun `allowed coordinates pass`() {
        val violations = policy.check(listOf("androidx.core:core-ktx:1.0", "junit:junit:4.13.2", "org.jetbrains.kotlin:kotlin-stdlib:2"))
        assertEquals(emptyList<String>(), violations)
    }

    @Test
    fun `unknown group fails with allowlist hint`() {
        val violations = policy.check(listOf("com.example:lib:1.0"))
        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("com.example:lib:1.0"))
        assertTrue(violations.single().contains("allowlist"))
    }

    @Test
    fun `denylisted tracker fails with denylist message`() {
        val violations = policy.check(listOf("com.google.firebase:firebase-analytics:21.0"))
        assertEquals(1, violations.size)
        assertTrue(violations.single().contains("denylist"))
    }

    @Test
    fun `denylist wins even if allowlist would match`() {
        val withBoth = DependencyPolicy.parse(allowlist = "com.google.*", denylist = "com.google.firebase.*")
        assertTrue(withBoth.check(listOf("com.google.firebase:x:1")).single().contains("denylist"))
    }

    @Test
    fun `prefix pattern does not match lookalike groups`() {
        val violations = policy.check(listOf("androidxevil:lib:1.0"))
        assertEquals(1, violations.size)
    }

    @Test
    fun `exact pattern does not match subgroups`() {
        val violations = policy.check(listOf("junit.evil:lib:1.0"))
        assertEquals(1, violations.size)
    }

    @Test
    fun `duplicates are reported once`() {
        val violations = policy.check(listOf("com.example:lib:1.0", "com.example:lib:1.0"))
        assertEquals(1, violations.size)
    }

    @Test
    fun `artifact pattern allows only that artifact`() {
        val single = DependencyPolicy.parse(allowlist = "com.google.guava:listenablefuture", denylist = "")
        assertEquals(emptyList<String>(), single.check(listOf("com.google.guava:listenablefuture:1.0")))
        assertEquals(1, single.check(listOf("com.google.guava:guava:33.0")).size)
    }
}
