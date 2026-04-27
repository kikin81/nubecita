# PostCard external link card embed — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render `app.bsky.embed.external` posts in `PostCard` as a native Material 3 link-preview card (thumb 1.91:1, title, description, domain footer) and open the URL in a Chrome Custom Tab on tap. Closes nubecita-aku.

**Architecture:** Pure Compose leaf composable in `:designsystem` (no heavy deps); new `EmbedUi.External` data variant in `:data:models`; mapper in `:feature:feed:impl` swaps the existing `Unsupported` route for `External(...)`; `FeedScreen` provides the `CustomTabsIntent` launcher via the existing `PostCallbacks` plumbing. Spec: `docs/superpowers/specs/2026-04-27-postcard-external-embed-design.md`.

**Tech Stack:** Kotlin 2.x, Jetpack Compose with Material 3 Expressive, AGP screenshot test plugin, JUnit 5 + kotlinx.serialization for mapper tests, `androidx.browser:browser` for Custom Tabs (already in libs.versions.toml).

---

## Task 1: Build `PostCardExternalEmbed` leaf composable + Compose previews

**Files:**
- Create: `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCardExternalEmbed.kt`

The leaf composable takes raw fields (not `EmbedUi.External`) so it has no dependency on the `:data:models` sealed-variant change. That comes in Task 3. Building the leaf first lets us iterate on visuals via `@Preview` immediately.

- [ ] **Step 1.1: Write the leaf composable**

Create `PostCardExternalEmbed.kt`:

```kotlin
package net.kikin.nubecita.designsystem.component

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Renders a Bluesky `app.bsky.embed.external` link card natively in Compose.
 *
 * The whole `Surface` is a single tap target — taps anywhere open the URI
 * via the host-supplied [onTap] callback (typically a `CustomTabsIntent`
 * launcher provided by the screen).
 *
 * Layout:
 * - Optional thumbnail at 1.91:1 (OG-image standard) using [ContentScale.Crop],
 *   clipped to the card's top corners. When [thumbUrl] is null the section
 *   is omitted entirely (text-only card; no placeholder, no gradient).
 * - Title (titleMedium), description (bodyMedium) — each capped at 2 lines
 *   with ellipsis. Empty strings are skipped (no empty `Text` row).
 * - Domain footer: globe icon + host (`Uri.parse(uri).host?.removePrefix("www.")`,
 *   falling back to the full URI when the host is null for opaque inputs).
 */
@Composable
fun PostCardExternalEmbed(
    uri: String,
    title: String,
    description: String,
    thumbUrl: String?,
    onTap: (uri: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth().clickable { onTap(uri) },
    ) {
        Column {
            if (thumbUrl != null) {
                NubecitaAsyncImage(
                    model = thumbUrl,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(THUMB_ASPECT)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                ),
                            ),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (title.isNotBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = displayHost(uri),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private const val THUMB_ASPECT: Float = 1.91f

/**
 * Extracts a user-readable host from [uri]. Falls back to the full URI
 * when `Uri.parse(uri).host` is null (opaque or malformed inputs).
 *
 * `Uri` here is `android.net.Uri`, not the atproto-kotlin runtime `Uri`
 * — the composable takes a `String` to keep `:designsystem` free of
 * lib-type leakage.
 */
private fun displayHost(uri: String): String =
    Uri.parse(uri).host?.removePrefix("www.") ?: uri

@Preview(name = "External embed — with thumb", showBackground = true)
@Composable
private fun PostCardExternalEmbedWithThumbPreview() {
    NubecitaTheme {
        PostCardExternalEmbed(
            uri = PREVIEW_URI,
            title = PREVIEW_TITLE,
            description = PREVIEW_DESCRIPTION,
            thumbUrl = PREVIEW_THUMB,
            onTap = {},
        )
    }
}

@Preview(name = "External embed — no thumb", showBackground = true)
@Composable
private fun PostCardExternalEmbedNoThumbPreview() {
    NubecitaTheme {
        PostCardExternalEmbed(
            uri = PREVIEW_URI,
            title = PREVIEW_TITLE,
            description = PREVIEW_DESCRIPTION,
            thumbUrl = null,
            onTap = {},
        )
    }
}

private const val PREVIEW_URI: String = "https://www.theverge.com/tech/elon-altman-court-battle"
private const val PREVIEW_TITLE: String = "Elon Musk and Sam Altman's court battle over the future of OpenAI"
private const val PREVIEW_DESCRIPTION: String = "The billionaire battle goes to court."
private const val PREVIEW_THUMB: String = "https://example.com/preview-external-thumb.jpg"
```

