// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.data.auth

import app.gauja.core.api.apis.AuthApi
import app.gauja.core.api.models.PostAuthLocalRequest
import app.gauja.core.common.AppError
import app.gauja.core.common.AppException
import app.gauja.core.common.Secret
import app.gauja.core.data.session.ApiSession
import app.gauja.core.data.session.checked
import app.gauja.core.database.users.UserDao
import app.gauja.core.model.servers.AuthMethod
import app.gauja.core.model.servers.Cached
import app.gauja.core.model.servers.ProfileId
import app.gauja.core.model.users.User
import app.gauja.core.network.ProfileTransport
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface AuthRepository {
    fun observeUser(profileId: ProfileId): Flow<Cached<User>?>

    suspend fun signInLocal(profileId: ProfileId, email: String, password: Secret)

    suspend fun refresh(profileId: ProfileId)

    suspend fun logout(profileId: ProfileId)
}

internal class LiveAuthRepository
@Inject
constructor(
    private val sessions: ApiSession,
    private val transport: ProfileTransport,
    private val users: UserDao,
    private val clock: Clock,
) : AuthRepository {
    override fun observeUser(profileId: ProfileId): Flow<Cached<User>?> =
        users.observe(profileId.value.toString()).map { it?.domain() }

    override suspend fun signInLocal(profileId: ProfileId, email: String, password: Secret) {
        sessions.use(profileId) { profile, retrofit ->
            if (profile.authMethod != AuthMethod.SESSION) throw AppException(AppError.VALIDATION)
            val api = retrofit.create(AuthApi::class.java)
            var completed = false
            try {
                val input =
                    password.useBytes { PostAuthLocalRequest(email, it.toString(Charsets.UTF_8)) }
                api.postAuthLocal(input).checked()
                users.upsert(
                    Cached(api.getAuthMe().checked().domain(), clock.instant()).entity(profileId)
                )
                completed = true
            } finally {
                if (!completed)
                    withContext(NonCancellable) {
                        transport.clearSession(profile)
                        users.clear(profileId.value.toString())
                    }
            }
        }
    }

    override suspend fun refresh(profileId: ProfileId) {
        sessions.use(profileId) { _, retrofit ->
            try {
                val user = retrofit.create(AuthApi::class.java).getAuthMe().checked().domain()
                users.upsert(Cached(user, clock.instant()).entity(profileId))
            } catch (error: AppException) {
                if (error.reason == AppError.AUTH) users.clear(profileId.value.toString())
                throw error
            }
        }
    }

    override suspend fun logout(profileId: ProfileId) {
        sessions.use(profileId) { profile, retrofit ->
            try {
                retrofit.create(AuthApi::class.java).postAuthLogout().checked()
            } finally {
                withContext(NonCancellable) {
                    transport.clearCredentials(profile)
                    users.clear(profileId.value.toString())
                }
            }
        }
    }
}
