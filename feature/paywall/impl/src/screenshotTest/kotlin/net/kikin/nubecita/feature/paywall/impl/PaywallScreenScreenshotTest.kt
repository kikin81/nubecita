package net.kikin.nubecita.feature.paywall.impl

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.data.models.SubscriptionOfferingFixtures
import net.kikin.nubecita.data.models.SubscriptionPlanId
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme

/**
 * Screenshot baselines for the paywall body ([PaywallContent]).
 *
 * Four fixtures × Light/Dark = 8 baselines, covering the spec's
 * "light/dark × selected plan × loading × error" matrix:
 *
 * - **ready-annual** — offering loaded, annual selected (the default):
 *   savings badge + per-month-equivalent on the annual card, primary
 *   border on the selected card.
 * - **ready-monthly** — offering loaded, monthly selected: the highlight
 *   moves to the monthly card; the annual card keeps its savings badge.
 * - **loading** — centered progress indicator while the offering loads.
 * - **error** — retryable load-failure state (the path a keyless local /
 *   bench build always lands on, since `loadPlans()` returns failure there).
 *
 * Prices come from [SubscriptionOfferingFixtures.proOffering] (the D9
 * anchors: $1.99/mo, $19.99/yr ⇒ 16% off) so baselines are deterministic
 * and independent of any live provider response. The snackbar host and the
 * purchase/restore spinners are runtime-only and aren't pinned here.
 */
@PreviewTest
@Preview(name = "paywall-ready-annual-light", showBackground = true, heightDp = 920)
@Preview(
    name = "paywall-ready-annual-dark",
    showBackground = true,
    heightDp = 920,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PaywallReadyAnnualScreenshot() {
    NubecitaCanvasPreviewTheme {
        PaywallContent(
            state =
                PaywallState(
                    status = PaywallStatus.Ready(SubscriptionOfferingFixtures.proOffering()),
                    selectedPlan = SubscriptionPlanId.Annual,
                ),
            onClose = {},
            onRetry = {},
            onPlanSelect = {},
            onPurchase = {},
            onRestore = {},
            onTerms = {},
            onPrivacy = {},
        )
    }
}

@PreviewTest
@Preview(name = "paywall-ready-monthly-light", showBackground = true, heightDp = 920)
@Preview(
    name = "paywall-ready-monthly-dark",
    showBackground = true,
    heightDp = 920,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PaywallReadyMonthlyScreenshot() {
    NubecitaCanvasPreviewTheme {
        PaywallContent(
            state =
                PaywallState(
                    status = PaywallStatus.Ready(SubscriptionOfferingFixtures.proOffering()),
                    selectedPlan = SubscriptionPlanId.Monthly,
                ),
            onClose = {},
            onRetry = {},
            onPlanSelect = {},
            onPurchase = {},
            onRestore = {},
            onTerms = {},
            onPrivacy = {},
        )
    }
}

@PreviewTest
@Preview(name = "paywall-loading-light", showBackground = true, heightDp = 720)
@Preview(
    name = "paywall-loading-dark",
    showBackground = true,
    heightDp = 720,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PaywallLoadingScreenshot() {
    NubecitaCanvasPreviewTheme {
        PaywallContent(
            state = PaywallState(status = PaywallStatus.Loading),
            onClose = {},
            onRetry = {},
            onPlanSelect = {},
            onPurchase = {},
            onRestore = {},
            onTerms = {},
            onPrivacy = {},
        )
    }
}

@PreviewTest
@Preview(name = "paywall-error-light", showBackground = true, heightDp = 720)
@Preview(
    name = "paywall-error-dark",
    showBackground = true,
    heightDp = 720,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PaywallErrorScreenshot() {
    NubecitaCanvasPreviewTheme {
        PaywallContent(
            state = PaywallState(status = PaywallStatus.Error),
            onClose = {},
            onRetry = {},
            onPlanSelect = {},
            onPurchase = {},
            onRestore = {},
            onTerms = {},
            onPrivacy = {},
        )
    }
}
