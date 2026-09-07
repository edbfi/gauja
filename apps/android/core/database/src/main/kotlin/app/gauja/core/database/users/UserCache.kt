// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.database.users

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cached_user")
data class UserEntity(
    @PrimaryKey val profileId: String,
    val userId: Int,
    val displayName: String,
    val email: String,
    val permissions: Long,
    val fetchedAt: Long,
)

@Dao
interface UserDao {
    @Query("SELECT * FROM cached_user WHERE profileId = :profileId")
    fun observe(profileId: String): Flow<UserEntity?>

    @Upsert suspend fun upsert(user: UserEntity)

    @Query("DELETE FROM cached_user WHERE profileId = :profileId")
    suspend fun clear(profileId: String)
}
