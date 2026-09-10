// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.database

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver
import app.gauja.core.common.IoDispatcher
import app.gauja.core.database.media.TitleDao
import app.gauja.core.database.media.TitleEntity
import app.gauja.core.database.users.UserDao
import app.gauja.core.database.users.UserEntity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

@Database(entities = [UserEntity::class, TitleEntity::class], version = 1, exportSchema = true)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun users(): UserDao

    abstract fun titles(): TitleDao
}

@Module
@InstallIn(SingletonComponent::class)
object CacheModule {
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
        @IoDispatcher io: CoroutineDispatcher,
    ): CacheDatabase =
        Room.databaseBuilder(context, CacheDatabase::class.java, "gauja-cache.db")
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(io)
            .build()

    @Provides fun users(database: CacheDatabase): UserDao = database.users()

    @Provides fun titles(database: CacheDatabase): TitleDao = database.titles()
}
