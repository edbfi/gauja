// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.network

import android.content.ContextWrapper
import app.gauja.core.common.AppError
import app.gauja.core.common.AppException
import app.gauja.core.model.ServerAddress
import app.gauja.core.model.images.PosterSize
import app.gauja.core.model.servers.Cached
import app.gauja.core.model.servers.ProfileId
import app.gauja.core.model.servers.PublicSettings
import app.gauja.core.model.servers.ServerProfile
import app.gauja.core.model.status.MediaServerType
import app.gauja.core.testing.FixtureLoader
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileImagesTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun diskCacheReopensOfflineAndDeletingOneProfilePreservesTheOther() = runTest {
        val context =
            object : ContextWrapper(RuntimeEnvironment.getApplication()) {
                override fun getCacheDir() = folder.root
            }
        MockWebServer().use { server ->
            server.start()
            repeat(2) {
                server.enqueue(
                    MockResponse.Builder()
                        .addHeader("Content-Type", "image/png")
                        .body(Buffer().write(FixtureLoader.artwork()))
                        .build()
                )
            }
            val first =
                ServerProfile(
                    ProfileId(UUID.randomUUID()),
                    "First",
                    requireNotNull(ServerAddress.parse(server.url("/").toString())),
                    publicSettings =
                        Cached(
                            PublicSettings(
                                "Test",
                                true,
                                true,
                                false,
                                MediaServerType.NOT_CONFIGURED,
                                true,
                            ),
                            Instant.EPOCH,
                        ),
                )
            val second = first.copy(id = ProfileId(UUID.randomUUID()), displayName = "Second")
            ProfileImages(context).use { cache ->
                cache.load(first, "/gauja-test.png", PosterSize.MEDIUM, false)
                cache.load(second, "/gauja-test.png", PosterSize.MEDIUM, false)
            }
            ProfileImages(context).use { cache ->
                assertNotNull(cache.load(first, "/gauja-test.png", PosterSize.MEDIUM, true))
                assertNotNull(cache.load(second, "/gauja-test.png", PosterSize.MEDIUM, true))
                cache.clear(first.id)
                val failure =
                    try {
                        cache.load(first, "/gauja-test.png", PosterSize.MEDIUM, true)
                        null
                    } catch (error: AppException) {
                        error
                    }
                assertEquals(AppError.OFFLINE, failure?.reason)
                assertNotNull(cache.load(second, "/gauja-test.png", PosterSize.MEDIUM, true))
                assertEquals(2, server.requestCount)
            }
        }
    }
}