- [ ] **Step 1.2: Verify it compiles**

Run: `./gradlew :designsystem:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 1.3: Verify previews render in Studio (manual visual check)**

Open `PostCardExternalEmbed.kt` in Android Studio and use the Compose preview pane to view both `External embed — with thumb` and `External embed — no thumb` in light + dark themes. The with-thumb preview shows the placeholder painter from `NubecitaAsyncImage` (preview tooling doesn't hit the network); the no-thumb preview shows a text-only card. Domain footer reads `theverge.com`.

- [ ] **Step 1.4: Commit**

```bash
git add designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCardExternalEmbed.kt
git commit -m "feat(designsystem): PostCardExternalEmbed leaf composable

Native Compose link-preview card for app.bsky.embed.external. Thumb
at 1.91:1 (OG standard), Crop scale, clipped to card's top corners.
Title (titleMedium, 2 lines), description (bodyMedium, 2 lines),
domain footer with globe icon. Empty strings skip their row.

Refs: nubecita-aku"
```

---

## Task 2: Add screenshot tests + generate baselines

**Files:**
- Create: `designsystem/src/screenshotTest/kotlin/net/kikin/nubecita/designsystem/component/PostCardExternalEmbedScreenshotTest.kt`
- Generates: `designsystem/src/screenshotTest/reference/...` (baseline PNGs)

- [ ] **Step 2.1: Write the screenshot test**

```kotlin
package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot baselines for [PostCardExternalEmbed] covering the two
 * render branches (with-thumb / no-thumb) across light + dark themes.
 *
 * The with-thumb shot uses the `NubecitaAsyncImage` placeholder painter
 * (preview tooling doesn't hit the network), so the baseline verifies
 * card geometry + text layout + corner clipping rather than the
 * thumbnail content itself.
 */

@PreviewTest
@Preview(name = "with-thumb-light", showBackground = true)
@Preview(name = "with-thumb-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardExternalEmbedWithThumbScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardExternalEmbed(
            uri = PREVIEW_URI,
            title = PREVIEW_TITLE,
            description = PREVIEW_DESCRIPTION,
            thumbUrl = PREVIEW_THUMB,
            onTap = {},
        )
    }
}

@PreviewTest
@Preview(name = "no-thumb-light", showBackground = true)
@Preview(name = "no-thumb-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardExternalEmbedNoThumbScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardExternalEmbed(
            uri = PREVIEW_URI,
            title = PREVIEW_TITLE,
            description = PREVIEW_DESCRIPTION,
            thumbUrl = null,
            onTap = {},
        )
    }
}

