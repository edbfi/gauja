// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.testing

import app.gauja.core.common.AppError
import app.gauja.core.model.media.TitleSummary
import app.gauja.core.model.servers.Cached

sealed interface CachedTitleState {
    data object Loading : CachedTitleState

    data object Empty : CachedTitleState

    data object Denied : CachedTitleState

    data class Failed(val error: AppError) : CachedTitleState

    data class Offline(val title: Cached<TitleSummary>) : CachedTitleState
}
