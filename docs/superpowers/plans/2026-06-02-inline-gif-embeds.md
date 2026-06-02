# Inline Animated GIF Embeds Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Render GIFs posted as `app.bsky.embed.external` (Klipy/Tenor/`.gif`) as inline, looping, animated images via Coil's `AnimatedImageDecoder`, instead of static link cards — in feed, post detail, quoted posts, and record-with-media.

**Architecture:** New `EmbedUi.Gif` / `QuotedEmbedUi.Gif` (`: MediaEmbed`) produced by a GIF detector in `:core:feed-mapping`; rendered by a new `PostCardGifEmbed` (Coil `AsyncImage` + the `AnimatedImageDecoder` registered in `CoilModule`). Each GIF is an independent animated drawable — unlike the single-shared-ExoPlayer video pipeline, this lets N GIFs animate at once, and only on-screen items run (LazyColumn recycling + Coil disposal). minSdk 28 ⇒ the hardware `AnimatedImageDecoder`, decode off the main thread.

**Tech Stack:** Kotlin, Jetpack Compose, Coil 3.4.0 (`coil-gif`), Hilt, JUnit5/MockK, AGP compose screenshot plugin.

**Bd:** nubecita-q01y

---

## Detection facts (from atproto analysis)
- GIF marker: external `uri` host ∈ {`static.klipy.com`, `media.tenor.com`} OR host ends `.giphy.com` OR path (pre-`?`) ends `.gif`.
- aspectRatio: Klipy/Tenor URLs carry `ww`/`hh` query params → `ww/hh`; null otherwise.
- Coil loads the `.gif` URL directly (`content-type: image/gif`) — no mp4 reconstruction.

## File map
- Modify `gradle/libs.versions.toml` — add `coil-gif` library.
- Modify `app/build.gradle.kts` — add `coil-gif` impl dep.
- Modify `app/src/main/java/net/kikin/nubecita/data/CoilModule.kt` — register `AnimatedImageDecoder.Factory()`.
- Modify `data/models/.../EmbedUi.kt` — add `Gif : MediaEmbed`.
- Modify `data/models/.../QuotedEmbedUi.kt` — add `Gif : MediaEmbed`.
- Create `core/feed-mapping/src/main/.../GifEmbed.kt` — detector + aspect parser.
- Modify `core/feed-mapping/src/main/.../FeedMapping.kt` — route GIF externals to `Gif`.
- Create `core/feed-mapping/src/test/.../GifEmbedTest.kt` — detector unit tests.
- Modify `core/feed-mapping/src/test/.../FeedMappingTest.kt` — Klipy-external→Gif mapping test.
- Create `designsystem/src/main/.../component/PostCardGifEmbed.kt` — the render.
- Modify `designsystem/.../PostCard.kt`, `PostCardQuotedPost.kt`, `PostCardRecordWithMediaEmbed.kt` — add `is …Gif` branches.
- Create `designsystem/src/screenshotTest/.../PostCardGifEmbedScreenshotTest.kt` + baseline.
- Modify `feature/feed/impl/src/bench/.../data/BenchTimelineDto.kt` + `BenchTimelineMapper.kt` + `assets/timeline.json` + add a bench GIF asset — GIF fixture for macrobench.

---

### Task 1: `EmbedUi.Gif` + `QuotedEmbedUi.Gif` models

**Files:** Modify `data/models/src/main/kotlin/net/kikin/nubecita/data/models/EmbedUi.kt`, `QuotedEmbedUi.kt`

- [ ] **Step 1:** Add to `EmbedUi.kt` immediately after the `External` data class (after line 118), inside the sealed interface:

```kotlin
    /**
     * A GIF posted as an `app.bsky.embed.external` (Klipy/Tenor/Giphy, or any
     * `.gif` URL). Rendered inline as a looping animated image via Coil's
     * AnimatedImageDecoder — NOT the video pipeline: the shared single ExoPlayer
     * can only drive one video, so N GIFs in a thread would freeze. Coil gives
     * each GIF an independent drawable; only on-screen ones animate.
     *
     * - [gifUrl] the animated source (the external `uri`, an `image/gif`).
     * - [thumbUrl] the static poster (the external `thumb`), null if absent.
     * - [aspectRatio] width/height when derivable (Klipy/Tenor `ww`/`hh` query
     *   params); null when unknown — the render caps height instead of guessing.
     * - [alt] alt text / title for accessibility (may be empty → treat as null).
     */
    public data class Gif(
        val gifUrl: String,
        val thumbUrl: String?,
        val aspectRatio: Float?,
        val alt: String?,
    ) : MediaEmbed
```