private const val PREVIEW_URI: String = "https://www.theverge.com/tech/elon-altman-court-battle"
private const val PREVIEW_TITLE: String = "Elon Musk and Sam Altman's court battle over the future of OpenAI"
private const val PREVIEW_DESCRIPTION: String = "The billionaire battle goes to court."
private const val PREVIEW_THUMB: String = "https://example.com/preview-external-thumb.jpg"
```

- [ ] **Step 2.2: Run validate to confirm baselines are missing**

Run: `./gradlew :designsystem:validateDebugScreenshotTest`
Expected: FAILS with "missing reference image" for the new test functions (this is the failing-test step in TDD).

- [ ] **Step 2.3: Generate baselines**

Run: `./gradlew :designsystem:updateDebugScreenshotTest`
Expected: BUILD SUCCESSFUL. Four new PNGs written under `designsystem/src/screenshotTest/reference/.../component/PostCardExternalEmbedScreenshotTestKt.PostCardExternalEmbedWithThumbScreenshot_*.png` (and `NoThumb_*`).

- [ ] **Step 2.4: Visually inspect each baseline PNG**

Open each of the four generated PNGs and verify:
- with-thumb-light: card has light surfaceContainer background, placeholder thumb at top, dark text below
- with-thumb-dark: same layout, dark surfaceContainer, light text
- no-thumb-light: text-only card, light surface, no whitespace where thumb would have been
- no-thumb-dark: same as no-thumb-light, dark theme

If anything looks wrong (alignment, spacing, theme), fix the composable and re-run `updateDebugScreenshotTest` before committing.

- [ ] **Step 2.5: Run validate to confirm baselines pass**

Run: `./gradlew :designsystem:validateDebugScreenshotTest`
Expected: PASS.

- [ ] **Step 2.6: Commit**

```bash
git add designsystem/src/screenshotTest/kotlin/net/kikin/nubecita/designsystem/component/PostCardExternalEmbedScreenshotTest.kt designsystem/src/screenshotTest/reference/
git commit -m "test(designsystem): screenshot baselines for PostCardExternalEmbed

Four shots: with-thumb / no-thumb x light / dark. Mirrors the
PostCardImageEmbedScreenshotTest pattern.

Refs: nubecita-aku"
```

---

## Task 3: Add `EmbedUi.External` variant + extend `PostCard.EmbedSlot` dispatch

**Files:**
- Modify: `data/models/src/main/kotlin/net/kikin/nubecita/data/models/EmbedUi.kt`
- Modify: `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCard.kt`

Adding `External` makes `PostCard.EmbedSlot`'s `when (embed)` non-exhaustive (compile error). Both files must change in the same commit to keep the build green.

- [ ] **Step 3.1: Add `External` variant to `EmbedUi.kt`**

In `data/models/src/main/kotlin/net/kikin/nubecita/data/models/EmbedUi.kt`, add the new variant between `Video` and `Unsupported`:

```kotlin
    /**
     * Bluesky `app.bsky.embed.external#view`.
     *
     * Native link-preview card. The atproto-kotlin lib already produces
     * fetchable URLs via `Uri.raw` for both `external.uri` and
     * `external.thumb`; no blob-ref → CDN URL construction is required
     * on the nubecita side.
     *
     * - [uri] is the linked URL (full, raw).
     * - [title] / [description] are non-null per the lexicon but Bluesky
     *   permits empty strings — the render layer skips empty rows.
     * - [thumbUrl] is null when the optional `thumb` field is absent;
     *   the render layer omits the thumb section entirely (text-only
     *   card, no placeholder).
     */
    public data class External(
        val uri: String,
        val title: String,
        val description: String,
        val thumbUrl: String?,
    ) : EmbedUi
```

Update the KDoc on `EmbedUi` (lines 14–28):

```kotlin
 * Currently supported variants:
 *
 * - [Empty] — post has no embed
 * - [Images] — `app.bsky.embed.images`, 1–4 images
 * - [Video] — `app.bsky.embed.video#view`, HLS-backed video post
 * - [External] — `app.bsky.embed.external#view`, native link-preview card
 * - [Unsupported] — any embed type outside the current scope (`#record`,
 *   `#recordWithMedia`); rendered as a deliberate "Unsupported embed"
 *   chip, NOT an error
 *
 * Future variants (one per follow-on bd ticket):
 * - `Record` (nubecita-6vq)
 * - `RecordWithMedia` (nubecita-umn)
