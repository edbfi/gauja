// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.navigation

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigatorTest {
    @Test
    fun navigationMutatesOnlyTheBoundSavedStackAndKeepsItsRoot() {
        val navigator = Navigator()
        val first = mutableListOf<NavKey>(ServerRoute)
        val oldBinding = navigator.attach(first)
        navigator.navigate(ServerRoute)
        assertEquals(2, first.size)
        val restored = first.toMutableList()
        val binding = navigator.attach(restored)
        oldBinding.close()
        navigator.back()
        navigator.back()
        assertEquals(listOf(ServerRoute), restored)
        assertEquals(2, first.size)
        binding.close()
    }
}
