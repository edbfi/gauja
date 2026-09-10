// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.ui.media

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import app.gauja.core.designsystem.GaujaTheme
import app.gauja.core.designsystem.LocalGaujaDarkTheme
import app.gauja.core.designsystem.generated.GaujaDarkColors
import app.gauja.core.designsystem.generated.GaujaLightColors
import app.gauja.core.model.status.MediaRequestStatus
import app.gauja.core.model.status.MediaStatus
import app.gauja.core.ui.R

@Composable
fun StatusBadge(status: MediaStatus, modifier: Modifier = Modifier, is4k: Boolean = false) {
    val label =
        when (status) {
            MediaStatus.PENDING -> R.string.status_pending
            MediaStatus.PROCESSING -> R.string.status_processing
            MediaStatus.PARTIALLY_AVAILABLE -> R.string.status_partial
            MediaStatus.AVAILABLE -> R.string.status_available
            MediaStatus.BLOCKLISTED -> R.string.status_blocklisted
            MediaStatus.DELETED -> R.string.status_deleted
            MediaStatus.UNKNOWN,
            is MediaStatus.Unrecognized -> R.string.status_unknown
        }
    BadgeLabel(label, family(status), modifier, is4k)
}

@Composable
fun StatusBadge(status: MediaRequestStatus, modifier: Modifier = Modifier, is4k: Boolean = false) {
    val (label, family) =
        when (status) {
            MediaRequestStatus.PENDING -> R.string.status_pending to StatusFamily.WARNING
            MediaRequestStatus.APPROVED -> R.string.status_approved to StatusFamily.PROCESSING
            MediaRequestStatus.DECLINED -> R.string.status_declined to StatusFamily.DANGER
            MediaRequestStatus.FAILED -> R.string.status_failed to StatusFamily.DANGER
            MediaRequestStatus.COMPLETED -> R.string.status_completed to StatusFamily.AVAILABLE
            is MediaRequestStatus.Unrecognized -> R.string.status_unknown to StatusFamily.NEUTRAL
        }
    BadgeLabel(label, family, modifier, is4k)
}

private enum class StatusFamily {
    WARNING,
    PROCESSING,
    AVAILABLE,
    DANGER,
    NEUTRAL,
}

private fun family(status: MediaStatus) =
    when (status) {
        MediaStatus.PENDING -> StatusFamily.WARNING
        MediaStatus.PROCESSING -> StatusFamily.PROCESSING
        MediaStatus.PARTIALLY_AVAILABLE,
        MediaStatus.AVAILABLE -> StatusFamily.AVAILABLE
        MediaStatus.BLOCKLISTED,
        MediaStatus.DELETED -> StatusFamily.DANGER
        MediaStatus.UNKNOWN,
        is MediaStatus.Unrecognized -> StatusFamily.NEUTRAL
    }

@Composable
private fun BadgeLabel(
    label: Int,
    family: StatusFamily,
    modifier: Modifier = Modifier,
    is4k: Boolean = false,
) {
    val dark = LocalGaujaDarkTheme.current
    val (background, foreground) =
        when (family) {
            StatusFamily.WARNING ->
                if (dark) GaujaDarkColors.pendingBackground to GaujaDarkColors.pendingForeground
                else GaujaLightColors.pendingBackground to GaujaLightColors.pendingForeground
            StatusFamily.PROCESSING ->
                if (dark)
                    GaujaDarkColors.processingBackground to GaujaDarkColors.processingForeground
                else GaujaLightColors.processingBackground to GaujaLightColors.processingForeground
            StatusFamily.AVAILABLE ->
                if (dark) GaujaDarkColors.availableBackground to GaujaDarkColors.availableForeground
                else GaujaLightColors.availableBackground to GaujaLightColors.availableForeground
            StatusFamily.DANGER ->
                if (dark) GaujaDarkColors.failedBackground to GaujaDarkColors.failedForeground
                else GaujaLightColors.failedBackground to GaujaLightColors.failedForeground
            StatusFamily.NEUTRAL ->
                if (dark) GaujaDarkColors.unknownBackground to GaujaDarkColors.unknownForeground
                else GaujaLightColors.unknownBackground to GaujaLightColors.unknownForeground
        }
    val text =
        if (is4k) stringResource(R.string.status_4k, stringResource(label))
        else stringResource(label)
    Surface(
        modifier = modifier,
        color = background,
        contentColor = foreground,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Preview
@PreviewScreenSizes
@Composable
private fun BadgePreview() {
    GaujaTheme { StatusBadge(MediaStatus.Unrecognized(999), is4k = true) }
}
