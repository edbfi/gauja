// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.feature.servers.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gauja.core.common.ProbeError
import app.gauja.core.common.ProbeException
import app.gauja.core.data.servers.ServerProbe
import app.gauja.core.model.ServerAddress
import app.gauja.core.model.ServerSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal data class ServerCheckState(
    val address: String = "",
    val checking: Boolean = false,
    val snapshot: ServerSnapshot? = null,
    val error: ProbeError? = null,
)

@HiltViewModel
internal class ServerCheckViewModel
@Inject
constructor(private val savedState: SavedStateHandle, private val probe: ServerProbe) :
    ViewModel() {
    private val mutable = MutableStateFlow(ServerCheckState(address = savedState["address"] ?: ""))
    val state =
        mutable.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), mutable.value)
    private var attempt: Job? = null
    private var generation = 0

    fun edit(address: String) {
        cancel()
        // Only validated addresses enter Android saved state; userinfo/query may contain secrets.
        savedState["address"] = ServerAddress.parse(address)?.value ?: ""
        mutable.value = ServerCheckState(address)
    }

    fun check() {
        if (mutable.value.checking) return
        val address = ServerAddress.parse(mutable.value.address)
        if (address == null) {
            mutable.value = mutable.value.copy(error = ProbeError.ADDRESS)
            return
        }
        val identity = ++generation
        mutable.value = mutable.value.copy(checking = true, error = null)
        attempt = viewModelScope.launch {
            try {
                val snapshot = probe.check(address)
                if (generation == identity)
                    mutable.value = mutable.value.copy(checking = false, snapshot = snapshot)
            } catch (error: CancellationException) {
                throw error
            } catch (error: ProbeException) {
                if (generation == identity)
                    mutable.value = mutable.value.copy(checking = false, error = error.reason)
            }
        }
    }

    fun foreground() {
        if (mutable.value.snapshot != null) check()
    }

    fun cancel() {
        generation++
        attempt?.cancel()
        attempt = null
        mutable.value = mutable.value.copy(checking = false)
    }
}