- [ ] **Step 2:** Add to `QuotedEmbedUi.kt` after the `External` data class (after line ~68):

```kotlin
    /**
     * A GIF external embed inside a quoted post. Same field-set as
     * [EmbedUi.Gif] — see that variant's KDoc.
     */
    public data class Gif(
        val gifUrl: String,
        val thumbUrl: String?,
        val aspectRatio: Float?,
        val alt: String?,
    ) : MediaEmbed
```

- [ ] **Step 3:** Compile — `./gradlew :data:models:compileDebugKotlin` — expect SUCCESS (compilers in dependent modules now flag non-exhaustive `when`; fixed in Tasks 3–4).
- [ ] **Step 4:** Commit `git add data/models && git commit -m "feat(models): add EmbedUi.Gif / QuotedEmbedUi.Gif variants"`

---

### Task 2: Coil `coil-gif` dependency + `AnimatedImageDecoder` registration

**Files:** Modify `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/java/net/kikin/nubecita/data/CoilModule.kt`

- [ ] **Step 1:** In `libs.versions.toml` `[libraries]`, after `coil-core` (line ~103) add:

```toml
coil-gif = { module = "io.coil-kt.coil3:coil-gif" }
```

- [ ] **Step 2:** In `app/build.gradle.kts`, beside the other coil deps (`implementation(libs.coil.core)` / `coil.network.okhttp`) add (keep alphabetical for checkSortDependencies):

```kotlin
    implementation(libs.coil.gif)
```

- [ ] **Step 3:** In `CoilModule.kt`, add the import `import coil3.gif.AnimatedImageDecoder` and change the components block:

```kotlin
            .components {
                add(OkHttpNetworkFetcherFactory())
                // Animated GIF/WebP support (minSdk 28 ⇒ the hardware
                // AnimatedImageDecoder, not the slow software GifDecoder).
                add(AnimatedImageDecoder.Factory())
            }
```

- [ ] **Step 4:** Compile — `./gradlew :app:compileProductionDebugKotlin` — expect SUCCESS.
- [ ] **Step 5:** Commit `git add gradle/libs.versions.toml app && git commit -m "feat(coil): register AnimatedImageDecoder for animated GIFs"`

---

### Task 3: GIF detector + feed-mapping routing (TDD)

**Files:** Create `core/feed-mapping/src/main/kotlin/net/kikin/nubecita/core/feedmapping/GifEmbed.kt`; create `core/feed-mapping/src/test/kotlin/net/kikin/nubecita/core/feedmapping/GifEmbedTest.kt`; modify `FeedMapping.kt`; modify `FeedMappingTest.kt`.

- [ ] **Step 1: Write the failing detector test** — `GifEmbedTest.kt`:

```kotlin
package net.kikin.nubecita.core.feedmapping

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class GifEmbedTest {
    @Test fun `klipy gif url is detected`() {
        assertTrue(isGifExternalUri("https://static.klipy.com/ii/h/28/65/X.gif?hh=498&ww=463&mp4=Y"))
    }

    @Test fun `tenor and giphy and bare dot-gif are detected`() {
        assertTrue(isGifExternalUri("https://media.tenor.com/abc/clip.gif"))
        assertTrue(isGifExternalUri("https://media1.giphy.com/media/abc/giphy.gif"))
        assertTrue(isGifExternalUri("https://example.com/a/b.GIF?x=1"))
    }

    @Test fun `plain article link is not a gif`() {
        assertFalse(isGifExternalUri("https://example.com/articles/embedded-db"))
    }

    @Test fun `aspect ratio parsed from ww and hh`() {
        assertEquals(463f / 498f, gifAspectRatioOrNull("https://static.klipy.com/x.gif?hh=498&ww=463")!!, 0.0001f)
    }

    @Test fun `aspect ratio null when params absent`() {
        assertNull(gifAspectRatioOrNull("https://media.tenor.com/abc/clip.gif"))
    }
}
```

- [ ] **Step 2: Run — FAIL** — `./gradlew :core:feed-mapping:testDebugUnitTest --tests "*GifEmbedTest*"` — expect unresolved-reference failure.

- [ ] **Step 3: Implement `GifEmbed.kt`:**

