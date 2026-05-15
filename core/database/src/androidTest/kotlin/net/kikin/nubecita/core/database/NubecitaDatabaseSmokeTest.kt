package net.kikin.nubecita.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Sanity check that the Room build pipeline produced a usable database
 * class and that the convention plugin's runtime deps are on the
 * classpath. No DAOs are exercised — those tests will land alongside
 * the first real entity.
 */
@RunWith(AndroidJUnit4::class)
internal class NubecitaDatabaseSmokeTest : DatabaseTest() {
    @Test
    fun database_opens_and_closes() {
        // openHelper is lazily initialized; reading writableDatabase
        // forces the underlying SQLite connection open so a failure to
        // generate the Room implementation class would surface here.
        val sqlite = db.openHelper.writableDatabase
        assertNotNull(sqlite)
        assertTrue(sqlite.isOpen)
    }
}
