// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.database.media

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cached_title", primaryKeys = ["profileId", "mediaType", "tmdbId"])
data class TitleEntity(
    val profileId: String,
    val mediaType: String,
    val tmdbId: Int,
    val title: String?,
    val year: Int?,
    val posterPath: String?,
    val rating: Double?,
    val status: Int?,
    val status4k: Int?,
    val fetchedAt: Long,
)

@Dao
interface TitleDao {
    @Query(
        "SELECT * FROM cached_title WHERE profileId = :profileId AND mediaType = :mediaType AND tmdbId = :tmdbId"
    )
    fun observe(profileId: String, mediaType: String, tmdbId: Int): Flow<TitleEntity?>

    @Upsert suspend fun upsert(title: TitleEntity)

    @Query("DELETE FROM cached_title WHERE profileId = :profileId")
    suspend fun clear(profileId: String)
}
