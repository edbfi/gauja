// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.data.media

import app.gauja.core.common.AppError
import app.gauja.core.common.AppException
import app.gauja.core.data.session.safeApi
import app.gauja.core.datastore.profiles.ServerProfileStore
import app.gauja.core.model.images.PosterSize
import app.gauja.core.model.servers.ProfileId
import app.gauja.core.network.ProfileImages
import app.gauja.core.network.ProfileTransport
import coil3.Image
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class ImagesRepository
@Inject
constructor(
    private val profiles: ServerProfileStore,
    private val transport: ProfileTransport,
    private val images: ProfileImages,
) {
    suspend fun load(
        profileId: ProfileId,
        source: String,
        size: PosterSize,
        offline: Boolean,
    ): Image = safeApi {
        val profile =
            profiles.profiles.first().firstOrNull { it.id == profileId }
                ?: throw AppException(AppError.NOT_FOUND)
        transport.withCache(profileId) { images.load(profile, source, size, offline) }
    }
}
