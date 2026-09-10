// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gauja.core.common.AppError
import app.gauja.core.common.AppException
import app.gauja.core.data.servers.ServersRepository
import app.gauja.core.datastore.preferences.PreferencesStore
import app.gauja.core.model.servers.Preferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RootPreferences(val value: Preferences = Preferences(), val error: AppError? = null)

@HiltViewModel
class RootModel
@Inject
constructor(private val preferences: PreferencesStore, private val servers: ServersRepository) :
    ViewModel() {
    val state =
        preferences.preferences
            .map { RootPreferences(it) }
            .catch { emit(RootPreferences(error = AppError.UNKNOWN)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RootPreferences())
    private val refreshError = MutableStateFlow<AppError?>(null)
    val foregroundError =
        refreshError.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun foreground() {
        viewModelScope.launch {
            try {
                preferences.preferences.first().activeProfileId?.let { servers.refresh(it) }
                refreshError.value = null
            } catch (error: CancellationException) {
                throw error
            } catch (error: AppException) {
                refreshError.value = error.reason
            } catch (_: Exception) {
                refreshError.value = AppError.UNKNOWN
            }
        }
    }
}
