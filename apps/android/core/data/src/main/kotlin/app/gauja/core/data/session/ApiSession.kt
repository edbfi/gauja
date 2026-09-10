// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.data.session

import app.gauja.core.common.AppError
import app.gauja.core.common.AppException
import app.gauja.core.datastore.profiles.ServerProfileStore
import app.gauja.core.model.servers.ProfileId
import app.gauja.core.model.servers.ServerProfile
import app.gauja.core.network.ProfileTransport
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

internal class ApiSession
@Inject
constructor(
    private val profiles: ServerProfileStore,
    private val transport: ProfileTransport,
    private val json: Json,
) {
    suspend fun <T> use(id: ProfileId, operation: suspend (ServerProfile, Retrofit) -> T): T =
        safeApi {
            transport.withProfile(
                id,
                {
                    profiles.profiles.first().firstOrNull { it.id == id }
                        ?: throw AppException(AppError.NOT_FOUND)
                },
            ) { profile, client ->
                operation(
                    profile,
                    Retrofit.Builder()
                        .baseUrl(profile.address.apiBase)
                        .client(client)
                        .addConverterFactory(
                            json.asConverterFactory("application/json".toMediaType())
                        )
                        .build(),
                )
            }
        }
}

internal suspend fun <T> safeApi(operation: suspend () -> T): T =
    try {
        operation()
    } catch (error: CancellationException) {
        throw error
    } catch (error: AppException) {
        throw error
    } catch (error: java.io.IOException) {
        throw AppException(networkError(error))
    } catch (_: SerializationException) {
        throw AppException(AppError.VALIDATION)
    } catch (_: IllegalArgumentException) {
        throw AppException(AppError.VALIDATION)
    } catch (_: Exception) {
        throw AppException(AppError.UNKNOWN)
    }

internal fun <T> Response<T>.checked(): T {
    if (isSuccessful) return body() ?: throw AppException(AppError.VALIDATION)
    errorBody()?.close()
    throw AppException(
        when (code()) {
            401 -> AppError.AUTH
            403 -> AppError.PERMISSION
            404 -> AppError.NOT_FOUND
            in 400..499 -> AppError.VALIDATION
            else -> AppError.NETWORK
        }
    )
}

// A failed alternate address can wrap the original TLS rejection (OkHttp fast fallback).
private fun networkError(error: Throwable): AppError =
    when {
        error is javax.net.ssl.SSLException ||
            error.cause?.let { networkError(it) == AppError.TLS } == true ||
            error.suppressed.any { networkError(it) == AppError.TLS } -> AppError.TLS
        error is java.net.UnknownHostException || error is java.net.ConnectException ->
            AppError.OFFLINE
        else -> AppError.NETWORK
    }
