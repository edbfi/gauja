// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.data.servers

import app.gauja.core.common.DefaultDispatcher
import app.gauja.core.common.IoDispatcher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
abstract class ProbeModule {
    @Binds internal abstract fun bindProbe(implementation: LiveServerProbe): ServerProbe
}

@Module
@InstallIn(SingletonComponent::class)
internal object ProbeDependencies {
    @Provides fun json(): Json = probeJson()

    @Provides fun clock(): Clock = Clock.systemUTC()

    @Provides @DefaultDispatcher fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides @IoDispatcher fun io(): CoroutineDispatcher = Dispatchers.IO
}