```kotlin
package net.kikin.nubecita.core.feedmapping

/**
 * Detection + sizing for GIFs posted as `app.bsky.embed.external`. Bluesky's
 * GIF picker (now Klipy; historically Tenor) publishes the GIF as an external
 * embed whose `uri` is an `image/gif`. We render those inline (animated) rather
 * than as a static link card. See nubecita-q01y.
 */

private val GIF_EXACT_HOSTS = setOf("static.klipy.com", "media.tenor.com")

/** True when [uri] is an animated GIF: a known provider host, or a `.gif` path. */
internal fun isGifExternalUri(uri: String): Boolean {
    val host = uriHost(uri)
    if (host != null && (host in GIF_EXACT_HOSTS || host.endsWith(".giphy.com"))) return true
    return uri.substringBefore('?').endsWith(".gif", ignoreCase = true)
}

/** width/height from `ww`/`hh` query params (Klipy/Tenor), or null when absent/invalid. */
internal fun gifAspectRatioOrNull(uri: String): Float? {
    val query = uri.substringAfter('?', missingDelimiterValue = "")
    if (query.isEmpty()) return null
    val params =
        query.split('&').mapNotNull { pair ->
            val k = pair.substringBefore('=', "")
            val v = pair.substringAfter('=', "")
            if (k.isNotEmpty() && v.isNotEmpty()) k to v else null
        }.toMap()
    val w = params["ww"]?.toFloatOrNull()
    val h = params["hh"]?.toFloatOrNull()
    return if (w != null && h != null && w > 0f && h > 0f) w / h else null
}

private fun uriHost(uri: String): String? =
    runCatching { java.net.URI(uri).host?.lowercase() }.getOrNull()
```

- [ ] **Step 4: Run — PASS** — same command — expect PASS.

- [ ] **Step 5: Write the failing mapping test** — append to `FeedMappingTest.kt` (mirror the existing external test at line 62; add a constant like `POST_WITH_EXTERNAL_EMBED` but with a Klipy `.gif` uri):

```kotlin
    @Test
    fun `klipy external embed projects to EmbedUi Gif`() {
        val postView = decodePostView(POST_WITH_KLIPY_GIF_EMBED)
        val mapped = postView.toPostUiCore()
        assertNotNull(mapped)
        val gif = assertInstanceOf(EmbedUi.Gif::class.java, mapped!!.embed)
        assertEquals("https://static.klipy.com/x/Y.gif?hh=498&ww=463", gif.gifUrl)
        assertEquals(463f / 498f, gif.aspectRatio!!, 0.0001f)
    }
```

with the fixture constant (copy `POST_WITH_EXTERNAL_EMBED`, swap the `external.uri` to `https://static.klipy.com/x/Y.gif?hh=498&ww=463` and add a `thumb`):

```kotlin
    const val POST_WITH_KLIPY_GIF_EMBED = """
        { "uri":"at://did:plc:fake/app.bsky.feed.post/g1","cid":"bafyreifakecid000000000000000000000000000000000",
          "author":{"did":"did:plc:fake","handle":"fake.bsky.social"},"indexedAt":"2026-04-26T12:00:00Z",
          "record":{"${'$'}type":"app.bsky.feed.post","text":"gif","createdAt":"2026-04-26T12:00:00Z"},
          "embed":{"${'$'}type":"app.bsky.embed.external#view","external":{
            "uri":"https://static.klipy.com/x/Y.gif?hh=498&ww=463","title":"a gif","description":"" }}}
    """
```

- [ ] **Step 6: Run — FAIL** — `./gradlew :core:feed-mapping:testDebugUnitTest --tests "*FeedMappingTest*"` — expect the mapping still returns `External`.

- [ ] **Step 7: Route GIFs in `FeedMapping.kt`.** Change `toEmbedUiExternal()` (line 213) to return `EmbedUi.MediaEmbed` with a GIF branch:

```kotlin
fun ExternalView.toEmbedUiExternal(): EmbedUi.MediaEmbed {
    val uri = external.uri.raw
    return if (isGifExternalUri(uri)) {
        EmbedUi.Gif(
            gifUrl = uri,
            thumbUrl = external.thumb?.raw,
            aspectRatio = gifAspectRatioOrNull(uri),
            alt = external.title.ifBlank { external.description }.ifBlank { null },
        )
    } else {
        EmbedUi.External(
            uri = uri,
            domain = displayDomainOf(uri),
            title = external.title,
            description = external.description,
            thumbUrl = external.thumb?.raw,
        )
    }
}
```

Add a quoted helper (used by the two quoted sites at 384 and 352):

