package net.kikin.nubecita.share

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import net.kikin.nubecita.BuildConfig
import net.kikin.nubecita.MainActivity
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import net.kikin.nubecita.feature.composer.impl.R as ComposerR

/**
 * End-to-end smoke for the inbound share target (`nubecita-9xoz`): an
 * `ACTION_SEND` `text/plain` intent delivered to the real [MainActivity] opens
 * the composer prefilled with the shared text — the full path through
 * `handleShareIntent` → `ShareIntentParser` → the buffered `DeepLinkRouter` →
 * `MainShell` → composer.
 *
 * Bench-only: `FakeSessionStateProvider` boots signed-in straight into
 * `MainShell`, so the buffered router drains and the composer route resolves.
 * On the production flavor the app opens Login (the buffered share would sit
 * undrained behind the auth gate), so this `assumeTrue`-skips there — matching
 * [net.kikin.nubecita.navigation.ModerationNavigationE2ETest]. The CI
 * `instrumented` job runs the production flavor, so this runs on bench / local
 * connected runs (`connectedBenchDebugAndroidTest`); the `ShareIntentParser`
 * classification is covered deterministically by JVM unit tests, and the copy
 * glue by `DefaultSharedMediaStoreTest`.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ShareToComposerE2ETest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val grantNotifications: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private lateinit var device: UiDevice
    private lateinit var context: Context

    @Before
    fun setUp() {
        assumeTrue("Share E2E requires the bench flavor (boots signed-in)", BuildConfig.FLAVOR == "bench")
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().targetContext
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun actionSendText_opensComposerPrefilled() {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, SHARED_URL)
            }

        ActivityScenario.launch<MainActivity>(intent).use {
            // The shared URL is seeded verbatim into the composer field. Asserting
            // on the shared text itself is locale-independent and is the direct
            // evidence that the share reached the composer prefilled.
            assertNotNull(
                "composer never opened prefilled with the shared URL — share path likely broken",
                device.wait(Until.findObject(By.textContains(SHARED_HOST)), WAIT_MS),
            )
        }
    }

    @Test
    fun actionSendImage_attachesImageAndEnablesPost() {
        val imageUri = insertTestImage()
        try {
            val intent =
                Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_SEND
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

            ActivityScenario.launch<MainActivity>(intent).use {
                val postLabel = context.getString(ComposerR.string.composer_post_action)
                assertNotNull(
                    "composer never opened for the image share",
                    device.wait(Until.findObject(By.text(postLabel)), WAIT_MS),
                )
                // The image is copied + attached asynchronously; a post with an
                // attachment (and no text) is submittable, so Post enables. An
                // empty composer — the failure mode where the image never
                // attached — keeps Post disabled.
                assertTrue(
                    "image share should attach and enable Post",
                    device.wait(Until.hasObject(By.text(postLabel).enabled(true)), WAIT_MS),
                )
            }
        } finally {
            context.contentResolver.delete(imageUri, null, null)
        }
    }

    /** Insert a real 64×64 JPEG the app owns into MediaStore and return its (non-own-authority) content:// Uri. */
    private fun insertTestImage(): Uri {
        val values =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "nubecita_share_e2e.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            }
        val resolver = context.contentResolver
        val uri =
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("could not insert a test image into MediaStore")
        try {
            resolver.openOutputStream(uri)!!.use { out ->
                val bitmap =
                    Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                bitmap.recycle()
            }
        } catch (e: Throwable) {
            // Don't leak the MediaStore row if writing the bytes fails before the
            // caller's finally-delete can run.
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    private companion object {
        const val SHARED_URL = "https://example.com/shared-article"
        const val SHARED_HOST = "example.com"
        const val WAIT_MS = 8_000L
    }
}
