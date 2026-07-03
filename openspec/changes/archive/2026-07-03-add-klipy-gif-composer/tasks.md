<!-- Task groups map 1:1 to the beads children of epic nubecita-50ge. -->

## 1. `:core:klipy` scaffold — bd `nubecita-gotc`

- [x] 1.1 Create the `:core:klipy` module (`nubecita.android.library` + `nubecita.android.hilt`, consumer-rules.pro) and a dedicated Ktor `HttpClient` (kotlinx.serialization JSON) with the `Logging` plugin sanitizing/omitting the URL path (path-embedded key).
- [x] 1.2 Add the KLIPY API key as a Gradle secret → `BuildConfig`; base URL `https://api.klipy.com/api/v1/{key}/` treated as secret.
- [x] 1.3 Define `@Serializable` DTOs + a polymorphic deserializer keyed on `type` (general/ad), and a `KlipyMediaUi` `:data:models` type + mapper that retains the embed source (gif URL + `hh`/`ww` + mp4/webp slugs) and a light grid rendition.
- [x] 1.4 Define the `KlipyRepository` interface (returns `:data:models` types only) and a stable `customer_id` DataStore.

## 2. Production repository + paging — bd `nubecita-ubzz`

- [x] 2.1 Implement `search` / `trending` / `categories` / `recents` against the KLIPY endpoints (Recents+Trending lead the category list; blank query → trending).
- [x] 2.2 Add a `KlipyPagingSource` (Paging 3) with reset-on-query/tab/category change.

## 3. Interaction tracking — bd `nubecita-3hu9`

- [x] 3.1 Implement `trackView` / `trackShare` / `report` / `hideRecent` on an injected `@ApplicationScope` coroutine (survives composer teardown), failures swallowed.

## 4. Bench fake + DI — bd `nubecita-srx0`

- [x] 4.1 `BenchFakeKlipyRepository` with canned offline data (no key/network).
- [x] 4.2 Bench + production Hilt modules binding `KlipyRepository`.

## 5. Picker UI — bd `nubecita-m1xp`

- [x] 5.1 `KlipyPickerViewModel` (`MviViewModel`): ~300ms-debounced search, GIF/Sticker tabs, categories, Paging flow, preview/report/hide handlers.
- [x] 5.2 Picker composable: `ModalBottomSheet` on compact / `Popup`-over-`Surface` on medium/expanded (matching `AudiencePicker`/`LanguagePicker`); "Search KLIPY" search; `LazyVerticalStaggeredGrid` cells playing light webp via one shared Coil `ImageLoader`; `blur_preview` placeholders.
- [x] 5.3 Long-press preview (fires `trackView`) + Report action; persistent "Powered by KLIPY" mark.

## 6. Composer integration + posting + tests — bd `nubecita-6sxt`

- [x] 6.1 GIF button in `ComposerOptionsChipRow` opening the picker; mutual exclusivity with photo attachments (disable each when the other is present).
- [x] 6.2 Pending-GIF-embed composer state; on select fire `trackShare`; render the preview via `PostCardGifEmbed` with a remove ✕.
- [x] 6.3 `ComposerEmbedIntent.Gif` folded into `:core:posting`'s existing `app.bsky.embed.external` builder — write the `static.klipy.com/ii/…?hh=&ww=&mp4=` URI + upload the preview as `external.thumb`.
- [x] 6.4 Unit tests: DTO→`KlipyMediaUi` mapper, polymorphic deserializer, embed-URI builder with an assertion that `isGifExternalUri` accepts the emitted URI (produce↔consume contract), mutual-exclusivity reducer.
- [x] 6.5 Screenshot tests (picker sheet + composer GIF preview) + `en/es/pt` strings for picker/branding.
