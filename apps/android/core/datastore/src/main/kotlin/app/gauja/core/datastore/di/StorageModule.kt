// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.gauja.core.common.IoDispatcher
import app.gauja.core.datastore.crypto.EncryptedSerializer
import app.gauja.core.datastore.crypto.KeystoreCipher
import app.gauja.core.datastore.crypto.KeystoreKeys
import app.gauja.core.datastore.crypto.StorageCipher
import app.gauja.core.datastore.crypto.StorageKeys
import app.gauja.core.datastore.profiles.ProfileDocument
import app.gauja.core.datastore.profiles.ProtoServerProfileStore
import app.gauja.core.datastore.profiles.ServerProfileStore
import app.gauja.core.datastore.secrets.EncryptedSecretStore
import app.gauja.core.datastore.secrets.SecretDocument
import app.gauja.core.datastore.secrets.SecretStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@Singleton
internal class StorageScope @Inject constructor(@IoDispatcher io: CoroutineDispatcher) :
    CoroutineScope {
    override val coroutineContext: CoroutineContext = SupervisorJob() + io
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class StorageBindings {
    @Binds abstract fun keys(implementation: KeystoreKeys): StorageKeys

    @Binds abstract fun cipher(implementation: KeystoreCipher): StorageCipher

    @Binds abstract fun profiles(implementation: ProtoServerProfileStore): ServerProfileStore

    @Binds abstract fun secrets(implementation: EncryptedSecretStore): SecretStore
}

@Module
@InstallIn(SingletonComponent::class)
internal object StorageModule {
    @Provides
    @Singleton
    fun profiles(
        @ApplicationContext context: Context,
        cipher: StorageCipher,
        scope: StorageScope,
    ): DataStore<ProfileDocument> =
        DataStoreFactory.create(
            serializer =
                EncryptedSerializer(
                    ProfileDocument(),
                    ProfileDocument.serializer(),
                    cipher,
                    "gauja.profiles.v1",
                ),
            scope = scope,
            produceFile = { File(context.noBackupFilesDir, "datastore/profiles.pb") },
        )

    @Provides
    @Singleton
    fun secrets(
        @ApplicationContext context: Context,
        cipher: StorageCipher,
        scope: StorageScope,
    ): DataStore<SecretDocument> =
        DataStoreFactory.create(
            serializer =
                EncryptedSerializer(
                    SecretDocument(),
                    SecretDocument.serializer(),
                    cipher,
                    "gauja.secrets.v1",
                ),
            scope = scope,
            produceFile = { File(context.noBackupFilesDir, "datastore/secrets.pb") },
        )

    @Provides
    @Singleton
    fun preferences(
        @ApplicationContext context: Context,
        scope: StorageScope,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = {
                File(context.noBackupFilesDir, "datastore/preferences.preferences_pb")
            },
        )
}
