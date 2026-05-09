package net.kikin.nubecita.designsystem.preview

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview

/**
 * Multi-preview that sweeps the three Material 3 width-size-class buckets
 * Nubecita ships against (Compact / Medium / Expanded) × Light / Dark.
 *
 * Use on full-screen render-state fixtures where width-class branching
 * (FAB sizing, list-detail panes, adaptive containers) needs regression
 * coverage at every bucket. Don't use on standalone components — write
 * `@Preview` directly with the one device spec you need.
 *
 * Nubecita-specific subset of `androidx.compose.ui.tooling.preview.PreviewScreenSizes`:
 * drops Phone-Landscape, Tablet-Landscape, and Desktop (we don't target
 * ChromeOS), keeps the Foldable bucket because that's where width-class
 * regressions hide.
 *
 * Bundles Light / Dark inside the annotation rather than asking callers
 * to stack `@PreviewLightDark`. Compose tooling concatenates multi-preview
 * declarations rather than computing their cross-product, so a stacked
 * `@PreviewLightDark` + screen-size annotation yields 5 fixtures (3 sizes
 * light + 2 themes default-device), not the 6 (3 × 2) callers would
 * intuit. Bundling here makes the cross-product explicit and uniform.
 *
 * Name double-suffixes (`Preview…Previews`) to satisfy two contradictory
 * lint rules active in the project: ktlint's
 * `compose:preview-annotation-naming` requires a `Preview` prefix
 * (matching `androidx.compose.ui.tooling.preview.PreviewScreenSizes` etc.),
 * while the Slack `compose-lints` `ComposePreviewNaming` rule requires a
 * literal `Previews` plural suffix. The stutter is intentional.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(name = "Phone Light", device = Devices.PHONE, showSystemUi = true)
@Preview(
    name = "Phone Dark",
    device = Devices.PHONE,
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(name = "Foldable Light", device = Devices.FOLDABLE, showSystemUi = true)
@Preview(
    name = "Foldable Dark",
    device = Devices.FOLDABLE,
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(name = "Tablet Light", device = Devices.TABLET, showSystemUi = true)
@Preview(
    name = "Tablet Dark",
    device = Devices.TABLET,
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
annotation class PreviewNubecitaScreenPreviews
