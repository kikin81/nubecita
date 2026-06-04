package net.kikin.nubecita.feature.feed.impl.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Guards two bench feed fixture invariants tied to the fastlane marketing hero
 * (`MarketingScreenshotJourney.a01Feed`), which captures the INITIAL feed
 * viewport for the Play Store:
 *
 * 1. **GIF-free hero** — an animated GIF freezes on whatever frame is showing at
 *    capture time, so the uploaded screenshot would differ run to run. GIF posts
 *    live below the captured fold (index >= [HERO_FOLD]).
 * 2. **No content-warning cover in the hero** — a mature/NSFW-labelled post
 *    renders as a "Sensitive content" cover; capturing that into the Play Store
 *    listing would publish a content-filtered marketing screenshot. Covered posts
 *    therefore also live below the fold.
 *
 * These are STRUCTURAL checks, not pixel tests — they pin the only properties
 * that matter so a future fixture edit can't silently regress the marketing hero.
 */
class BenchTimelineFixtureTest {
    @Test
    fun `hero-fold posts carry no GIF embed`() {
        heroFoldItems().forEachIndexed { index, item ->
            val type = item.embedField("type")
            assertTrue(type != "Gif") {
                "Bench timeline item $index is a Gif embed. The first $HERO_FOLD " +
                    "posts are captured in the fastlane marketing hero and must stay " +
                    "deterministic — move GIF posts below index $HERO_FOLD."
            }
        }
    }

    @Test
    fun `hero-fold posts carry no content-warning cover`() {
        heroFoldItems().forEachIndexed { index, item ->
            assertNull(item.embedField("contentWarning")) {
                "Bench timeline item $index carries a contentWarning. The first " +
                    "$HERO_FOLD posts are captured in the fastlane marketing hero; a " +
                    "covered (NSFW-labelled) post would publish a content-filtered Play " +
                    "Store screenshot — move mature posts below index $HERO_FOLD."
            }
        }
    }

    // The first [HERO_FOLD] feed items — what a01Feed captures un-scrolled.
    private fun heroFoldItems(): List<JsonElement> {
        // testDebugUnitTest runs with the module dir as the working directory;
        // fall back to the repo-root-relative path in case a runner differs.
        val fixture =
            listOf(
                File("src/bench/assets/timeline.json"),
                File("feature/feed/impl/src/bench/assets/timeline.json"),
            ).firstOrNull { it.exists() }
                ?: error("bench timeline.json not found from ${File("").absolutePath}")
        return Json
            .parseToJsonElement(fixture.readText())
            .jsonObject["items"]!!
            .jsonArray
            .take(HERO_FOLD)
    }

    // Reads `post.embed.<field>` as a string, or null when the field is absent.
    private fun JsonElement.embedField(name: String): String? =
        jsonObject["post"]!!
            .jsonObject["embed"]!!
            .jsonObject[name]
            ?.jsonPrimitive
            ?.content

    private companion object {
        /** Posts visible in `a01Feed`'s initial, un-scrolled viewport capture. */
        const val HERO_FOLD = 4
    }
}
