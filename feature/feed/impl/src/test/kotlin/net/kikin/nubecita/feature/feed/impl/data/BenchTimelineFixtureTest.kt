package net.kikin.nubecita.feature.feed.impl.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Guards the bench feed fixture's "hero fold is GIF-free" invariant.
 *
 * The fastlane marketing journey (`MarketingScreenshotJourney.a01Feed`) captures
 * the INITIAL feed viewport for the Play Store hero. An animated GIF in that
 * frame freezes on whatever frame is showing at capture time, so the uploaded
 * screenshot would differ run to run. GIF posts therefore live below the
 * captured fold (index >= [HERO_FOLD]); they are still in the macrobench scroll
 * path further down the list.
 *
 * This is a STRUCTURAL check, not a pixel test: a GIF frame is non-deterministic
 * by nature, so diffing the rendered hero would itself be flaky. We instead pin
 * the only property that actually matters — no GIF in the captured fold.
 */
class BenchTimelineFixtureTest {
    @Test
    fun `hero-fold posts carry no GIF embed`() {
        // testDebugUnitTest runs with the module dir as the working directory;
        // fall back to the repo-root-relative path in case a runner differs.
        val fixture =
            listOf(
                File("src/bench/assets/timeline.json"),
                File("feature/feed/impl/src/bench/assets/timeline.json"),
            ).firstOrNull { it.exists() }
                ?: error("bench timeline.json not found from ${File("").absolutePath}")

        val items =
            Json
                .parseToJsonElement(fixture.readText())
                .jsonObject["items"]!!
                .jsonArray

        items.take(HERO_FOLD).forEachIndexed { index, item ->
            val type =
                item.jsonObject["post"]!!
                    .jsonObject["embed"]!!
                    .jsonObject["type"]!!
                    .jsonPrimitive.content
            assertTrue(type != "Gif") {
                "Bench timeline item $index is a Gif embed. The first $HERO_FOLD " +
                    "posts are captured in the fastlane marketing hero and must stay " +
                    "deterministic — move GIF posts below index $HERO_FOLD."
            }
        }
    }

    private companion object {
        /** Posts visible in `a01Feed`'s initial, un-scrolled viewport capture. */
        const val HERO_FOLD = 4
    }
}
