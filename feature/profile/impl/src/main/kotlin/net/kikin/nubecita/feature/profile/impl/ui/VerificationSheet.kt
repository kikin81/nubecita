package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toLocalDateTime
import net.kikin.nubecita.data.models.VerifiedBadge
import net.kikin.nubecita.data.models.VerifierUi
import net.kikin.nubecita.designsystem.component.NubecitaWavyProgressIndicator
import net.kikin.nubecita.designsystem.component.VerificationBadge
import net.kikin.nubecita.feature.profile.impl.R
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.time.Instant

/**
 * Test tags for the verification explanation surfaces. [SHEET] is on the
 * [ModalBottomSheet] window; [CONTENT] is on the screenshot-testable body
 * (layoutlib can't render the modal wrapper — the content is pinned directly).
 */
internal object VerificationSheetTestTags {
    const val SHEET = "verification_sheet"
    const val CONTENT = "verification_sheet_content"
}

/**
 * Bottom sheet that explains what a profile's verification badge means and,
 * lazily, who issued it. Hosted by `ProfileScreenContent` when
 * `ProfileScreenViewState.verificationSheetVisible` is true; dismissed via
 * `ProfileEvent.VerificationSheetDismissed`.
 *
 * The explanation copy is our own static text (the AT Protocol response carries
 * no description) keyed on the [badge] tier; only the issuer list is resolved
 * from the wire (`getProfiles`, see nubecita-vw45.3). While that resolves the
 * sheet shows a spinner; a failure degrades to a non-blocking notice so the
 * explanation is always readable.
 *
 * No pixel baseline for the sheet itself — layoutlib doesn't render
 * `ModalBottomSheet` reliably. [VerificationSheetContent] carries the baselines.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VerificationSheet(
    badge: VerifiedBadge,
    verifiers: ImmutableList<VerifierUi>,
    isLoading: Boolean,
    isError: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.testTag(VerificationSheetTestTags.SHEET),
    ) {
        VerificationSheetContent(
            badge = badge,
            verifiers = verifiers,
            isLoading = isLoading,
            isError = isError,
        )
    }
}

@Composable
internal fun VerificationSheetContent(
    badge: VerifiedBadge,
    verifiers: ImmutableList<VerifierUi>,
    isLoading: Boolean,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    val titleRes: Int
    val bodyRes: Int
    when (badge) {
        VerifiedBadge.TrustedVerifier -> {
            titleRes = R.string.verification_sheet_title_trusted_verifier
            bodyRes = R.string.verification_sheet_body_trusted_verifier
        }
        else -> {
            titleRes = R.string.verification_sheet_title_verified
            bodyRes = R.string.verification_sheet_body_verified
        }
    }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(VerificationSheetTestTags.CONTENT)
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VerificationBadge(badge = badge, size = 28.dp)
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // The issuer section only appears when there is something to show for it:
        // resolved verifiers, an in-flight resolve, or a resolve error.
        if (isLoading || isError || verifiers.isNotEmpty()) {
            HorizontalDivider()
            Text(
                text = stringResource(R.string.verification_sheet_verifiers_header),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            when {
                isLoading ->
                    NubecitaWavyProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp),
                    )
                isError ->
                    Text(
                        text = stringResource(R.string.verification_sheet_verifiers_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                else ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        verifiers.forEach { VerifierRow(it) }
                    }
            }
        }
    }
}

@Composable
private fun VerifierRow(
    verifier: VerifierUi,
    modifier: Modifier = Modifier,
) {
    val displayName = verifier.displayName?.takeUnless { it.isBlank() }
    val formattedDate = remember(verifier.verifiedAt) { formatVerifiedDate(verifier.verifiedAt) }
    val dateText = stringResource(R.string.verification_sheet_verified_on, formattedDate)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName ?: "@${verifier.handle}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (displayName != null) {
                Text(
                    text = "@${verifier.handle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = dateText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Localized medium date for a verification's `createdAt` (e.g. "May 1, 2026"). */
private fun formatVerifiedDate(instant: Instant): String =
    DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .format(instant.toLocalDateTime(TimeZone.currentSystemDefault()).date.toJavaLocalDate())
