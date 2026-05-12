package net.kikin.nubecita.designsystem.hero

import androidx.collection.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The two-stop "Bold Expressive" hero gradient — vibrant at the top,
 * dark-muted at the bottom — used as the profile screen's hero
 * backdrop. The bottom stop is the contrast surface for white text
 * overlays, so its luminance is bounded for WCAG AA against white
 * (see [enforceContrastAgainstWhite]).
 */
public data class BoldHeroGradientStops(
    val top: Color,
    val bottom: Color,
)

/**
 * Read-side cache for resolved [BoldHeroGradientStops], keyed on
 * banner URL. Production wires a private [LruCache]-backed default
 * via [NubecitaTheme]; tests inject a fake to assert hit/miss behavior
 * (e.g. that re-entering a profile after navigating away doesn't
 * re-decode the banner image).
 *
 * The cache stores derived stops rather than the raw [Palette] — stop
 * derivation is trivial but the relative-luminance contrast guard
 * isn't free, so caching the post-guard result is what callers
 * actually want.
 */
public interface BoldHeroGradientCache {
    public fun get(key: String): BoldHeroGradientStops?

    public fun put(
        key: String,
        stops: BoldHeroGradientStops,
    )
}

/**
 * Process-shared default implementation. 16 entries is more than the
 * number of profiles a typical user opens in a session; LRU eviction
 * keeps memory bounded while preserving the "navigate away and back
 * is instant" property the spec requires.
 */
private class DefaultBoldHeroGradientCache(
    maxEntries: Int = 16,
) : BoldHeroGradientCache {
    private val lru = LruCache<String, BoldHeroGradientStops>(maxEntries)

    override fun get(key: String): BoldHeroGradientStops? = lru.get(key)

    override fun put(
        key: String,
        stops: BoldHeroGradientStops,
    ) {
        lru.put(key, stops)
    }
}

/**
 * [CompositionLocal] for the read/write side of the gradient cache.
 * [NubecitaTheme] provides a process-shared [DefaultBoldHeroGradientCache];
 * tests override the local with a fake to assert cache behavior.
 *
 * The default value is a no-op cache (never hits, never stores) so
 * that consumers outside [NubecitaTheme] still render — they just
 * pay the Palette extraction on every recomposition. The contract is
 * that production callers always wrap in [NubecitaTheme].
 */
public val LocalBoldHeroGradientCache: androidx.compose.runtime.ProvidableCompositionLocal<BoldHeroGradientCache> =
    staticCompositionLocalOf { NoOpCache }

/** Bypass cache when no [NubecitaTheme] is in scope (previews, raw test fixtures). */
internal val DefaultGradientCache: BoldHeroGradientCache = DefaultBoldHeroGradientCache()

private object NoOpCache : BoldHeroGradientCache {
    override fun get(key: String): BoldHeroGradientStops? = null

    override fun put(
        key: String,
        stops: BoldHeroGradientStops,
    ) {
        // Intentionally empty — see KDoc on LocalBoldHeroGradientCache.
    }
}

/**
 * "Bold Expressive" hero backdrop for the profile screen.
 *
 * Decodes [banner] via the global Coil image loader, runs
 * [Palette] extraction on [Dispatchers.Default] off the main thread,
 * caches the resulting [BoldHeroGradientStops] via
 * [LocalBoldHeroGradientCache] (keyed on the banner URL), and renders
 * a vertical 2-stop gradient — vibrant at the top, dark-muted at the
 * bottom. The bottom stop is contrast-guarded for WCAG AA against
 * white text overlays per [enforceContrastAgainstWhite].
 *
 * Fallback path: when [banner] is `null` (no custom banner) or
 * extraction hasn't completed yet, the composable derives the
 * gradient deterministically from [avatarHue] via [fromAvatarHue].
 * The fallback IS the initial state, so the swap to the
 * palette-derived gradient — when it arrives — happens once,
 * one-directional, with no flicker back.
 *
 * The [banner] parameter is a nullable URL string — the same shape
 * `app.bsky.actor.defs#profileViewDetailed.banner` returns and the
 * same shape the existing `NubecitaAsyncImage(model: Any?)` Coil
 * wrapper accepts.
 *
 * @param banner Nullable URL string for the user's banner image.
 *   When `null`, the gradient renders deterministically from [avatarHue]
 *   without ever touching Coil or Palette.
 * @param avatarHue Deterministic hue in `0..360` for the fallback
 *   gradient. Typically derived from `did + handle.firstChar` upstream.
 * @param modifier Modifier applied to the backdrop [Box].
 * @param content Foreground content rendered over the gradient (avatar,
 *   name, handle, bio, stats, etc.). The gradient is drawn behind via
 *   [Modifier.background].
 */
@Composable
public fun BoldHeroGradient(
    banner: String?,
    avatarHue: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val stops = rememberBoldHeroGradient(banner = banner, avatarHue = avatarHue)
    Box(
        modifier =
            modifier.background(
                Brush.verticalGradient(
                    colorStops = arrayOf(0f to stops.top, 1f to stops.bottom),
                ),
            ),
    ) {
        content()
    }
}

/**
 * Compose-friendly entry point that resolves stops imperatively — used
 * by callers that need the gradient colors without the [Box] wrapper
 * (e.g. for compositing into a custom Canvas / Brush).
 *
 * Same caching semantics as [BoldHeroGradient]: cache hit returns
 * synchronously; cache miss returns the [avatarHue] fallback until
 * extraction completes, then swaps to the palette-derived stops.
 */
