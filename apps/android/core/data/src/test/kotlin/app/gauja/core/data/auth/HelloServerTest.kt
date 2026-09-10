// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.data.auth

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import app.gauja.core.common.Secret
import app.gauja.core.data.media.ImagesRepository
import app.gauja.core.data.servers.probeJson
import app.gauja.core.data.session.ApiSession
import app.gauja.core.database.CacheDatabase
import app.gauja.core.database.media.TitleEntity
import app.gauja.core.model.ServerAddress
import app.gauja.core.model.images.PosterSize
import app.gauja.core.model.media.MediaType
import app.gauja.core.model.media.TitleSummary
import app.gauja.core.model.media.TmdbId
import app.gauja.core.model.servers.Cached
import app.gauja.core.model.servers.ProfileId
import app.gauja.core.model.servers.PublicSettings
import app.gauja.core.model.servers.ServerProfile
import app.gauja.core.model.status.MediaServerType
import app.gauja.core.model.status.MediaStatus
import app.gauja.core.network.DeprecationStore
import app.gauja.core.network.ProfileImages
import app.gauja.core.network.ProfileTransport
import app.gauja.core.testing.CachedTitleRenderer
import app.gauja.core.testing.CachedTitleState
import app.gauja.core.testing.FakeClock
import app.gauja.core.testing.FixtureLoader
import app.gauja.core.testing.MemoryProfiles
import app.gauja.core.testing.MemorySecrets
import coil3.toBitmap
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
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
class HelloServerTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun recordedSessionCachesUserAndRendersOfflineWithoutRequests() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .addHeader(
                        "Set-Cookie",
                        "connect.sid=synthetic-session; Path=/; Max-Age=2592000",
                    )
                    .body(FixtureLoader.read("auth/postAuthLocal.json"))
                    .build()
            )
            server.enqueue(
                MockResponse.Builder().body(FixtureLoader.read("auth/getAuthMe.json")).build()
            )
            server.enqueue(
                MockResponse.Builder()
                    .addHeader("Content-Type", "image/png")
                    .body(Buffer().write(FixtureLoader.artwork()))
                    .build()
            )
            hello(
                server.url("/").toString(),
                "admin@example.invalid",
                Secret("synthetic-password".toByteArray()),
            )
            assertEquals("/api/v1/auth/local", server.takeRequest().target)
            assertEquals("connect.sid=synthetic-session", server.takeRequest().headers["Cookie"])
            assertEquals(null, server.takeRequest().headers["Cookie"])
            assertEquals(3, server.requestCount)
        }
    }

    @Test
    fun liveInitializedSeerrHelloServer() = runTest {
        val base = System.getenv("GAUJA_AUTH_SERVER")
        val credentials = System.getenv("GAUJA_AUTH_CREDENTIALS")
        assumeNotNull(base, credentials)
        val input = Json.parseToJsonElement(File(requireNotNull(credentials)).readText()).jsonObject
        val email = input.getValue("email").jsonPrimitive.content
        val password = Secret(input.getValue("password").jsonPrimitive.content.toByteArray())
        hello(requireNotNull(base), email, password, control = true)
    }

    private suspend fun hello(
        base: String,
        email: String,
        password: Secret,
        control: Boolean = false,
    ) {
        val profiles = MemoryProfiles()
        val profile =
            ServerProfile(
                ProfileId(UUID.randomUUID()),
                "Local test",
                requireNotNull(ServerAddress.parse(base)),
            )
        val configured =
            profile.copy(
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
                        FakeClock().instant(),
                    )
            )
        profiles.save(configured)
        val clock = FakeClock()
        val transport = ProfileTransport(MemorySecrets(), DeprecationStore(), clock, Dispatchers.IO)
        val database =
            Room.databaseBuilder<CacheDatabase>(
                    RuntimeEnvironment.getApplication(),
                    folder.root.resolve("cache.db").path,
                )
                .setDriver(AndroidSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        val imageCache = ProfileImages(RuntimeEnvironment.getApplication())
        try {
            val images = ImagesRepository(profiles, transport, imageCache)
            val repository =
                LiveAuthRepository(
                    ApiSession(profiles, transport, probeJson()),
                    transport,
                    database.users(),
                    clock,
                )
            repository.signInLocal(profile.id, email, password)
            val user = requireNotNull(repository.observeUser(profile.id).first())
            assertEquals(email, user.value.email)
            assertEquals(clock.instant(), user.fetchedAt)
            database
                .titles()
                .upsert(
                    TitleEntity(
                        profile.id.value.toString(),
                        "movie",
                        42,
                        "Synthetic cached title",
                        2026,
                        null,
                        8.2,
                        999,
                        null,
                        clock.millis(),
                    )
                )
            images.load(profile.id, "/gauja-test.png", PosterSize.MEDIUM, offline = false)
            if (control)
                withContext(Dispatchers.IO) {
                    val connection =
                        URI(base + "/__gauja_test/offline").toURL().openConnection()
                            as HttpURLConnection
                    try {
                        connection.requestMethod = "POST"
                        check(connection.responseCode == 204)
                    } finally {
                        connection.disconnect()
                    }
                }
            // The app is already running; warm the Compose host before measuring cached
            // read-through.
            val rendered =
                mutableStateOf(
                    TitleSummary(
                        TmdbId(1),
                        MediaType.MOVIE,
                        "Warmup",
                        null,
                        null,
                        null,
                        MediaStatus.UNKNOWN,
                    )
                )
            val fetchedAt = mutableStateOf(clock.instant())
            val displayedPoster = mutableStateOf<BitmapPainter?>(null)
            compose.setContent {
                CachedTitleRenderer(
                    CachedTitleState.Offline(Cached(rendered.value, fetchedAt.value)),
                    poster = displayedPoster.value,
                )
            }
            compose.waitForIdle()
            val started = System.nanoTime()
            val artwork =
                images.load(profile.id, "/gauja-test.png", PosterSize.MEDIUM, offline = true)
            val poster = BitmapPainter(artwork.toBitmap().asImageBitmap())
            val cached =
                requireNotNull(
                    database.titles().observe(profile.id.value.toString(), "movie", 42).first()
                )
            val title =
                TitleSummary(
                    TmdbId(cached.tmdbId),
                    MediaType.fromWire(cached.mediaType),
                    cached.title,
                    cached.year,
                    cached.posterPath,
                    cached.rating,
                    MediaStatus.fromWire(cached.status),
                )
            compose.runOnIdle {
                rendered.value = title
                fetchedAt.value = java.time.Instant.ofEpochMilli(cached.fetchedAt)
                displayedPoster.value = poster
            }
            compose.onNodeWithText("Synthetic cached title").assertIsDisplayed()
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            println("cached-render-ms=$elapsedMs")
            assertTrue("Cached read-through took ${elapsedMs}ms", elapsedMs <= 300)
            compose.onNodeWithText("Unknown status").assertIsDisplayed()
        } finally {
            imageCache.close()
            database.close()
            transport.delete(profile.id) {}
        }
    }
}