```kotlin
private fun ExternalView.toQuotedExternalOrGif(): QuotedEmbedUi.MediaEmbed {
    val uri = external.uri.raw
    return if (isGifExternalUri(uri)) {
        QuotedEmbedUi.Gif(
            gifUrl = uri,
            thumbUrl = external.thumb?.raw,
            aspectRatio = gifAspectRatioOrNull(uri),
            alt = external.title.ifBlank { external.description }.ifBlank { null },
        )
    } else {
        QuotedEmbedUi.External(
            uri = uri, domain = displayDomainOf(uri),
            title = external.title, description = external.description, thumbUrl = external.thumb?.raw,
        )
    }
}
```

Replace the inline `QuotedEmbedUi.External(...)` builds at the quoted dispatch (line ~384) and quoted record-with-media (line ~352) with `is ExternalView -> toQuotedExternalOrGif()`. (Sites 123 + 313 call `toEmbedUiExternal()` and need no change — its new return type `MediaEmbed` fits both `EmbedUi` and the `MediaEmbed?` slot.)

- [ ] **Step 8: Run — PASS** — both `*GifEmbedTest*` and `*FeedMappingTest*` green.
- [ ] **Step 9: Commit** `git add core/feed-mapping && git commit -m "feat(feed-mapping): route external GIF embeds to EmbedUi.Gif"`

---

### Task 4: `PostCardGifEmbed` render + exhaustive `when` branches

**Files:** Create `designsystem/.../component/PostCardGifEmbed.kt`; modify `PostCard.kt` (line 276 area), `PostCardQuotedPost.kt` (172), `PostCardRecordWithMediaEmbed.kt` (84).

- [ ] **Step 1: Create `PostCardGifEmbed.kt`:**

```kotlin
package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/**
 * Inline animated GIF (an `app.bsky.embed.external` whose URL is an image/gif).
 * Coil's AnimatedImageDecoder (registered in CoilModule) auto-loops it; each
 * instance is independent, so N GIFs animate at once, and off-screen ones stop
 * when the LazyColumn item leaves composition. [aspectRatio] reserves exact
 * layout space when known (no scroll jump); otherwise height is capped.
 */
@Composable
fun PostCardGifEmbed(
    gifUrl: String,
    aspectRatio: Float?,
    alt: String?,
    modifier: Modifier = Modifier,
) {
    val sized =
        modifier
            .fillMaxWidth()
            .let { if (aspectRatio != null) it.aspectRatio(aspectRatio) else it.heightIn(max = MAX_GIF_HEIGHT) }
            .clip(RoundedCornerShape(16.dp))
    NubecitaAsyncImage(
        model = gifUrl,
        contentDescription = alt,
        modifier = sized,
        contentScale = ContentScale.Crop,
    )
}

private val MAX_GIF_HEIGHT = 400.dp
```

- [ ] **Step 2:** In `PostCard.kt` `EmbedSlot` (after the `is EmbedUi.External` branch, ~line 287) add:

```kotlin
        is EmbedUi.Gif -> {
            Spacer(Modifier.height(10.dp))
            PostCardGifEmbed(gifUrl = embed.gifUrl, aspectRatio = embed.aspectRatio, alt = embed.alt)
        }
```

- [ ] **Step 3:** In `PostCardQuotedPost.kt` `QuotedEmbedSlot` (after `is QuotedEmbedUi.External`, ~line 184) add:

```kotlin
        is QuotedEmbedUi.Gif -> {
            Spacer(Modifier.height(8.dp))
            PostCardGifEmbed(gifUrl = embed.gifUrl, aspectRatio = embed.aspectRatio, alt = embed.alt)
        }
```

- [ ] **Step 4:** In `PostCardRecordWithMediaEmbed.kt` media `when` (after `is EmbedUi.External`, ~line 95) add:

```kotlin
        is EmbedUi.Gif -> {
            PostCardGifEmbed(gifUrl = media.gifUrl, aspectRatio = media.aspectRatio, alt = media.alt)
            true
        }
```

