// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.data.servers

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import app.gauja.core.common.Secret
import app.gauja.core.data.session.ApiSession
import app.gauja.core.database.CacheDatabase
import app.gauja.core.database.media.TitleEntity
import app.gauja.core.database.users.UserEntity
import app.gauja.core.datastore.preferences.PreferencesStore
import app.gauja.core.datastore.secrets.SecretKind
import app.gauja.core.model.ServerAddress
import app.gauja.core.model.servers.Cached
import app.gauja.core.model.servers.ProfileId
import app.gauja.core.model.servers.ServerProfile
import app.gauja.core.model.servers.ServerStatus
import app.gauja.core.network.DeprecationStore
import app.gauja.core.network.ProfileImages
import app.gauja.core.network.ProfileTransport
import app.gauja.core.testing.FakeClock
import app.gauja.core.testing.MemoryProfiles
import app.gauja.core.testing.MemorySecrets
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ServerEditTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun originEditsWaitForRequestsAndWipeOnlyTheEditedServer() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val database =
            Room.databaseBuilder<CacheDatabase>(context, folder.root.resolve("cache.db").path)
                .setDriver(AndroidSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        val images = ProfileImages(context)
        val preferenceJob = SupervisorJob()
        val preferences =
            PreferencesStore(
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(preferenceJob + Dispatchers.IO),
                    produceFile = { folder.root.resolve("test.preferences_pb") },
                )
            )
        val profiles = MemoryProfiles()
        val secrets = MemorySecrets()
        val clock = FakeClock()
        val transport = ProfileTransport(secrets, DeprecationStore(), clock, Dispatchers.IO)
        val sessions = ApiSession(profiles, transport, probeJson())
        val repository =
            LiveServersRepository(
                profiles,
                sessions,
                transport,
                secrets,
                preferences,
                database.users(),
                database.titles(),
                clock,
                images,
            )
        val original =
            ServerProfile(
                ProfileId(UUID.randomUUID()),
                "First",
                requireNotNull(ServerAddress.parse("https://first.example")),
                status = Cached(ServerStatus("3.4.1", null, false, false), clock.instant()),
            )
        val other = original.copy(id = ProfileId(UUID.randomUUID()))
        try {
            for (profile in listOf(original, other)) {
                repository.save(profile)
                val id = profile.id.value.toString()
                database
                    .users()
                    .upsert(
                        UserEntity(id, 1, "Reader", "reader@example.invalid", 32, clock.millis())
                    )
                database
                    .titles()
                    .upsert(
                        TitleEntity(
                            id,
                            "movie",
                            42,
                            "Synthetic",
                            null,
                            null,
                            null,
                            null,
                            null,
                            clock.millis(),
                        )
                    )
                for (kind in SecretKind.entries) secrets.write(
                    profile,
                    kind,
                    Secret("synthetic".toByteArray()),
                )
            }
            val renamed =
                original.copy(
                    displayName = "Renamed",
                    address = requireNotNull(ServerAddress.parse("https://FIRST.example:443/path")),
                )
            repository.save(renamed)
            assertNotNull(database.users().observe(original.id.value.toString()).first())
            assertNotNull(secrets.read(renamed, SecretKind.BASIC_AUTH_PASSWORD))
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val request =
                async(Dispatchers.IO) {
                    sessions.use(original.id) { profile, _ ->
                        started.complete(Unit)
                        release.await()
                        profiles.save(profile)
                    }
                }
            started.await()
            val changed =
                original.copy(
                    address = requireNotNull(ServerAddress.parse("https://second.example"))
                )
            val edit = async(Dispatchers.IO) { repository.save(changed) }
            kotlinx.coroutines.withContext(Dispatchers.IO) { delay(50) }
            assertEquals(
                renamed.address,
                profiles.profiles.value.first { it.id == original.id }.address,
            )
            release.complete(Unit)
            request.await()
            edit.await()
            val saved = profiles.profiles.value.first { it.id == original.id }
            assertEquals(changed.address, saved.address)
            assertNull(saved.status)
            assertNull(saved.publicSettings)
            assertNull(database.users().observe(original.id.value.toString()).first())
            assertNull(database.titles().observe(original.id.value.toString(), "movie", 42).first())
            assertNotNull(database.users().observe(other.id.value.toString()).first())
            assertNotNull(database.titles().observe(other.id.value.toString(), "movie", 42).first())
            for (kind in SecretKind.entries) {
                assertNull(secrets.read(original, kind))
                assertNull(secrets.read(changed, kind))
                assertNotNull(secrets.read(other, kind))
            }
        } finally {
            transport.delete(original.id) {}
            transport.delete(other.id) {}
            images.close()
            database.close()
            preferenceJob.cancel()
            preferenceJob.join()
        }
    }
}
