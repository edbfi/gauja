// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.network

import app.gauja.core.common.AppError
import app.gauja.core.common.AppException
import app.gauja.core.common.IoDispatcher
import app.gauja.core.common.Secret
import app.gauja.core.datastore.secrets.SecretKind
import app.gauja.core.datastore.secrets.SecretStore
import app.gauja.core.model.servers.AuthMethod
import app.gauja.core.model.servers.ProfileId
import app.gauja.core.model.servers.ServerProfile
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/** A profile lock spans requests and cache writes, so deletion cannot be undone by a late reply. */
@Singleton
class ProfileTransport
@Inject
constructor(
    private val secrets: SecretStore,
    private val diagnostics: DeprecationStore,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val locks = ConcurrentHashMap<ProfileId, Mutex>()
    private val sessions = ConcurrentHashMap<ProfileId, Session>()
    private val deleted = ConcurrentHashMap.newKeySet<ProfileId>()

    suspend fun <T> withProfile(profile: ServerProfile, block: suspend (OkHttpClient) -> T): T =
        withContext(io) {
            locks
                .getOrPut(profile.id) { Mutex() }
                .withLock {
                    if (profile.id in deleted) throw AppException(AppError.NOT_FOUND)
                    val session = session(profile)
                    try {
                        block(session.client)
                    } finally {
                        // A cancelled request may have rotated the cookie before cancellation
                        // arrived.
                        withContext(NonCancellable) {
                            secrets.write(
                                profile.id,
                                SecretKind.SESSION_COOKIE,
                                session.jar.secret(),
                            )
                        }
                    }
                }
        }

    suspend fun delete(profileId: ProfileId, wipe: suspend () -> Unit) =
        withContext(io) {
            deleted.add(profileId)
            sessions[profileId]?.client?.dispatcher?.cancelAll()
            locks
                .getOrPut(profileId) { Mutex() }
                .withLock {
                    close(profileId)
                    wipe()
                    diagnostics.clear(profileId)
                }
        }

    // Called inside withProfile so cookie clearing and persistent clearing are one ordered
    // operation.
    suspend fun clearSession(profileId: ProfileId) {
        sessions[profileId]?.jar?.clear()
        secrets.write(profileId, SecretKind.SESSION_COOKIE, null)
    }

    suspend fun clearCredentials(profileId: ProfileId) {
        clearSession(profileId)
        secrets.write(profileId, SecretKind.API_KEY, null)
        secrets.write(profileId, SecretKind.PLEX_TOKEN, null)
        close(profileId)
    }

    private suspend fun session(profile: ServerProfile): Session {
        sessions[profile.id]?.let { existing ->
            if (existing.profile == profile) return existing
            close(profile.id)
        }
        val origin = profile.address.value.toHttpUrl()
        val jar = ProfileCookies(origin, clock, profile.authMethod == AuthMethod.SESSION)
        if (profile.authMethod == AuthMethod.SESSION) {
            secrets.read(profile.id, SecretKind.SESSION_COOKIE)?.useBytes { bytes ->
                Cookie.parse(origin, bytes.toString(Charsets.UTF_8))?.let { jar.restore(it) }
            }
        }
        val apiKey =
            if (profile.authMethod == AuthMethod.API_KEY)
                secrets.read(profile.id, SecretKind.API_KEY)
            else null
        val basicPassword = secrets.read(profile.id, SecretKind.BASIC_AUTH_PASSWORD)
        val builder =
            OkHttpClient.Builder()
                .cookieJar(jar)
                .cache(null)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .callTimeout(20, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request()
                    if (!sameOrigin(request.url, origin)) {
                        android.util.Log.e("Gauja", "EGRESS_REJECTED")
                        throw java.io.IOException("EGRESS_REJECTED")
                    }
                    val authenticated =
                        request
                            .newBuilder()
                            .removeHeader("X-API-User")
                            .removeHeader("X-Api-Key")
                            .removeHeader("Authorization")
                            .removeHeader("Cookie")
                    apiKey?.useBytes {
                        authenticated.header("X-Api-Key", it.toString(Charsets.UTF_8))
                    }
                    profile.basicAuthUsername?.let { username ->
                        basicPassword?.useBytes {
                            authenticated.header(
                                "Authorization",
                                Credentials.basic(username, it.toString(Charsets.UTF_8)),
                            )
                        }
                    }
                    chain.proceed(authenticated.build()).also { response ->
                        diagnostics.record(profile.id, request.url.encodedPath, response.headers)
                        if (response.code == 401) jar.clear()
                    }
                }
        configureTrust(builder, profile.tlsMode)
        return Session(profile, jar, builder.build()).also { sessions[profile.id] = it }
    }

    private fun close(id: ProfileId) {
        sessions.remove(id)?.let {
            it.jar.clear()
            it.client.dispatcher.cancelAll()
            it.client.connectionPool.evictAll()
            it.client.dispatcher.executorService.shutdown()
        }
    }

    private class Session(
        val profile: ServerProfile,
        val jar: ProfileCookies,
        val client: OkHttpClient,
    )
}

internal fun sameOrigin(target: HttpUrl, origin: HttpUrl): Boolean =
    target.scheme == origin.scheme && target.host == origin.host && target.port == origin.port

private class ProfileCookies(
    private val origin: HttpUrl,
    private val clock: Clock,
    private val enabled: Boolean,
) : CookieJar {
    private var cookie: Cookie? = null

    @Synchronized
    fun restore(value: Cookie) {
        if (
            enabled &&
                value.name == "connect.sid" &&
                (value.domain == origin.host ||
                    (!value.hostOnly && origin.host.endsWith("." + value.domain)))
        )
            cookie = value
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (enabled && sameOrigin(url, origin))
            cookies.lastOrNull { it.name == "connect.sid" }?.let { cookie = it }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        listOfNotNull(
            cookie?.takeIf {
                enabled &&
                    sameOrigin(url, origin) &&
                    it.matches(url) &&
                    it.expiresAt > clock.millis()
            }
        )

    @Synchronized
    fun secret(): Secret? =
        cookie
            ?.takeIf { enabled && it.expiresAt > clock.millis() }
            ?.let { Secret(it.toString().toByteArray()) }

    @Synchronized
    fun clear() {
        cookie = null
    }
}
