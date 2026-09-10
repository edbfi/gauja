// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.gauja.core.data.servers.ProbeModule
import app.gauja.core.data.servers.ServerProbe
import app.gauja.core.model.Compatibility
import app.gauja.core.model.ServerAddress
import app.gauja.core.model.ServerSnapshot
import app.gauja.core.model.status.MediaServerType
import dagger.Binds
import dagger.Module
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Inject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ServerCheckTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun injectedProbeRendersDomainResult() {
        compose.onNodeWithText("Server address").performTextInput("https://example.invalid")
        compose.onNodeWithText("Check server").performClick()
        compose.onNodeWithText("Test library").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("https://example.invalid").assertIsDisplayed()
    }
}

class TestProbe @Inject constructor() : ServerProbe {
    override suspend fun check(address: ServerAddress) =
        ServerSnapshot(
            address,
            "3.4.1",
            "Test library",
            true,
            false,
            true,
            false,
            MediaServerType.PLEX,
            Compatibility.TESTED,
        )
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ProbeModule::class])
abstract class TestProbeModule {
    @Binds abstract fun probe(implementation: TestProbe): ServerProbe
}
