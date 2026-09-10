// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.gauja.core.designsystem.GaujaTheme
import app.gauja.core.model.servers.ThemeMode
import app.gauja.core.navigation.EntryProviderInstaller
import app.gauja.core.navigation.Navigator
import app.gauja.core.navigation.ServerRoute
import app.gauja.core.ui.state.ContentMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var navigator: Navigator
    @Inject lateinit var installers: Set<@JvmSuppressWildcards EntryProviderInstaller>

    private val model: RootModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            LifecycleEventEffect(Lifecycle.Event.ON_START) { model.foreground() }
            val darkTheme =
                when (state.value.theme) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                }
            GaujaTheme(darkTheme = darkTheme) {
                val error = state.error
                if (error != null) {
                    ContentMessage(error)
                    return@GaujaTheme
                }
                val backStack = rememberNavBackStack(ServerRoute)
                DisposableEffect(backStack) {
                    val binding = navigator.attach(backStack)
                    onDispose { binding.close() }
                }
                NavDisplay(
                    backStack = backStack,
                    modifier = Modifier.safeDrawingPadding(),
                    onBack = navigator::back,
                    entryDecorators =
                        listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                    entryProvider = entryProvider { installers.forEach { it() } },
                )
            }
        }
    }
}