```

- [ ] **Step 3.2: Confirm compile error in PostCard.EmbedSlot**

Run: `./gradlew :designsystem:compileDebugKotlin`
Expected: FAIL — `'when' expression must be exhaustive` on the `EmbedSlot` `when (embed)` block.

- [ ] **Step 3.3: Add the `EmbedUi.External` branch to `PostCard.EmbedSlot`**

In `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCard.kt`, update `EmbedSlot` (lines 202–227). Add the new branch, matching the `Images` pattern (10dp top spacer + leaf composable). The tap callback comes from the next task — for now wire it to a temporary no-op `{}`:

```kotlin
@Composable
private fun EmbedSlot(
    embed: EmbedUi,
    videoEmbedSlot: (@Composable (EmbedUi.Video) -> Unit)?,
) {
    when (embed) {
        EmbedUi.Empty -> Unit
        is EmbedUi.Images -> {
            Spacer(Modifier.height(10.dp))
            PostCardImageEmbed(items = embed.items)
        }
        is EmbedUi.Video -> {
            if (videoEmbedSlot != null) {
                Spacer(Modifier.height(10.dp))
                videoEmbedSlot(embed)
            }
        }
        is EmbedUi.External -> {
            Spacer(Modifier.height(10.dp))
            PostCardExternalEmbed(
                uri = embed.uri,
                title = embed.title,
                description = embed.description,
                thumbUrl = embed.thumbUrl,
                onTap = {},
            )
        }
        is EmbedUi.Unsupported -> {
            Spacer(Modifier.height(10.dp))
            PostCardUnsupportedEmbed(typeUri = embed.typeUri)
        }
    }
}
```

Update the KDoc on `PostCard` (lines 65–77):

```kotlin
 * **Supported embed types.**
 * - `EmbedUi.Empty` — no embed slot rendered
 * - `EmbedUi.Images` — 1–4 images via [PostCardImageEmbed]
 * - `EmbedUi.Video` — host-supplied via [videoEmbedSlot]
 * - `EmbedUi.External` — native link-preview card via [PostCardExternalEmbed]
 * - `EmbedUi.Unsupported` — deliberate-degradation chip via [PostCardUnsupportedEmbed]
 *
 * **Deferred embeds** (each tracked under its own bd ticket):
 * - quoted posts (record) — nubecita-6vq
 * - record-with-media — nubecita-umn
```

- [ ] **Step 3.4: Add a PostCard preview with an external embed**

In `PostCard.kt`, add a new `@Preview` after the existing `PostCardWithImagePreview` (around line 353):

```kotlin
@Preview(name = "PostCard — with external link card", showBackground = true)
@Composable
private fun PostCardWithExternalEmbedPreview() {
    NubecitaTheme {
        PostCard(
            post =
                previewPost(
                    embed =
                        EmbedUi.External(
                            uri = "https://www.theverge.com/tech/elon-altman-court-battle",
                            title = "Elon Musk and Sam Altman's court battle over the future of OpenAI",
                            description = "The billionaire battle goes to court.",
                            thumbUrl = "https://example.com/preview-external-thumb.jpg",
                        ),
                ),
        )
    }
}
```

- [ ] **Step 3.5: Verify compile passes**

Run: `./gradlew :designsystem:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3.6: Run all designsystem unit tests + screenshot validate**

Run: `./gradlew :designsystem:testDebugUnitTest :designsystem:validateDebugScreenshotTest`
Expected: PASS. The existing PostCard screenshot baselines should not have changed (the `External` branch is unreachable in the existing screenshots since `previewPost(...)` defaults to `EmbedUi.Empty`).

- [ ] **Step 3.7: Commit**

```bash
git add data/models/src/main/kotlin/net/kikin/nubecita/data/models/EmbedUi.kt designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCard.kt
git commit -m "feat(data,designsystem): EmbedUi.External + PostCard dispatch

Adds the External variant to the EmbedUi sealed type and wires
PostCard.EmbedSlot to render it via PostCardExternalEmbed. Tap
callback is a no-op placeholder; threaded through PostCallbacks
in the next commit.

Refs: nubecita-aku"
```

