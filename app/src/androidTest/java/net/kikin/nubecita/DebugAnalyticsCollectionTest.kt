package net.kikin.nubecita

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins `firebase_analytics_collection_enabled=false` into the merged **debug**
 * manifest.
 *
 * CI instrumentation runs `:app:connectedProductionDebugAndroidTest` — the
 * production flavor, which binds real `FirebaseAnalytics` — and
 * `applicationIdSuffix` is deliberately unset (see the note in
 * `app/build.gradle.kts`), so those emulator runs report as
 * `net.kikin.nubecita` and land in the same GA4 property as real installs.
 * Measured 2026-09-13, that inflated `first_open` / `newUsers` / `activeUsers`
 * by roughly 30% — every run is a wiped AVD with a fresh app-instance id, so it
 * counts as a brand-new user (`nubecita-qyxg`).
 *
 * `FirebaseInitTest.firebaseAnalytics_isAvailable` does NOT cover this: the flag
 * gates *collection*, not instantiation, so `FirebaseAnalytics.getInstance`
 * returns a non-null singleton whether collection is on or off and that test
 * passes in both states. Only the merged manifest carries the value, so a unit
 * test can't reach it either — hence an instrumentation assertion.
 *
 * This test lives in `androidTest`, which only ever runs against a debug build
 * type, so it asserts unconditionally. Release builds deliberately omit the key
 * and are never exercised here.
 */
@RunWith(AndroidJUnit4::class)
class DebugAnalyticsCollectionTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /**
     * Mirrors [NotificationSmallIconTest.applicationMetaData]. `metaData` is a
     * platform type and is genuinely `null` when the manifest declares no
     * `<meta-data>` at all; coercing that to an empty bundle would misreport it
     * as "the analytics key is missing" and hide the larger breakage.
     */
    private fun applicationMetaData(): Bundle =
        requireNotNull(
            context.packageManager
                .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                .metaData,
        ) {
            "The merged manifest declares no <meta-data> at all — not just a missing analytics " +
                "key. Something removed the whole block from AndroidManifest.xml."
        }

    /**
     * Asserted in two steps on purpose.
     *
     * A single `assertFalse(getBoolean(KEY, false))` would PASS in exactly the
     * broken state this guards: with the key absent, `getBoolean` returns the
     * supplied default and a `false` default is trivially false. That is the
     * same trap [NotificationSmallIconTest] documents for its `getInt(…, 0)`
     * form. So check presence first, then read the value with `true` — the
     * dangerous value — as the default, making absence fail both ways.
     */
    @Test
    fun debugBuilds_disableFirebaseAnalyticsCollection() {
        val metaData = applicationMetaData()

        assertTrue(
            "$ANALYTICS_COLLECTION_ENABLED is missing from the merged manifest. Debug builds " +
                "will collect Firebase Analytics again, and CI emulator runs will pollute the " +
                "production GA4 property with phantom installs (nubecita-qyxg). Restore the " +
                "meta-data in app/src/debug/AndroidManifest.xml.",
            metaData.containsKey(ANALYTICS_COLLECTION_ENABLED),
        )
        assertFalse(
            "$ANALYTICS_COLLECTION_ENABLED is present but not false — debug builds are still " +
                "collecting analytics into the production GA4 property (nubecita-qyxg).",
            metaData.getBoolean(ANALYTICS_COLLECTION_ENABLED, true),
        )
    }

    private companion object {
        const val ANALYTICS_COLLECTION_ENABLED = "firebase_analytics_collection_enabled"
    }
}
