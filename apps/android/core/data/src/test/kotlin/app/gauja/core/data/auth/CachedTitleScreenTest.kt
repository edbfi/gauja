// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.data.auth

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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

    @Test
    fun fontScalingWrapsTheCompleteTitleWithoutOverflow() {
        val scale = mutableFloatStateOf(1f)
        val title = "A journey along the river"
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale.value)) {
                CachedTitleRenderer(
                    offline(1, title),
                    Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }
        fun layout(): TextLayoutResult {
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(title, useUnmergedTree = true).performSemanticsAction(
                SemanticsActions.GetTextLayoutResult
            ) {
                it(results)
            }
            return results.single()
        }
        val normal = layout()
        compose.runOnIdle { scale.value = 2f }
        val enlarged = layout()
        assertTrue(
            "Normal ${normal.size} at ${normal.layoutInput.density.fontScale}; enlarged ${enlarged.size} at ${enlarged.layoutInput.density.fontScale}",
            enlarged.size.height > normal.size.height,
        )
        assertFalse(enlarged.hasVisualOverflow)
        assertEquals(title.length, enlarged.getLineEnd(enlarged.lineCount - 1))
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
