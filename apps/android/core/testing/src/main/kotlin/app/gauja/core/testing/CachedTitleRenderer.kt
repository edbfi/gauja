// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.testing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import app.gauja.core.common.AppError
import app.gauja.core.designsystem.GaujaTheme
import app.gauja.core.model.media.MediaType
import app.gauja.core.model.media.TitleSummary
import app.gauja.core.model.media.TmdbId
import app.gauja.core.model.servers.Cached
import app.gauja.core.ui.media.TitleCard
import app.gauja.core.ui.state.ContentMessage
import java.time.Instant

@Composable
fun CachedTitleRenderer(
    title: TitleSummary,
    modifier: Modifier = Modifier,
    poster: Painter? = null,
) {
    CachedTitleRenderer(CachedTitleState.Offline(Cached(title, Instant.EPOCH)), modifier, poster)
}

@Composable
fun CachedTitleRenderer(
    state: CachedTitleState,
    modifier: Modifier = Modifier,
    poster: Painter? = null,
    dark: Boolean = true,
    onOpen: (MediaType, TmdbId) -> Unit = { _, _ -> },
) {
    GaujaTheme(darkTheme = dark) {
        Column(modifier.widthIn(max = 220.dp)) {
            when (state) {
                CachedTitleState.Loading -> CircularProgressIndicator()
                CachedTitleState.Empty -> Text("No cached titles")
                CachedTitleState.Denied -> ContentMessage(AppError.PERMISSION)
                is CachedTitleState.Failed -> ContentMessage(state.error, onRetry = {})
                is CachedTitleState.Offline -> {
                    Text("Offline · cached ${state.title.fetchedAt}")
                    TitleCard(state.title.value, onOpen, poster = poster)
                }
            }
        }
    }
}
