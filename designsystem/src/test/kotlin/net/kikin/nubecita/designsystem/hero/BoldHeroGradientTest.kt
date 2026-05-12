package net.kikin.nubecita.designsystem.hero

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for the parts of [BoldHeroGradient] that don't need a
 * live Compose runtime: the LRU cache contract and the
 * [enforceContrastAgainstWhite] guard.
 *
 * The Composable + Coil pipeline parts of [BoldHeroGradient] are
 * exercised by the screenshot tests in `:designsystem/src/screenshotTest`
 * and by feature-side instrumentation tests in Bead D.
 */
class BoldHeroGradientTest {
    private val sampleStops =
        BoldHeroGradientStops(
            top = Color(0xFF8080FF),
            bottom = Color(0xFF202060),
        )

    @Test
    fun defaultCachePutThenGetReturnsStops() {
        val cache = newDefaultCache()
        cache.put("https://example.com/banner.jpg", sampleStops)

        val retrieved = cache.get("https://example.com/banner.jpg")

        assertNotNull(retrieved)
        assertSame(sampleStops, retrieved)
    }

    @Test
    fun defaultCacheMissReturnsNull() {
        val cache = newDefaultCache()
        assertNull(cache.get("https://example.com/never-stored.jpg"))
    }

    @Test
    fun defaultCacheLruEvictsOldestEntry() {
        // 2-entry cache so the test stays terse. Same semantics as the
        // production 16-entry one.
        val cache = newDefaultCache(maxEntries = 2)
        cache.put("a", sampleStops)
        cache.put("b", sampleStops)
        cache.put("c", sampleStops) // evicts "a"

        assertNull(cache.get("a"), "oldest entry MUST be evicted on LRU overflow")
        assertNotNull(cache.get("b"))
        assertNotNull(cache.get("c"))
    }

    @Test
    fun contrastGuardLeavesAlreadyDarkColorsUntouched() {
        val alreadyDark = Color(red = 0.1f, green = 0.1f, blue = 0.1f)
        // Precondition: this color is below the AA threshold.
        assertTrue(alreadyDark.luminance() < 0.183f, "test setup MUST start under the threshold")

        val guarded = enforceContrastAgainstWhite(alreadyDark)
        assertEquals(alreadyDark, guarded, "below-threshold colors MUST pass through")
    }

    @Test
    fun contrastGuardDarkensWhiteUntilAaPasses() {
        val pureWhite = Color.White
        val guarded = enforceContrastAgainstWhite(pureWhite)

        assertTrue(
            guarded.luminance() <= 0.183f,
            "guarded color MUST satisfy AA threshold; got luminance ${guarded.luminance()}",
        )
    }

    @Test
    fun contrastGuardDarkensVeryLightColorsUntilAaPasses() {
        // A pastel banner — top of the WCAG AA failure range against white text.
        val pastel = Color(red = 0.85f, green = 0.85f, blue = 0.7f)
        assertTrue(pastel.luminance() > 0.183f, "test setup MUST start above the threshold")

        val guarded = enforceContrastAgainstWhite(pastel)

        assertTrue(
            guarded.luminance() <= 0.183f,
            "guarded color MUST satisfy AA threshold; got luminance ${guarded.luminance()}",
        )
    }

    @Test
    fun avatarHueFallbackIsDeterministic() {
        val stopsA = fromAvatarHue(217)
        val stopsB = fromAvatarHue(217)
        assertEquals(stopsA, stopsB, "same hue MUST produce the same stops")
    }

    @Test
    fun avatarHueFallbackBottomMeetsAaContrast() {
        // The avatarHue fallback constructs stops with `lightness = 0.18f`
        // — comfortably under the AA threshold. The guarantee is structural,
        // but the assertion documents it so a future tweak that brightens
        // the fallback gets caught.
        listOf(0, 45, 90, 135, 180, 217, 270, 315).forEach { hue ->
            val stops = fromAvatarHue(hue)
            assertTrue(
                stops.bottom.luminance() <= 0.183f,
                "fallback bottom MUST meet AA at hue=$hue; got luminance ${stops.bottom.luminance()}",
            )
        }
    }

    @Test
    fun avatarHueWrapsForOutOfRangeInputs() {
        // 380 mod 360 = 20; -10 mod 360 = 350. Both should produce
        // well-defined stops without crashing.
        val wrappedHigh = fromAvatarHue(380)
        val wrappedLow = fromAvatarHue(-10)
        val expectedHigh = fromAvatarHue(20)
        val expectedLow = fromAvatarHue(350)
        assertEquals(expectedHigh, wrappedHigh, "hues > 360 MUST wrap")
        assertEquals(expectedLow, wrappedLow, "negative hues MUST wrap")
    }

    /**
     * Minimal [BoldHeroGradientCache] backed by a fresh [LruCache]. The
     * production `DefaultBoldHeroGradientCache` class is file-private and
     * its singleton uses the default capacity (16); LRU-eviction tests
     * need a small-capacity instance, so this helper wraps a fresh
     * [LruCache] in an anonymous [BoldHeroGradientCache] that mirrors
     * the production behavior without reaching into file-private symbols.
     */
    private fun newDefaultCache(maxEntries: Int = 16): BoldHeroGradientCache {
        val lru = androidx.collection.LruCache<String, BoldHeroGradientStops>(maxEntries)
        return object : BoldHeroGradientCache {
            override fun get(key: String): BoldHeroGradientStops? = lru.get(key)

            override fun put(
                key: String,
                stops: BoldHeroGradientStops,
            ) {
                lru.put(key, stops)
            }
        }
    }
}
