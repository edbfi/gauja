// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.data.session

import app.gauja.core.data.auth.AuthRepository
import app.gauja.core.data.auth.LiveAuthRepository
import app.gauja.core.data.servers.LiveServersRepository
import app.gauja.core.data.servers.ServersRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoriesModule {
    @Binds abstract fun auth(implementation: LiveAuthRepository): AuthRepository

    @Binds abstract fun servers(implementation: LiveServersRepository): ServersRepository
}