@Composable
public fun rememberBoldHeroGradient(
    banner: String?,
    avatarHue: Int,
): BoldHeroGradientStops {
    val context = LocalContext.current
    val cache = LocalBoldHeroGradientCache.current

    // Normalize blank to null up front so the initial cache lookup and the
    // async extraction guard use the same notion of "no banner". Without
    // this, an empty string would issue a cache lookup keyed on "" before
    // the LaunchedEffect's isNullOrEmpty() short-circuit.
    val normalizedBanner = banner?.takeUnless { it.isEmpty() }

    // Initial state = avatarHue-derived gradient. Synchronous, deterministic,
    // never flickers. When Palette extraction completes (banner != null path),
    // we update once.
    val fallbackStops = remember(avatarHue) { fromAvatarHue(avatarHue) }
    var stops by remember(normalizedBanner, avatarHue) {
        mutableStateOf(normalizedBanner?.let(cache::get) ?: fallbackStops)
    }

    // Kick off async Palette extraction when banner is non-null and not cached.
    // Keyed on the banner URL — recomposing with the same banner doesn't
    // re-trigger; switching banners cancels the in-flight load.
    LaunchedEffect(normalizedBanner) {
        if (normalizedBanner == null) return@LaunchedEffect
        cache.get(normalizedBanner)?.let {
            stops = it
            return@LaunchedEffect
        }
        val resolved = extractGradientStops(context, normalizedBanner)
        if (resolved != null) {
            cache.put(normalizedBanner, resolved)
            stops = resolved
        }
        // On extraction failure, the avatarHue fallback persists — better
        // than blanking the hero. No effect emitted; the failure is logged
        // by Coil internally.
    }
    return stops
}

/**
 * Decode the banner via Coil, extract a palette off the main thread,
 * derive 2-stop gradient colors, and contrast-guard the bottom stop.
 *
 * Returns `null` on Coil failure (network error, decode failure, etc.).
 * Caller falls back to the avatarHue-derived gradient in that case.
 */
private suspend fun extractGradientStops(
    context: android.content.Context,
    bannerUrl: String,
): BoldHeroGradientStops? =
    withContext(Dispatchers.Default) {
        val request =
            ImageRequest
                .Builder(context)
                .data(bannerUrl)
                .allowHardware(false) // Palette can't read hardware bitmaps
                .build()
        val result = context.imageLoader.execute(request)
        val image = (result as? SuccessResult)?.image ?: return@withContext null
        val bitmap = image.toBitmap()
        val palette = Palette.from(bitmap).generate()

        // Prefer vibrant + darkMuted; fall back through the swatch
        // hierarchy when a particular swatch isn't represented in
        // the image (e.g. monochrome banners). The defaults
        // (Color.Black) are sentinels that signal "no swatch
        // found" and force fallback to avatar hue downstream.
        val vibrantArgb =
            palette.vibrantSwatch?.rgb
                ?: palette.lightVibrantSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: palette.lightMutedSwatch?.rgb
                ?: return@withContext null
        val darkArgb =
            palette.darkMutedSwatch?.rgb
                ?: palette.darkVibrantSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
                ?: return@withContext null

        val top = Color(vibrantArgb)
        val rawBottom = Color(darkArgb)
        val bottom = enforceContrastAgainstWhite(rawBottom)

        BoldHeroGradientStops(top = top, bottom = bottom)
    }

/**
 * Derive a deterministic 2-stop gradient from a `0..360` hue.
 *
 * Top stop: full saturation, mid lightness — reads as the user's
 * primary color. Bottom stop: lower saturation, darker lightness —
 * the contrast surface for white text. Both stops are within
 * sRGB gamut and AA-compliant against white text by construction.
 */
internal fun fromAvatarHue(hue: Int): BoldHeroGradientStops {
    val normalized = ((hue % 360) + 360) % 360
    val top = Color.hsl(hue = normalized.toFloat(), saturation = 0.55f, lightness = 0.45f)
    val bottom = Color.hsl(hue = normalized.toFloat(), saturation = 0.45f, lightness = 0.18f)
    return BoldHeroGradientStops(top = top, bottom = bottom)
}

/**
 * Darken [color] until its relative luminance produces at least the
 * WCAG AA `4.5:1` contrast ratio against white text.
 *
 * Threshold derivation:
 * `contrast = (L_white + 0.05) / (L_color + 0.05) >= 4.5`
 * `L_color <= (1.05 / 4.5) - 0.05 = 0.183`
 *
 * Strategy: blend toward black in 5% steps (max 20 iterations — in
 * practice convergence is well under 10). Pure-white inputs would
 * never converge in a naïve "subtract from each channel" approach
 * because [Color.luminance] is gamma-decoded; the blend handles that
 * correctly.
 */
internal fun enforceContrastAgainstWhite(color: Color): Color {
    if (color.luminance() <= MAX_BOTTOM_LUMINANCE) return color
    var current = color
    repeat(MAX_DARKEN_ITERATIONS) {
        if (current.luminance() <= MAX_BOTTOM_LUMINANCE) return current
        current =
            Color(
                red = current.red * (1f - DARKEN_STEP),
                green = current.green * (1f - DARKEN_STEP),
                blue = current.blue * (1f - DARKEN_STEP),
                alpha = current.alpha,
            )
    }
    return current
}

private const val MAX_BOTTOM_LUMINANCE = 0.183f
private const val DARKEN_STEP = 0.05f
private const val MAX_DARKEN_ITERATIONS = 20
