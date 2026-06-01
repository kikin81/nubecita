package net.kikin.nubecita.feature.paywall.impl

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.kikin.nubecita.data.models.SubscriptionPlanId
import net.kikin.nubecita.designsystem.component.NubecitaPrimaryButton
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.spacing
import net.kikin.nubecita.feature.paywall.impl.ui.PaywallPerkRow
import net.kikin.nubecita.feature.paywall.impl.ui.PaywallPlanPicker

/**
 * Stateful Nubecita Pro paywall. Owns the [PaywallViewModel], the effect
 * collector, and the snackbar host; delegates rendering to [PaywallContent]
 * so previews and screenshot tests can drive every status with fixture
 * inputs.
 *
 * The purchase CTA needs the hosting Activity (design D5: the Composable
 * supplies it, the VM never holds one). `LocalActivity.current` is non-null
 * inside `MainShell`; the `?.let` guard keeps the tap a silent no-op in the
 * pathological null case rather than crashing.
 *
 * [onDismiss] pops the paywall off the inner back stack. It's invoked by
 * the close affordance and by the [PaywallEffect.Dismiss] effect (a
 * completed purchase / a restore that grants Pro).
 */
@Composable
internal fun PaywallScreen(
    onDismiss: () -> Unit,
    onPurchaseSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = LocalActivity.current

    val purchaseErrorMsg = stringResource(R.string.paywall_purchase_error)
    val restoreErrorMsg = stringResource(R.string.paywall_restore_error)
    val nothingToRestoreMsg = stringResource(R.string.paywall_nothing_to_restore)

    // The effect collector lives in a restarting LaunchedEffect; capture the
    // nav callbacks via rememberUpdatedState so an effect always invokes the
    // current callback (ktlint compose:lambda-param-in-effect).
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnPurchaseSuccess by rememberUpdatedState(onPurchaseSuccess)

    LaunchedEffect(Unit) {
        // Capture the effect scope so each snackbar runs in its own child job
        // (same rationale as :feature:settings — a dismiss-interrupted
        // showSnackbar must not tear down the whole effect collector).
        val effectScope = this
        viewModel.effects.collect { effect ->
            when (effect) {
                PaywallEffect.Dismiss -> currentOnDismiss()
                PaywallEffect.PurchaseSucceeded -> currentOnPurchaseSuccess()
                PaywallEffect.ShowPurchaseError ->
                    effectScope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(purchaseErrorMsg)
                    }
                PaywallEffect.ShowRestoreError ->
                    effectScope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(restoreErrorMsg)
                    }
                PaywallEffect.ShowNothingToRestore ->
                    effectScope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(nothingToRestoreMsg)
                    }
                is PaywallEffect.LaunchUri ->
                    // Chrome Custom Tab when available; narrow catch swallows
                    // only the "no CCT-capable browser" case (matches Settings).
                    try {
                        CustomTabsIntent
                            .Builder()
                            .setShowTitle(true)
                            .build()
                            .launchUrl(context, Uri.parse(effect.uri))
                    } catch (_: ActivityNotFoundException) {
                        // No browser available — silent no-op.
                    }
            }
        }
    }

    PaywallContent(
        state = state,
        onClose = onDismiss,
        onRetry = { viewModel.handleEvent(PaywallEvent.Retry) },
        onPlanSelect = { viewModel.handleEvent(PaywallEvent.PlanSelected(it)) },
        onPurchase = { activity?.let { viewModel.handleEvent(PaywallEvent.PurchaseClicked(it)) } },
        onRestore = { viewModel.handleEvent(PaywallEvent.RestoreClicked) },
        onTerms = { viewModel.handleEvent(PaywallEvent.TermsClicked) },
        onPrivacy = { viewModel.handleEvent(PaywallEvent.PrivacyClicked) },
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/**
 * Stateless paywall body. Branches on [PaywallStatus]: a centered spinner
 * while loading, a retryable message on error, and the full supporter
 * pitch + plan picker + CTA + disclosure once the offering is [Ready].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaywallContent(
    state: PaywallState,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onPlanSelect: (SubscriptionPlanId) -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onTerms: () -> Unit,
    onPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.paywall_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        NubecitaIcon(
                            name = NubecitaIconName.Close,
                            contentDescription = stringResource(R.string.paywall_close_content_description),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (val status = state.status) {
            PaywallStatus.Loading -> PaywallLoadingBody(padding)
            PaywallStatus.Error -> PaywallErrorBody(padding = padding, onRetry = onRetry)
            is PaywallStatus.Ready ->
                PaywallReadyBody(
                    padding = padding,
                    offering = status.offering,
                    selectedPlan = state.selectedPlan,
                    isPurchasing = state.isPurchasing,
                    isRestoring = state.isRestoring,
                    onPlanSelect = onPlanSelect,
                    onPurchase = onPurchase,
                    onRestore = onRestore,
                    onTerms = onTerms,
                    onPrivacy = onPrivacy,
                )
        }
    }
}

@Composable
private fun PaywallLoadingBody(padding: PaddingValues) {
    val loadingLabel = stringResource(R.string.paywall_loading_content_description)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.semantics { contentDescription = loadingLabel },
        )
    }
}

@Composable
private fun PaywallErrorBody(
    padding: PaddingValues,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(MaterialTheme.spacing.s6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NubecitaIcon(
            name = NubecitaIconName.WifiOff,
            contentDescription = null,
            opticalSize = MaterialTheme.spacing.s10,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.paywall_error_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = MaterialTheme.spacing.s4),
        )
        Text(
            text = stringResource(R.string.paywall_error_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = MaterialTheme.spacing.s2),
        )
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier.padding(top = MaterialTheme.spacing.s5),
        ) {
            Text(stringResource(R.string.paywall_retry))
        }
    }
}

@Composable
private fun PaywallReadyBody(
    padding: PaddingValues,
    offering: net.kikin.nubecita.data.models.SubscriptionOffering,
    selectedPlan: SubscriptionPlanId,
    isPurchasing: Boolean,
    isRestoring: Boolean,
    onPlanSelect: (SubscriptionPlanId) -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onTerms: () -> Unit,
    onPrivacy: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(padding),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = CONTENT_MAX_WIDTH)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = MaterialTheme.spacing.s5, vertical = MaterialTheme.spacing.s4),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s5),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Hero — supporter-first framing.
            NubecitaIcon(
                name = NubecitaIconName.Favorite,
                contentDescription = null,
                filled = true,
                opticalSize = MaterialTheme.spacing.s12,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.paywall_headline),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.paywall_subhead),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            // Perks.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s4),
            ) {
                PaywallPerkRow(
                    icon = NubecitaIconName.PlayArrow,
                    title = stringResource(R.string.paywall_perk_pip_title),
                    body = stringResource(R.string.paywall_perk_pip_body),
                )
                PaywallPerkRow(
                    icon = NubecitaIconName.Verified,
                    title = stringResource(R.string.paywall_perk_badge_title),
                    body = stringResource(R.string.paywall_perk_badge_body),
                )
            }

            // Plan picker.
            PaywallPlanPicker(
                offering = offering,
                selectedPlan = selectedPlan,
                onPlanSelect = onPlanSelect,
            )

            // CTA.
            NubecitaPrimaryButton(
                onClick = onPurchase,
                text = stringResource(R.string.paywall_cta),
                isLoading = isPurchasing,
                modifier = Modifier.fillMaxWidth(),
            )

            // Disclosure + legal links + restore.
            Text(
                text = stringResource(R.string.paywall_disclosure),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onTerms) { Text(stringResource(R.string.paywall_terms)) }
                DotSeparator()
                TextButton(onClick = onPrivacy) { Text(stringResource(R.string.paywall_privacy)) }
                DotSeparator()
                TextButton(onClick = onRestore, enabled = !isRestoring) {
                    if (isRestoring) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.paywall_restore))
                    }
                }
            }
        }
    }
}

@Composable
private fun DotSeparator() {
    Text(
        text = "·",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // Purely decorative divider between the legal links — clear its
        // semantics so TalkBack doesn't land on a meaningless "·" node.
        modifier = Modifier.clearAndSetSemantics {},
    )
}

private val CONTENT_MAX_WIDTH = 480.dp
