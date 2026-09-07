// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.testing

import java.io.File

object FixtureLoader {
    fun artwork(): ByteArray = root().resolve("design/assets/test/poster.png").readBytes()

    private fun root(): File =
        generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { it.resolve("api/UPSTREAM_COMMIT").isFile }

    fun read(relative: String): String {
        require(!relative.contains("..") && !relative.startsWith("/"))
        val root =
            generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
                .first { it.resolve("api/UPSTREAM_COMMIT").isFile }
        return root.resolve("api/fixtures/3.4.1/$relative").readText()
    }
}
