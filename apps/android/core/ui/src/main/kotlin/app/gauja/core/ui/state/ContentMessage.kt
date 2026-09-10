// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.ui.state

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import app.gauja.core.common.AppError
import app.gauja.core.designsystem.GaujaTheme
import app.gauja.core.ui.R

@Composable
fun ContentMessage(error: AppError, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    val message =
        when (error) {
            AppError.NETWORK -> R.string.error_network
            AppError.TLS -> R.string.error_tls
            AppError.AUTH -> R.string.error_auth
            AppError.PERMISSION -> R.string.error_permission
            AppError.NOT_FOUND -> R.string.error_notfound
            AppError.VALIDATION -> R.string.error_validation
            AppError.SERVER_VERSION -> R.string.error_serverversion
            AppError.OFFLINE -> R.string.error_offline
            AppError.UNKNOWN -> R.string.error_unknown
        }
    Column(modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(stringResource(message))
        if (onRetry != null) Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Preview
@PreviewScreenSizes
@Composable
private fun ErrorPreview() {
    GaujaTheme { ContentMessage(AppError.OFFLINE) }
}
