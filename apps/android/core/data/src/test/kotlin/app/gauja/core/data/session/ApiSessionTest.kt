// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.data.session

import app.gauja.core.common.AppError
import app.gauja.core.common.AppException
import java.io.IOException
import java.net.ConnectException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ApiSessionTest {
    @Test
    fun transportFailuresBecomeSafeCategoriesAndCancellationSurvives() = runTest {
        val fallback =
            ConnectException("untrusted server details").apply {
                addSuppressed(SSLHandshakeException("untrusted certificate details"))
            }
        for ((failure, category) in
            listOf(
                fallback to AppError.TLS,
                ConnectException("untrusted") to AppError.OFFLINE,
                IOException("untrusted response") to AppError.NETWORK,
            )) {
            val actual = runCatching { safeApi<Unit> { throw failure } }.exceptionOrNull()
            require(actual is AppException)
            assertEquals(category, actual.reason)
            assertEquals(category.messageKey, actual.message)
            assertNull(actual.cause)
        }
        val cancelled = CancellationException("cancelled")
        assertSame(cancelled, runCatching { safeApi<Unit> { throw cancelled } }.exceptionOrNull())
    }
}
