// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.model.permissions

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionTest {
    private fun rows(name: String): List<List<String>> =
        File("../../../../api/test-vectors/$name.txt")
            .readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split('|') }

    @Test
    fun upstreamTableAndUnusedBits() {
        val table = rows("permission-flags").associate { it[0] to it[1].toLong() }
        assertEquals(table, Permission.entries.associate { it.name to it.mask })
        assertEquals(
            0L,
            Permission.entries.fold(0L) { mask, flag -> mask or flag.mask } and ((1L shl 29) or 1L),
        )
    }

    @Test
    fun sharedParityCases() {
        for (row in rows("permissions")) {
            val user = row[2].toLong()
            val actual =
                if (row[0] == "scalar") hasPermission(row[1].toLong(), user)
                else {
                    val required = row[1].split(',').filter(String::isNotEmpty).map(String::toLong)
                    hasPermission(
                        required,
                        user,
                        if (row[3] == "and") PermissionMode.AND else PermissionMode.OR,
                    )
                }
            assertEquals(row.joinToString("|"), row[4].toBooleanStrict(), actual)
        }
    }
}
