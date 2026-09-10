// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.network

import android.content.Context
import app.gauja.core.common.AppError
import app.gauja.core.common.AppException
import app.gauja.core.model.images.PosterSize
import app.gauja.core.model.images.imageUrl
import app.gauja.core.model.servers.ProfileId
import app.gauja.core.model.servers.ServerProfile
import coil3.Image
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Call
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

@Singleton
class ProfileImages @Inject constructor(@ApplicationContext private val context: Context) :
    AutoCloseable {
    private val memory by lazy { MemoryCache.Builder().maxSizeBytes(64L * 1024 * 1024).build() }
    private val disk by lazy {
        DiskCache.Builder()
            .directory(context.cacheDir.resolve("artwork").toOkioPath())
            .maxSizeBytes(256L * 1024 * 1024)
            .build()
    }
    private val loaders = mutableMapOf<ProfileId, Loader>()
    // Coil shuts down a loader by clearing its memory cache. This cache belongs to
    // the factory, so closing one profile must leave the other profiles intact.
    private val loaderMemory by lazy {
        object : MemoryCache by memory {
            override fun clear() = Unit
        }
    }

    // The caller holds ProfileTransport's profile lock through loading and cache writes.
    suspend fun load(
        profile: ServerProfile,
        source: String,
        size: PosterSize,
        offline: Boolean,
    ): Image {
        val url =
            imageUrl(
                source,
                profile.address,
                profile.publicSettings?.value?.cacheImages == true,
                size,
            ) ?: throw AppException(AppError.VALIDATION)
        val key =
            "${profile.id.value}:" +
                MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") {
                    "%02x".format(it)
                }
        if (!offline) recordKey(profile.id, key)
        val request =
            ImageRequest.Builder(context)
                .data(url)
                .size(size.width, size.width * 3 / 2)
                .allowHardware(false)
                .memoryCacheKey(key)
                .diskCacheKey(key)
                .networkCachePolicy(if (offline) CachePolicy.DISABLED else CachePolicy.ENABLED)
                .diskCachePolicy(if (offline) CachePolicy.READ_ONLY else CachePolicy.ENABLED)
                .build()
        val result = loader(profile).execute(request)
        return (result as? SuccessResult)?.image
            ?: throw AppException(if (offline) AppError.OFFLINE else AppError.NETWORK)
    }

    @Synchronized
    override fun close() {
        loaders.values.forEach(::closeLoader)
        loaders.clear()
        memory.clear()
        disk.shutdown()
    }

    @Synchronized
    fun clear(id: ProfileId) {
        memory.keys.filter { it.key.startsWith("${id.value}:") }.forEach(memory::remove)
        val index = index(id)
        if (index.isFile) index.readLines().forEach(disk::remove)
        index.delete()
        loaders.remove(id)?.let(::closeLoader)
    }

    @Synchronized
    private fun recordKey(id: ProfileId, key: String) {
        val index = index(id)
        index.parentFile?.mkdirs()
        val keys = if (index.exists()) index.readLines().toSet() else emptySet()
        if (key !in keys) index.appendText(key + "\n")
    }

    private fun index(id: ProfileId) = context.cacheDir.resolve("artwork-keys/${id.value}.txt")

    @Synchronized
    private fun loader(profile: ServerProfile): ImageLoader {
        loaders[profile.id]
            ?.takeIf { it.profile == profile }
            ?.let {
                return it.loader
            }
        loaders.remove(profile.id)?.let(::closeLoader)
        val origin = profile.address.value.toHttpUrl()
        val server = imageClient(profile, true)
        val external = imageClient(profile, false)
        val routing = Call.Factory { request ->
            if (sameOrigin(request.url, origin)) server.newCall(request)
            else external.newCall(request)
        }
        val imageLoader =
            ImageLoader.Builder(context)
                .memoryCache(loaderMemory)
                .diskCache(disk)
                .components { add(OkHttpNetworkFetcherFactory(callFactory = { routing })) }
                .build()
        return imageLoader.also {
            loaders[profile.id] = Loader(profile, it, listOf(server, external))
        }
    }

    private fun imageClient(profile: ServerProfile, server: Boolean): OkHttpClient {
        val origin = profile.address.value.toHttpUrl()
        val builder =
            OkHttpClient.Builder()
                .cookieJar(CookieJar.NO_COOKIES)
                .followRedirects(false)
                .followSslRedirects(false)
                .addInterceptor { chain ->
                    val url = chain.request().url
                    val approved =
                        if (server) sameOrigin(url, origin)
                        else
                            url.scheme == "https" &&
                                url.port == 443 &&
                                url.host in setOf("image.tmdb.org", "artworks.thetvdb.com")
                    if (!approved) {
                        android.util.Log.e("Gauja", "EGRESS_REJECTED")
                        throw java.io.IOException("EGRESS_REJECTED")
                    }
                    chain.proceed(
                        chain
                            .request()
                            .newBuilder()
                            .removeHeader("Cookie")
                            .removeHeader("Authorization")
                            .removeHeader("X-Api-Key")
                            .removeHeader("X-API-User")
                            .build()
                    )
                }
        if (server) configureTrust(builder, profile.tlsMode)
        return builder.build()
    }

    private fun closeLoader(item: Loader) {
        item.loader.shutdown()
        item.clients.forEach {
            it.dispatcher.cancelAll()
            it.connectionPool.evictAll()
            it.dispatcher.executorService.shutdown()
        }
    }

    private class Loader(
        val profile: ServerProfile,
        val loader: ImageLoader,
        val clients: List<OkHttpClient>,
    )
}
