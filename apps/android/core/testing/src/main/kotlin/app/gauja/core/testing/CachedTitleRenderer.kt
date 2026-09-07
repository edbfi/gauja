// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.testing

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import app.gauja.core.designsystem.GaujaTheme
import app.gauja.core.model.media.TitleSummary
import app.gauja.core.ui.media.TitleCard

@Composable
fun CachedTitleRenderer(
    title: TitleSummary,
    modifier: Modifier = Modifier,
    poster: Painter? = null,
) {
    GaujaTheme { TitleCard(title, { _, _ -> }, modifier.widthIn(max = 220.dp), poster = poster) }
}
