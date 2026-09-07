// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.database

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import app.gauja.core.database.users.UserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class CacheDatabaseTest {
    @Test
    fun reopeningPreservesTimestampsAndProfileWipesAreIsolated() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val name = "cache-reopen.db"
        context.deleteDatabase(name)
        fun open(): CacheDatabase =
            Room.databaseBuilder(context, CacheDatabase::class.java, name)
                .setDriver(AndroidSQLiteDriver())
                .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
                .build()
        val first = open()
        first.users().upsert(UserEntity("a", 1, "A", "a@example.invalid", 2, 1234))
        first.users().upsert(UserEntity("b", 1, "B", "b@example.invalid", 32, 5678))
        first.close()
        val reopened = open()
        try {
            assertEquals(1234L, reopened.users().observe("a").first()?.fetchedAt)
            reopened.users().clear("a")
            assertNull(reopened.users().observe("a").first())
            assertEquals("B", reopened.users().observe("b").first()?.displayName)
        } finally {
            reopened.close()
            context.deleteDatabase(name)
        }
    }
}
