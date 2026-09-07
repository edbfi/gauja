// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.data.auth

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.gauja.core.common.AppError
import app.gauja.core.model.media.MediaType
import app.gauja.core.model.media.TitleSummary
import app.gauja.core.model.media.TmdbId
import app.gauja.core.model.servers.Cached
import app.gauja.core.model.status.MediaStatus
import app.gauja.core.testing.CachedTitleRenderer
import app.gauja.core.testing.CachedTitleState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class CachedTitleScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun cacheStatesAreVisibleAndRecycledCardOpensCurrentIdentity() {
        val state = mutableStateOf<CachedTitleState>(CachedTitleState.Loading)
        val dark = mutableStateOf(true)
        var opened: TmdbId? = null
        compose.setContent {
            CachedTitleRenderer(state.value, dark = dark.value, onOpen = { _, id -> opened = id })
        }
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
        compose.runOnIdle { state.value = CachedTitleState.Empty }
        compose.onNodeWithText("No cached titles").assertIsDisplayed()
        compose.runOnIdle { state.value = CachedTitleState.Failed(AppError.NETWORK) }
        compose.onNodeWithText("Retry").assertIsDisplayed()
        compose.runOnIdle { state.value = CachedTitleState.Denied }
        compose
            .onNodeWithText("You do not have permission to view this content.")
            .assertIsDisplayed()
        compose.runOnIdle { state.value = offline(1, "First") }
        compose.onNodeWithText("First").performClick()
        assertEquals(TmdbId(1), opened)
        compose.runOnIdle {
            state.value = offline(2, "Second")
            dark.value = false
        }
        compose.onNodeWithText("Second").performClick()
        assertEquals(TmdbId(2), opened)
        compose.onNodeWithText("Unknown status").assertIsDisplayed()
    }

    private fun offline(id: Int, title: String) =
        CachedTitleState.Offline(
            Cached(
                TitleSummary(
                    TmdbId(id),
                    MediaType.MOVIE,
                    title,
                    null,
                    null,
                    null,
                    MediaStatus.Unrecognized(999),
                ),
                Instant.EPOCH,
            )
        )
}