---

## Task 4: Add `PostCallbacks.onExternalEmbedTap` + thread through dispatch

**Files:**
- Modify: `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCallbacks.kt`
- Modify: `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCard.kt`

- [ ] **Step 4.1: Add `onExternalEmbedTap` field to PostCallbacks**

In `PostCallbacks.kt`, add the new callback (default no-op so existing call sites stay source-compatible):

```kotlin
@Stable
data class PostCallbacks(
    val onTap: (PostUi) -> Unit = {},
    val onAuthorTap: (AuthorUi) -> Unit = {},
    val onLike: (PostUi) -> Unit = {},
    val onRepost: (PostUi) -> Unit = {},
    val onReply: (PostUi) -> Unit = {},
    val onShare: (PostUi) -> Unit = {},
    val onExternalEmbedTap: (uri: String) -> Unit = {},
) {
```

- [ ] **Step 4.2: Thread the callback through `PostCard.EmbedSlot`**

Update `EmbedSlot` to accept `callbacks: PostCallbacks`:

```kotlin
@Composable
private fun EmbedSlot(
    embed: EmbedUi,
    callbacks: PostCallbacks,
    videoEmbedSlot: (@Composable (EmbedUi.Video) -> Unit)?,
) {
    when (embed) {
        EmbedUi.Empty -> Unit
        is EmbedUi.Images -> {
            Spacer(Modifier.height(10.dp))
            PostCardImageEmbed(items = embed.items)
        }
        is EmbedUi.Video -> {
            if (videoEmbedSlot != null) {
                Spacer(Modifier.height(10.dp))
                videoEmbedSlot(embed)
            }
        }
        is EmbedUi.External -> {
            Spacer(Modifier.height(10.dp))
            PostCardExternalEmbed(
                uri = embed.uri,
                title = embed.title,
                description = embed.description,
                thumbUrl = embed.thumbUrl,
                onTap = callbacks.onExternalEmbedTap,
            )
        }
        is EmbedUi.Unsupported -> {
            Spacer(Modifier.height(10.dp))
            PostCardUnsupportedEmbed(typeUri = embed.typeUri)
        }
    }
}
```

Update the call site in `PostCard` (around line 121) to pass `callbacks`:

```kotlin
                    EmbedSlot(
                        embed = post.embed,
                        callbacks = callbacks,
                        videoEmbedSlot = videoEmbedSlot,
                    )
```

- [ ] **Step 4.3: Verify compile passes**

Run: `./gradlew :designsystem:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4.4: Run designsystem tests**

Run: `./gradlew :designsystem:testDebugUnitTest :designsystem:validateDebugScreenshotTest`
Expected: PASS.

- [ ] **Step 4.5: Commit**

```bash
git add designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCallbacks.kt designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCard.kt
git commit -m "feat(designsystem): thread onExternalEmbedTap through PostCard

PostCallbacks gains onExternalEmbedTap (default no-op for source
compat). PostCard.EmbedSlot routes the External branch's onTap to
the callback so screens can wire it to a CustomTabsIntent launcher.

Refs: nubecita-aku"
```

---

## Task 5: Mapper change — `ExternalView` → `EmbedUi.External` + tests

**Files:**
- Modify: `feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/data/FeedViewPostMapper.kt`
- Modify: `feature/feed/impl/src/test/kotlin/net/kikin/nubecita/feature/feed/impl/data/FeedViewPostMapperTest.kt`

The existing test at lines 70–75 asserts the OLD behavior (`Unsupported`). It must be replaced with the new contract.

- [ ] **Step 5.1: Replace the old "external → Unsupported" test with three new tests**

In `FeedViewPostMapperTest.kt`, replace the existing test (lines 70–75):

```kotlin
    @Test
    fun `external embed maps to EmbedUi_Unsupported with the lexicon URI`() {
        val response = decodeFixture("timeline_with_external_embed.json")
        val mapped = response.feed.single().toPostUiOrNull()
        assertNotNull(mapped)
        assertEquals(EmbedUi.Unsupported(typeUri = "app.bsky.embed.external"), mapped!!.embed)
    }
