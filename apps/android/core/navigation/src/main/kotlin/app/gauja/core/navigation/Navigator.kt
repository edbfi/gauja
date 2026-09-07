// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.navigation

import androidx.annotation.MainThread
import androidx.navigation3.runtime.NavKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Navigator @Inject constructor() {
    private var stack: MutableList<NavKey>? = null

    @MainThread
    fun attach(backStack: MutableList<NavKey>): AutoCloseable {
        stack = backStack
        return AutoCloseable { if (stack === backStack) stack = null }
    }

    @MainThread
    fun navigate(key: NavKey) {
        checkNotNull(stack).add(key)
    }

    @MainThread
    fun back() {
        stack?.let { if (it.size > 1) it.removeLastOrNull() }
    }
}
