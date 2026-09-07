// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja

import app.cash.turbine.test
import app.gauja.core.common.AppError
import app.gauja.core.common.AppException
import app.gauja.core.data.servers.ServersRepository
import app.gauja.core.datastore.preferences.PreferencesStore
import app.gauja.core.model.servers.Preferences
import app.gauja.core.model.servers.ProfileId
import app.gauja.core.model.servers.ThemeMode
import app.gauja.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RootModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun storedThemeUpdatesAndForegroundRefreshesOnlyTheActiveProfile() = runTest {
        val id = ProfileId(UUID.randomUUID())
        val values = MutableStateFlow(Preferences(theme = ThemeMode.LIGHT, activeProfileId = id))
        val preferences = mockk<PreferencesStore>()
        every { preferences.preferences } returns values
        val servers = mockk<ServersRepository>()
        coEvery { servers.refresh(id) } returns Unit
        val model = RootModel(preferences, servers)
        model.state.test {
            assertEquals(ThemeMode.DARK, awaitItem().value.theme)
            assertEquals(ThemeMode.LIGHT, awaitItem().value.theme)
            values.value = values.value.copy(theme = ThemeMode.SYSTEM)
            assertEquals(ThemeMode.SYSTEM, awaitItem().value.theme)
        }
        model.foreground()
        advanceUntilIdle()
        coVerify(exactly = 1) { servers.refresh(id) }
        values.value = values.value.copy(activeProfileId = null)
        model.foreground()
        advanceUntilIdle()
        coVerify(exactly = 1) { servers.refresh(any()) }
    }

    @Test
    fun failedForegroundRefreshExposesSafeErrorWithoutDiscardingPreferences() = runTest {
        val id = ProfileId(UUID.randomUUID())
        val values = MutableStateFlow(Preferences(activeProfileId = id))
        val preferences = mockk<PreferencesStore>()
        every { preferences.preferences } returns values
        val servers = mockk<ServersRepository>()
        coEvery { servers.refresh(id) } throws AppException(AppError.OFFLINE)
        val model = RootModel(preferences, servers)
        model.foregroundError.test {
            assertEquals(null, awaitItem())
            model.foreground()
            assertEquals(AppError.OFFLINE, awaitItem())
        }
        assertEquals(id, values.value.activeProfileId)
    }
}