```

with these three:

```kotlin
    @Test
    fun `external embed maps to EmbedUi_External with uri title description and thumbUrl`() {
        val response = decodeFixture("timeline_with_external_embed.json")
        val mapped = response.feed.single().toPostUiOrNull()
        assertNotNull(mapped)
        val external = mapped!!.embed as EmbedUi.External
        assertEquals("https://davidimel.substack.com/p/bluesky-is-looking-for-its-myspace", external.uri)
        assertEquals("Bluesky is doubling down", external.title)
        assertEquals("Let's see if a feed-builder is it.", external.description)
        // The thumb is a server-side preview-card URL produced by the appview;
        // existence is what the assertion checks (the exact URL shape is
        // appview-internal and may evolve).
        assertNotNull(external.thumbUrl)
    }

    @Test
    fun `external embed without thumb maps to EmbedUi_External with thumbUrl null`() {
        // Synthetic external view that omits the optional `thumb` field.
        // The mapper must still produce EmbedUi.External; the render layer
        // omits the thumb section entirely (text-only card).
        val noThumbJson =
            """
            {
              "feed": [{
                "post": {
                  "uri": "at://did:plc:fake000000000000000000/app.bsky.feed.post/no-thumb",
                  "cid": "bafyreifakecid000000000000000000000000000000000",
                  "author": {
                    "did": "did:plc:fake000000000000000000",
                    "handle": "fake.bsky.social"
                  },
                  "indexedAt": "2026-04-26T12:00:00Z",
                  "record": {
                    "${'$'}type": "app.bsky.feed.post",
                    "text": "external without a thumb",
                    "createdAt": "2026-04-26T12:00:00Z"
                  },
                  "embed": {
                    "${'$'}type": "app.bsky.embed.external#view",
                    "external": {
                      "uri": "https://example.com/article",
                      "title": "Article without a thumbnail",
                      "description": "A short description."
                    }
                  }
                }
              }]
            }
            """.trimIndent()
        val response = json.decodeFromString(GetTimelineResponse.serializer(), noThumbJson)
        val mapped = response.feed.single().toPostUiOrNull()
        assertNotNull(mapped)
        val external = mapped!!.embed as EmbedUi.External
        assertEquals("https://example.com/article", external.uri)
        assertEquals("Article without a thumbnail", external.title)
        assertNull(external.thumbUrl)
    }

    @Test
    fun `external embed with empty title and description still maps to EmbedUi_External`() {
        // The lexicon types title and description as non-null String, but
        // Bluesky permits empty strings (e.g. when the OG scraper finds
        // nothing). The mapper must pass them through; the render layer
        // skips empty rows rather than treating empty as Unsupported.
        val emptyFieldsJson =
            """
            {
              "feed": [{
                "post": {
                  "uri": "at://did:plc:fake000000000000000000/app.bsky.feed.post/empty-fields",
                  "cid": "bafyreifakecid000000000000000000000000000000000",
                  "author": {
                    "did": "did:plc:fake000000000000000000",
                    "handle": "fake.bsky.social"
                  },
                  "indexedAt": "2026-04-26T12:00:00Z",
                  "record": {
                    "${'$'}type": "app.bsky.feed.post",
                    "text": "external with empty title/description",
                    "createdAt": "2026-04-26T12:00:00Z"
                  },
                  "embed": {
                    "${'$'}type": "app.bsky.embed.external#view",
                    "external": {
                      "uri": "https://example.com/no-metadata",
                      "title": "",
                      "description": ""
                    }
                  }
                }
              }]
            }
            """.trimIndent()
        val response = json.decodeFromString(GetTimelineResponse.serializer(), emptyFieldsJson)
        val mapped = response.feed.single().toPostUiOrNull()
        assertNotNull(mapped)
        val external = mapped!!.embed as EmbedUi.External
        assertEquals("", external.title)
        assertEquals("", external.description)
    }
