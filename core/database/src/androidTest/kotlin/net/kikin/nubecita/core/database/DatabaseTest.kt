package net.kikin.nubecita.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before

/**
 * Shared harness for DAO + database tests. Each concrete test subclass
 * gets a fresh in-memory [NubecitaDatabase] before every `@Test` and
 * closes it in `@After`. No disk, no clear-between-tests dance.
 *
 * Mirrors the Now in Android `DatabaseTest` pattern.
 */
internal abstract class DatabaseTest {
    protected lateinit var db: NubecitaDatabase

    @Before
    fun setupDatabase() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    NubecitaDatabase::class.java,
                ).build()
    }

    @After
    fun teardownDatabase() {
        db.close()
    }
}