- [ ] **Step 5: Compile** — `./gradlew :designsystem:compileDebugKotlin` — expect SUCCESS (no non-exhaustive-when errors remain).
- [ ] **Step 6: Add `@Preview` + screenshot test.** Append a `@Preview` to `PostCardGifEmbed.kt` (use a fake `gifUrl = "https://example.com/x.gif"`, `aspectRatio = 1.2f`) and create `designsystem/src/screenshotTest/kotlin/.../PostCardGifEmbedScreenshotTest.kt` mirroring an existing screenshot test (e.g. the external-embed one). Generate the baseline: `./gradlew :designsystem:updateDebugScreenshotTest`.
- [ ] **Step 7: Validate screenshot** — `./gradlew :designsystem:validateDebugScreenshotTest` — expect PASS.
- [ ] **Step 8: Commit** `git add designsystem && git commit -m "feat(designsystem): render EmbedUi.Gif inline (PostCardGifEmbed)"`

---

### Task 5: Bench GIF fixture (for the macrobench)

**Files:** add a small animated GIF asset under `feature/feed/impl/src/bench/assets/img/gifs/`; modify `BenchTimelineDto.kt`, `BenchTimelineMapper.kt`, `assets/timeline.json`.

- [ ] **Step 1: Add a bench GIF asset.** Generate a tiny looping GIF (ImageMagick): `magick -size 200x200 -delay 8 plasma:fractal -loop 0 \( +clone \) feature/feed/impl/src/bench/assets/img/gifs/loop.gif` (or copy a small CC GIF). Keep it < 200 KB.
- [ ] **Step 2:** In `BenchTimelineDto.kt`, add `Gif` to the `Type` enum (`@SerialName("Gif") Gif`) and add fields the mapper needs (`gifUrl`, reuse `aspectRatio`, `altText`, `thumbUrl`).
- [ ] **Step 3:** In `BenchTimelineMapper.kt` `toEmbedUi()` add:

```kotlin
        BenchEmbedDto.Type.Gif ->
            EmbedUi.Gif(
                gifUrl = gifUrl.orEmpty(),
                thumbUrl = thumbUrl,
                aspectRatio = aspectRatio?.toFloat(),
                altText = altText,
            ).takeIf { gifUrl != null } ?: EmbedUi.Unsupported(typeUri = "app.bsky.embed.external")
```

(adjust field name `altText` → `alt` to match the model — model field is `alt`.)

- [ ] **Step 4:** Add 3–4 GIF posts to `timeline.json` with `"embed": { "type":"Gif", "gifUrl":"file:///android_asset/img/gifs/loop.gif", "aspectRatio":1.0, "altText":"looping demo" }` so several are visible while the FeedScrollBenchmark scrolls.
- [ ] **Step 5: Compile bench** — `./gradlew :feature:feed:impl:compileBenchDebugKotlin` — expect SUCCESS.
- [ ] **Step 6: Commit** `git add feature/feed/impl/src/bench && git commit -m "test(bench): add GIF posts to the bench feed fixture"`

---

### Task 6: Validate + finalize

- [ ] **Step 1:** Full check — `./gradlew :core:feed-mapping:testDebugUnitTest :designsystem:validateDebugScreenshotTest spotlessCheck :app:checkSortDependencies` — all green.
- [ ] **Step 2:** Compile the whole app — `./gradlew :app:assembleProductionDebug :app:assembleBenchDebug` — SUCCESS (proves all `when` sites + bench compile).
- [ ] **Step 3:** Macrobench — `./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest --tests "*FeedScrollBenchmark*"` on a real device (or via the `run-instrumented` CI label). Record `frameDurationCpuMs`/`frameOverrunMs` p50/p95/p99; compare against a main-branch run. Expect no meaningful regression; if `frameOverrunMs` p95 rises materially, cap concurrent animations / shrink decode size.
- [ ] **Step 4:** Manual smoke on the 3 analysis posts (giantsplodgedotcom / queencobra / frenemyofthepeople) — GIFs animate inline in feed + post detail.
- [ ] **Step 5:** Open PR with `Closes: nubecita-q01y`, attach bench numbers.

---

## Self-review notes
- Exhaustive `when` coverage: `EmbedUi.Gif` added to `PostCard.EmbedSlot`, `PostCardRecordWithMediaEmbed`; `QuotedEmbedUi.Gif` added to `PostCardQuotedPost.QuotedEmbedSlot`. Mapper sites 123/313 covered via `toEmbedUiExternal` return-type change; 384/352 via `toQuotedExternalOrGif`.
- Type consistency: model field is `alt` (not `altText`) — Task 5 mapper must map to `alt`. `gifUrl`/`thumbUrl`/`aspectRatio` consistent across model, mapper, render.
- Tap-to-fullscreen is intentionally out of scope for v1 (GIF shows full inline); follow-up can route a GIF tap to `:feature:mediaviewer`.