```

- [ ] **Step 5.2: Run the failing tests**

Run: `./gradlew :feature:feed:impl:testDebugUnitTest --tests '*FeedViewPostMapperTest*' --info`
Expected: 3 NEW external tests FAIL with `ClassCastException` casting `EmbedUi.Unsupported` to `EmbedUi.External` (the mapper still maps to Unsupported).

- [ ] **Step 5.3: Update the mapper**

In `FeedViewPostMapper.kt`, replace the `ExternalView` branch (line 109):

```kotlin
        is ExternalView ->
            EmbedUi.External(
                uri = external.uri.raw,
                title = external.title,
                description = external.description,
                thumbUrl = external.thumb?.raw,
            )
```

Update the KDoc on `toEmbedUi()` to drop the External-deferred line:

```kotlin
/**
 * Maps the [PostViewEmbedUnion] open-union variant to PostCard's
 * [EmbedUi] surface (Empty / Images / Video / External / Unsupported).
 * Future `EmbedUi` variants (Record per nubecita-6vq, RecordWithMedia
 * per nubecita-umn) become compile errors at this `when` once they're
 * added to `EmbedUi`, surfacing the work needed.
 */
```

- [ ] **Step 5.4: Run the tests**

Run: `./gradlew :feature:feed:impl:testDebugUnitTest --tests '*FeedViewPostMapperTest*'`
Expected: PASS.

- [ ] **Step 5.5: Commit**

```bash
git add feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/data/FeedViewPostMapper.kt feature/feed/impl/src/test/kotlin/net/kikin/nubecita/feature/feed/impl/data/FeedViewPostMapperTest.kt
git commit -m "feat(feed): map ExternalView to EmbedUi.External

ExternalView no longer falls through to Unsupported; the mapper
now produces EmbedUi.External with uri/title/description/thumbUrl
extracted from the lexicon-typed view. Three tests cover
with-thumb, no-thumb, and empty-string title/description cases.

Refs: nubecita-aku"
```

---

## Task 6: Wire `FeedScreen` Custom Tabs launcher

**Files:**
- Modify: `feature/feed/impl/build.gradle.kts`
- Modify: `feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/FeedScreen.kt`

`androidx.browser` is currently only declared in `:feature:login:impl`. We need it on `:feature:feed:impl` too.

- [ ] **Step 6.1: Add `androidx.browser` to `:feature:feed:impl`**

In `feature/feed/impl/build.gradle.kts`, add the dep alongside other `implementation(libs.androidx....)` lines:

```kotlin
    implementation(libs.androidx.browser)
```

- [ ] **Step 6.2: Verify the dep resolves**

Run: `./gradlew :feature:feed:impl:dependencies --configuration debugCompileClasspath | grep androidx.browser`
Expected: a line containing `androidx.browser:browser:<version>`.

- [ ] **Step 6.3: Wire the callback in `FeedScreen`**

In `feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/FeedScreen.kt`, add imports near the top:

```kotlin
import android.net.Uri as AndroidUri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.platform.LocalContext
```

(The `as AndroidUri` alias avoids any clash if a `Uri` from atproto-kotlin is imported elsewhere in the file; verify no clash by inspection before keeping the alias.)

In the `FeedScreen` composable body (around line 80), pull `LocalContext` once and add the `onExternalEmbedTap` to the existing `remember(viewModel)` `PostCallbacks` block. Since the callback closes over `context`, expand the `remember` key to `remember(viewModel, context)`:

```kotlin
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val viewState = remember(state) { state.toViewState() }
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val callbacks =
        remember(viewModel, context) {
            PostCallbacks(
                onTap = { viewModel.handleEvent(FeedEvent.OnPostTapped(it)) },
                onAuthorTap = { viewModel.handleEvent(FeedEvent.OnAuthorTapped(it.did)) },
                onLike = { viewModel.handleEvent(FeedEvent.OnLikeClicked(it)) },
                onRepost = { viewModel.handleEvent(FeedEvent.OnRepostClicked(it)) },
                onReply = { viewModel.handleEvent(FeedEvent.OnReplyClicked(it)) },
                onShare = { viewModel.handleEvent(FeedEvent.OnShareClicked(it)) },
                onExternalEmbedTap = { uri ->
                    runCatching {
                        CustomTabsIntent
                            .Builder()
                            .setShowTitle(true)
                            .build()
                            .launchUrl(context, AndroidUri.parse(uri))
                    }
                },
            )
        }
