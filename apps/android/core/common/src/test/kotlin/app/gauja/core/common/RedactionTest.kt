// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RedactionTest {
    @Test
    fun everySecretAndHostIsRemovedFromDiagnostics() {
        val values = listOf("session-value", "operator-value", "basic-value", "plex-value")
        val secrets = values.map { Secret(it.toByteArray()) }
        val raw =
            values.joinToString("\n") +
                "\nhttps://private.example/seerr\nAuthorization: Basic encoded\nCookie: connect.sid=unlisted"
        val safe = Redaction.text(raw, secrets, listOf("private.example"))
        for (value in values + listOf("private.example", "encoded", "unlisted")) assertFalse(
            safe.contains(value)
        )
        assertEquals("[REDACTED]", secrets.first().toString())
    }

    @Test
    fun borrowedBytesAreIndependentAndCleared() {
        val original = "test".toByteArray()
        val secret = Secret(original)
        original.fill(0)
        val borrowed = secret.useBytes { it }
        assertEquals(listOf<Byte>(0, 0, 0, 0), borrowed.toList())
        assertEquals("test", secret.useBytes { it.toString(Charsets.UTF_8) })
    }
}
