// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.data.servers

import app.gauja.core.api.apis.PublicApi
import app.gauja.core.api.apis.SettingsApi
import app.gauja.core.data.session.ApiSession
import app.gauja.core.data.session.checked
import app.gauja.core.data.session.safeApi
import app.gauja.core.database.media.TitleDao
import app.gauja.core.database.users.UserDao
import app.gauja.core.datastore.preferences.PreferencesStore
import app.gauja.core.datastore.profiles.ServerProfileStore
import app.gauja.core.datastore.secrets.SecretStore
import app.gauja.core.model.servers.Cached
import app.gauja.core.model.servers.ProfileId
import app.gauja.core.model.servers.PublicSettings
import app.gauja.core.model.servers.ServerProfile
import app.gauja.core.model.servers.ServerStatus
import app.gauja.core.model.status.MediaServerType
import app.gauja.core.network.ProfileTransport
import java.time.Clock
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ServersRepository {
    val profiles: Flow<ImmutableList<ServerProfile>>

    suspend fun save(profile: ServerProfile)

    suspend fun refresh(profileId: ProfileId)

    suspend fun delete(profileId: ProfileId)
}

internal class LiveServersRepository
@Inject
constructor(
    private val store: ServerProfileStore,
    private val sessions: ApiSession,
    private val transport: ProfileTransport,
    private val secrets: SecretStore,
    private val preferences: PreferencesStore,
    private val users: UserDao,
    private val titles: TitleDao,
    private val clock: Clock,
) : ServersRepository {
    override val profiles = store.profiles.map { it.toImmutableList() }

    override suspend fun save(profile: ServerProfile) = safeApi { store.save(profile) }

    override suspend fun refresh(profileId: ProfileId) {
        sessions.use(profileId) { profile, retrofit ->
            val status = retrofit.create(PublicApi::class.java).getStatus(false).checked()
            val settings = retrofit.create(SettingsApi::class.java).getSettingsPublic().checked()
            val now = clock.instant()
            store.save(
                profile.copy(
                    status =
                        Cached(
                            ServerStatus(
                                status.version,
                                status.commitTag,
                                status.updateAvailable,
                                status.restartRequired,
                            ),
                            now,
                        ),
                    publicSettings =
                        Cached(
                            PublicSettings(
                                settings.applicationTitle,
                                settings.initialized,
                                settings.localLogin,
                                settings.mediaServerLogin,
                                MediaServerType.fromWire(
                                    settings.mediaServerType
                                        ?.takeIf { it.isFinite() && it % 1.0 == 0.0 }
                                        ?.toInt()
                                ),
                                settings.cacheImages,
                                newPlexLogin = settings.newPlexLogin,
                            ),
                            now,
                        ),
                )
            )
        }
    }

    override suspend fun delete(profileId: ProfileId) = safeApi {
        transport.delete(profileId) {
            secrets.clear(profileId)
            users.clear(profileId.value.toString())
            titles.clear(profileId.value.toString())
            preferences.clear(profileId)
            store.remove(profileId)
        }
    }
}
