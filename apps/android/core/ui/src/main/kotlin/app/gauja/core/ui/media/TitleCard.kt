// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.ui.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import app.gauja.core.designsystem.GaujaTheme
import app.gauja.core.model.media.MediaType
import app.gauja.core.model.media.TitleSummary
import app.gauja.core.model.media.TmdbId
import app.gauja.core.model.status.MediaStatus
import app.gauja.core.ui.R

@Composable
fun TitleCard(
    title: TitleSummary,
    onOpen: (MediaType, TmdbId) -> Unit,
    modifier: Modifier = Modifier,
    poster: Painter? = null,
) {
    val knownType =
        when (title.mediaType) {
            MediaType.MOVIE,
            MediaType.TV -> true
            is MediaType.Unrecognized -> false
        }
    Card(
        onClick = { onOpen(title.mediaType, title.id) },
        modifier = modifier,
        enabled = knownType,
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (poster == null)
                    Text(stringResource(R.string.artwork_missing), Modifier.padding(12.dp))
                else
                    Image(
                        poster,
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop,
                    )
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title.title?.takeIf(String::isNotBlank)
                        ?: stringResource(R.string.title_missing),
                    style = MaterialTheme.typography.titleMedium,
                )
                val type =
                    when (title.mediaType) {
                        MediaType.MOVIE -> R.string.media_movie
                        MediaType.TV -> R.string.media_tv
                        is MediaType.Unrecognized -> R.string.media_unknown
                    }
                Text(
                    listOfNotNull(stringResource(type), title.year?.toString()).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
                title.rating
                    ?.takeIf { it.isFinite() && it in 0.0..10.0 }
                    ?.let {
                        Text(
                            stringResource(R.string.rating, it),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                StatusBadge(title.status)
                title.status4k?.let { StatusBadge(it, is4k = true) }
            }
        }
    }
}

@Preview
@PreviewScreenSizes
@Composable
private fun TitlePreview() {
    GaujaTheme {
        TitleCard(
            TitleSummary(
                TmdbId(42),
                MediaType.MOVIE,
                "A journey along the river",
                2026,
                null,
                8.2,
                MediaStatus.AVAILABLE,
            ),
            { _, _ -> },
            Modifier.widthIn(max = 220.dp),
        )
    }
}