```

The `runCatching` swallows `ActivityNotFoundException` (no browser installed) silently per the design's v1 contract.

- [ ] **Step 6.4: Verify compile passes**

Run: `./gradlew :feature:feed:impl:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6.5: Run feed-impl unit tests**

Run: `./gradlew :feature:feed:impl:testDebugUnitTest`
Expected: PASS — no behavioral test for FeedScreen wiring exists; the mapper tests from Task 5 still pass.

- [ ] **Step 6.6: Commit**

```bash
git add feature/feed/impl/build.gradle.kts feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/FeedScreen.kt
git commit -m "feat(feed): launch CustomTabs on external embed tap

FeedScreen wires PostCallbacks.onExternalEmbedTap to a
CustomTabsIntent launcher. Adds the androidx.browser dep to
:feature:feed:impl. Failure path (no browser installed) is a
silent no-op.

Refs: nubecita-aku"
```

---

## Task 7: Manual smoke test + flip PR to ready-for-review

**Files:** none (verification only)

- [ ] **Step 7.1: Build the debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7.2: Install + launch on an emulator or device**

Run: `./gradlew :app:installDebug`, then launch the app.
- Sign in if needed.
- Scroll the feed until an external link card appears (Bluesky's official feed has them frequently — search for posts from `@theverge.com`, `@nytimes.com`, or `@bsky.app`).

- [ ] **Step 7.3: Verify visual rendering**

For at least one with-thumb card and one no-thumb card (if visible):
- Title is non-truncated for short titles, ellipsized at 2 lines for long
- Description likewise
- Domain footer reads the host without `www.` prefix
- Card surface tone is distinct from the post body background
- 120 Hz scroll: scroll a screenful of cards rapidly; no perceivable jank
- Repeat in light + dark mode (toggle via system settings)

- [ ] **Step 7.4: Verify tap behavior**

- Tap any external link card.
- Custom Tab opens with the article URL.
- Toolbar shows the page title.
- Hardware back closes the tab and returns to the feed with scroll position intact (no app task switch).

- [ ] **Step 7.5: Run the full pre-commit / quality gate**

Run: `./gradlew spotlessCheck lint testDebugUnitTest :designsystem:validateDebugScreenshotTest`
Expected: ALL PASS.

- [ ] **Step 7.6: Push and flip the PR to ready-for-review**

Use the gh-https credential-helper pattern (SSH agent unavailable on remote sessions):

```bash
git -c credential.helper= -c credential.helper='!gh auth git-credential' push https://github.com/kikin81/nubecita.git feat/nubecita-aku-postcard-external-link-card-embed-app-bsky-embed-e:feat/nubecita-aku-postcard-external-link-card-embed-app-bsky-embed-e
gh pr ready 61
```

Then re-read the PR body and update the test plan checkboxes for items now complete (mapper tests, screenshot tests, spotlessCheck/lint, manual smoke).

bd issue closure happens AFTER the PR merges — `bd close nubecita-aku` post-merge, not now.
