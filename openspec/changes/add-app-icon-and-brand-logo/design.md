## Context

The reference design system that lives under `openspec/references/design-system/` includes brand assets (`app-icon.svg`, `logomark.svg`, `logomark-mono.svg`, `logo.svg`) and a preview HTML (`preview/brand-app-icon.html`) describing the intended treatment as "Android adaptive icon — Foreground + background layers, safe zone respected. Background is brand sky `#0A7AFF`."

The current `:app` ships unmodified Android Studio template assets:
- `res/drawable/ic_launcher_foreground.xml` — the green android-droid robot.
- `res/drawable/ic_launcher_background.xml` — green gridded background.
- `res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher*.webp` — dpi-bucketed PNGs (10 files total: `ic_launcher.webp` + `ic_launcher_round.webp` per bucket).
- `res/mipmap-anydpi-v26/ic_launcher.xml` — adaptive icon manifest with `<background>`, `<foreground>`, **and `<monochrome>`** slots already wired, all pointing at the foreground/background drawables above.

The `:app` `minSdk = 28` (per `build-logic`'s `nubecita.android.application` plugin), so every device renders the adaptive icon path. The dpi-bucketed webp's never reach a screen.

`MainActivity.kt:41` calls `androidx.core.splashscreen.SplashScreen.installSplashScreen()`, but `Theme.Nubecita` is parented to `android:Theme.Material.Light.NoActionBar` — not `Theme.SplashScreen` — so none of the `windowSplashScreen*` attrs resolve. The SplashScreen API falls back to platform defaults (white background, default app icon).

The `:designsystem` module ships components (`PostCard`, `NubecitaPrimaryButton`, etc.) backed by Compose drawables and Material 3 theme tokens. It does NOT currently ship any brand-mark composable; `LoginScreen` opens with a plain `Text` title and `Splash` renders `Box(Modifier.fillMaxSize())`.

## Goals

- Replace the placeholder launcher icon with the brand cloud, configured for the full adaptive-icon contract (background, foreground, monochrome layers).
- Make the system splash render the brand cloud on `#0A7AFF` instead of platform defaults.
- Ship the brand mark as a reusable `:designsystem` composable so future surfaces (login, splash, settings about-screen, share previews) all draw from the same source.
- Use the new composable on the `Splash` route and the `LoginScreen` for a consistent cold-start identity.

## Non-Goals

- Porting `cloud-illustration.svg` and `empty-cloud.svg` (the larger illustrations) — defer to a follow-up.
- Porting the `compose/icons/*.svg` set under `openspec/references/design-system/compose/icons/` — separate task.
- Replacing the system SplashScreen API with a Compose splash — `installSplashScreen()` flow stays.
- Animating the launcher icon (no `windowSplashScreenAnimationDuration` / `windowSplashScreenAnimatedIcon` animated-vector — static cloud only for v1).

## Decisions

### Decision 1: Drop the sky-band overlay from the adaptive port

**Context:** `app-icon.svg` includes a decorative semi-transparent `#3F92FF` rounded rect spanning the top of the background — when rendered in a static viewer it produces a gentle "sky band" gradient effect.

**Decision:** Drop the band. Background is solid `#0A7AFF`.

**Rationale:** Adaptive icons get parallaxed and scaled per-layer by launchers (Pixel Launcher's icon-tilt animation, Material You's color-shift animations, Samsung's third-party launcher zoom). The foreground and background layers move independently. A fixed band on only the background drifts away from the cloud during that motion, which looks visually broken. Solid `#0A7AFF` is faithful to brand color and preserves correct motion across all launchers.

The reference SVG was designed for a static end-state (e.g. marketing materials, web screenshots) where the band reads as intentional. Adaptive rendering is a different surface.

### Decision 2: Delete the legacy mipmap webp's

**Context:** Android Studio's icon-export wizard emits both adaptive (`mipmap-anydpi-v26/`) and per-dpi raster (`mipmap-*dpi/`) variants. The raster variants are the pre-API-26 fallback; on API 26+ the adaptive icon is used instead.

**Decision:** Delete all 10 dpi-bucketed `ic_launcher*.webp` files.

**Rationale:** `:app` `minSdk = 28`. The raster variants are guaranteed-dead code that wastes ~10KB of APK and adds 10 files of cognitive load to the resource tree. Lint will warn that mipmap-anydpi-v26 isn't backed by a fallback; suppress with `tools:ignore="MissingDefaultResource"` on the adaptive-icon `<adaptive-icon>` root, or accept the warning (it's not an error).

### Decision 3: Wordmark is paths, not text

**Context:** The wordmark in `logo.svg` is rendered via SVG `<text>` and a class-styled font (`class="nb-wordmark"`). Reproducing this in an Android vector drawable would either require:
1. Embedding the wordmark text as `<path>` elements (paths-from-glyphs), OR
2. Composing in Kotlin: `Row { NubecitaLogomark(); Text("nubecita", style = ...) }` and wiring the font.

**Decision:** Option 1 — bake the wordmark as paths in the vector drawable.

**Rationale:** A logo composable backed by a single `Image(painter = ...)` has predictable size + tinting semantics. Mixing in a `Text` couples the logo's rendered metrics to font availability and theme changes — a font that fails to load (or a theme that swaps the font family) would break the logo. The wordmark is a fixed mark, not text — treating it as text invites accidental restyling.

The cost is a one-time SVG → vector-drawable conversion that bakes a specific font's "nubecita" glyphs into paths. Future wordmark redesigns require a fresh export. Acceptable for a brand asset.

### Decision 4: Single `colors.xml` source for `#0A7AFF`

**Context:** The `#0A7AFF` brand sky color appears in `app-icon.svg`, the future `ic_launcher_background.xml`, the future `windowSplashScreenBackground` theme attr, and (eventually) anywhere else the brand wants the sky surface (about-screen header, share-card backgrounds).

**Decision:** Define `@color/brand_sky_blue = "#FF0A7AFF"` once in `app/src/main/res/values/colors.xml`. The launcher background drawable references it via `<color android:color="@color/brand_sky_blue"/>` (or via a `<solid>` shape); the splash-screen theme attr references it via `@color/brand_sky_blue`.

**Rationale:** Standard Android resource hygiene — never repeat a hex literal across resource files. Future redesigns flip one value.

The `:designsystem` module already exposes `MaterialTheme.colorScheme.primary` which currently resolves to brand sky in the static (non-dynamic-color) palette per the `add-designsystem-theme` change. The launcher icon and splash theme can NOT reference Compose theme tokens (they're XML resources resolved before any Compose composition runs), hence the duplicate `@color` resource. The two sources represent the same concept; the duplication is a structural necessity, not a design smell.

### Decision 5: `NubecitaLogo` and `NubecitaLogomark` are separate composables

**Context:** The reference ships two related marks: the cloud-only logomark (square) and the cloud + wordmark logo (wide).

**Decision:** Two composables, two vector drawables. Not one composable with a `withWordmark: Boolean` flag.

**Rationale:** The two marks have different aspect ratios (1:1 vs ~3.3:1) and different intrinsic sizes. A single composable with a flag would couple them at the API boundary and force every caller to pass an aspect-aware modifier. Separate composables let each carry its own intrinsic size and let the type system distinguish "I want just the mark" from "I want mark + name."

Both composables share the same tint contract: a `tint`/`color` parameter defaulted to `MaterialTheme.colorScheme.primary`, applied via `ColorFilter.tint(...)` over an `Image(painter = painterResource(...))`. The vector drawables use `android:fillColor="#FFFFFFFF"` as the base color — `ColorFilter.tint` then recolors to the requested tint.

## Open Questions

### Q1: Which font supplies the "nubecita" wordmark glyphs?

The reference `logo.svg` renders the wordmark as `<text class="nb-wordmark">nubecita</text>`, but `nb-wordmark` is **not defined** in `openspec/references/design-system/colors_and_type.css`. The reference design system declares three font families (`Fraunces` — display; `Roboto Flex` — body; `JetBrains Mono` — mono) but does not pin one to the wordmark.

Decision 3 (wordmark as paths) cannot be executed until this is resolved. Three candidate resolutions:

1. **Fraunces (display)** — recommended. Already the brand display font, expressive serif suits a logo wordmark, SIL OFL license permits glyph-as-path embedding.
2. **Roboto Flex (body)** — also SIL OFL; reads more "UI" than "brand" but consistent with the body type.
3. **Compose Text inline** — reverses Decision 3. Render `Row { NubecitaLogomark(); Text("nubecita", style = ...) }` instead of a vector drawable. Trades hermetic rendering for simpler integration.

Implementer's task 0.1 records the resolution before any vector-drawable conversion happens.

## Risks / Trade-offs

- **Launcher icon caches**: Most Android launchers cache icon assets and only refresh on package install/replace. Existing devs running this branch over an older install may see the old green-droid until they reinstall — not a user-visible bug (production users get the new icon on first install) but worth knowing during dev.
- **Themed-icon fidelity**: Android 13+ themed icons recolor the monochrome layer using the user's accent color. Our cloud has no internal detail (it's a single silhouette shape), so it'll always render as a flat tinted blob in themed mode. Acceptable — most app icons that opt into themed look this way.
- **System splash icon size mismatch**: `windowSplashScreenAnimatedIcon` expects a 288dp drawable on API 31+, with the inner 192dp being the "icon" zone. Our `ic_launcher_foreground.xml` is 108dp (adaptive icon size). The system splash will scale it; the cloud may look slightly smaller than other apps' splash icons. If this looks off in practice, a follow-up can ship a dedicated `splash_icon.xml` at the splash-screen size.
- **Wordmark glyph rights**: We control `logo.svg`; assumed the wordmark glyphs are either an open font or our own work. If the source font has a restrictive license that prohibits glyph-as-path embedding, this needs a license review. (Most modern type foundries permit it; flag if the SVG was sourced from a third party.)

## Migration Plan

Single PR. No flag. No phased rollout. Visible on first install + first cold-start on upgrade (subject to the launcher cache caveat above).
