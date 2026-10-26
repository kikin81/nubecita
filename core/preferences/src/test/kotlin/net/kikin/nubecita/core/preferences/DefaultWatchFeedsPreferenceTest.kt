package net.kikin.nubecita.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

internal class DefaultWatchFeedsPreferenceTest {
    @TempDir
    var tempDir: File = File("")

    @Test
    fun `defaults to Following on a fresh store`() =
        runTest {
            val pref = DefaultWatchFeedsPreference(newDataStore(this))

            assertEquals(setOf(WatchFeedsPreference.FOLLOWING_FEED_URI), pref.selectedFeedUris.first())
        }

    @Test
    fun `a selection round-trips`() =
        runTest {
            val pref = DefaultWatchFeedsPreference(newDataStore(this))
            val selection = setOf("following", "at://did:plc:x/app.bsky.feed.generator/cats")

            pref.setSelectedFeedUris(selection)

            assertEquals(selection, pref.selectedFeedUris.first())
        }

    @Test
    fun `more than the maximum is capped`() =
        runTest {
            val pref = DefaultWatchFeedsPreference(newDataStore(this))

            pref.setSelectedFeedUris(setOf("a", "b", "c", "d"))

            assertEquals(WatchFeedsPreference.MAX_FEEDS, pref.selectedFeedUris.first().size)
        }

    private fun newDataStore(scope: TestScope): DataStore<Preferences> {
        val file = tempDir.resolve("watch_feeds_${System.nanoTime()}.preferences_pb")
        return PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { file },
        )
    }
}
