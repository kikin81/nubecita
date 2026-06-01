package net.kikin.nubecita.feature.paywall.impl.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.data.models.SubscriptionOffering
import net.kikin.nubecita.data.models.SubscriptionPlan
import net.kikin.nubecita.data.models.SubscriptionPlanId
import net.kikin.nubecita.designsystem.spacing
import net.kikin.nubecita.feature.paywall.impl.R
import java.text.NumberFormat
import java.util.Currency

/**
 * The two-plan selector. Annual is presented first (it's the default and
 * the better-value option — design D9) and carries the savings badge plus
 * the per-month-equivalent comparison line; monthly is the simpler anchor.
 *
 * Both cards share a [selectableGroup] so TalkBack announces them as a
 * single radio set ("1 of 2"), and each card is a [Role.RadioButton].
 */
@Composable
internal fun PaywallPlanPicker(
    offering: SubscriptionOffering,
    selectedPlan: SubscriptionPlanId,
    onPlanSelect: (SubscriptionPlanId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s3),
    ) {
        PlanCard(
            plan = offering.annual,
            planLabel = stringResource(R.string.paywall_plan_annual),
            periodCaption = stringResource(R.string.paywall_plan_per_year_caption),
            savingsPercent = offering.annualSavingsPercent,
            monthlyEquivalent =
                formatCurrency(
                    micros = offering.annualMonthlyEquivalentMicros,
                    currencyCode = offering.annual.priceCurrencyCode,
                ),
            selected = selectedPlan == SubscriptionPlanId.Annual,
            onSelect = { onPlanSelect(SubscriptionPlanId.Annual) },
            modifier = Modifier.weight(1f),
        )
        PlanCard(
            plan = offering.monthly,
            planLabel = stringResource(R.string.paywall_plan_monthly),
            periodCaption = stringResource(R.string.paywall_plan_per_month_caption),
            savingsPercent = 0,
            monthlyEquivalent = null,
            selected = selectedPlan == SubscriptionPlanId.Monthly,
            onSelect = { onPlanSelect(SubscriptionPlanId.Monthly) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PlanCard(
    plan: SubscriptionPlan,
    planLabel: String,
    periodCaption: String,
    savingsPercent: Int,
    monthlyEquivalent: String?,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Selection is conveyed visually by the primary-colored border + raised
    // container tone. The whole card carries a single radio-button semantics
    // node; clearAndSetSemantics collapses the inner Texts into one spoken
    // label so TalkBack says "Annual, $19.99" + selected state, not each line.
    val planContentDescription =
        stringResource(R.string.paywall_plan_content_description, planLabel, plan.formattedPrice)
    Surface(
        shape = RoundedCornerShape(MaterialTheme.spacing.s4),
        color =
            if (selected) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        border =
            if (selected) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            },
        modifier =
            modifier.selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .padding(MaterialTheme.spacing.s4)
                    .clearAndSetSemantics { contentDescription = planContentDescription },
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s1),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = planLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (savingsPercent > 0) {
                    SavingsBadge(percent = savingsPercent)
                }
            }
            Text(
                text = plan.formattedPrice,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text =
                    if (monthlyEquivalent != null) {
                        "$periodCaption · ${
                            stringResource(R.string.paywall_plan_annual_monthly_equivalent, monthlyEquivalent)
                        }"
                    } else {
                        periodCaption
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SavingsBadge(
    percent: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(MaterialTheme.spacing.s2),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.paywall_plan_savings_badge, percent),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier =
                Modifier.padding(
                    horizontal = MaterialTheme.spacing.s2,
                    vertical = MaterialTheme.spacing.s1,
                ),
        )
    }
}

/**
 * Format a micros amount (1 unit == 1_000_000 micros) as a currency string
 * for the annual per-month-equivalent line. Falls back to the platform
 * default currency if [currencyCode] isn't a recognized ISO 4217 code.
 * Distinct from [SubscriptionPlan.formattedPrice], which is the store's
 * own verbatim string and is always preferred for the headline prices.
 */
private fun formatCurrency(
    micros: Long,
    currencyCode: String,
): String {
    val format = NumberFormat.getCurrencyInstance()
    runCatching { format.currency = Currency.getInstance(currencyCode) }
    return format.format(micros / MICROS_PER_UNIT)
}

private const val MICROS_PER_UNIT = 1_000_000.0
