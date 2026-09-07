// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.compat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureGateTest {
    @Test
    fun bundledCapabilitiesFailClosed() {
        val gate = FeatureGate.bundled()
        assertTrue(gate.isSupported("blocklist", ServerVersion.parse("3.4.1")))
        assertTrue(gate.isSupported("blocklist", ServerVersion.parse("4.0.0+build")))
        assertFalse(gate.isSupported("blocklist", ServerVersion.parse("3.4.0")))
        assertFalse(gate.isSupported("blocklist", ServerVersion.parse("3.4.1-rc.1")))
        assertFalse(gate.isSupported("blocklist", null))
        assertFalse(gate.isSupported("missing", ServerVersion.parse("3.4.1")))
    }

    @Test
    fun semverPrereleaseOrderingAndValidation() {
        val values =
            listOf(
                "3.4.1-alpha",
                "3.4.1-alpha.1",
                "3.4.1-alpha.2",
                "3.4.1-alpha.10",
                "3.4.1-beta",
                "3.4.1",
            )
        val parsed = values.map { requireNotNull(ServerVersion.parse(it)) }
        assertTrue(parsed.zipWithNext().all { (a, b) -> a < b })
        listOf("3.4.1-01", "3.4.1-a..b", "3.4.1+", "3.4.1-a+b+c").forEach {
            assertNull(ServerVersion.parse(it))
        }
    }

    @Test
    fun maximumIsInclusiveAndInvalidBoundsDeny() {
        val gate =
            FeatureGate(mapOf("limited" to FeatureMetadata("3.4.1", "3.5.0", "/test", "test")))
        assertTrue(gate.isSupported("limited", ServerVersion.parse("3.5.0")))
        assertFalse(gate.isSupported("limited", ServerVersion.parse("3.5.1")))
        val invalid = FeatureGate(mapOf("bad" to FeatureMetadata("3.4.1", "bad", "/test", "test")))
        assertFalse(invalid.isSupported("bad", ServerVersion.parse("3.4.1")))
    }
}
