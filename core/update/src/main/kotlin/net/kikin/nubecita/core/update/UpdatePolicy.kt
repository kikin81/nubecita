package net.kikin.nubecita.core.update

/**
 * Pure, side-effect-free update decision. IMMEDIATE for high-priority/very-stale
 * (never throttled — a critical update should always prompt and also auto-resumes
 * a DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS). Otherwise FLEXIBLE, throttled to once
 * per availableVersionCode via [lastPromptedVersionCode]. If a high-priority update
 * arrives but Play reports IMMEDIATE not allowed, this gracefully degrades to
 * FLEXIBLE rather than nothing (asserted in UpdatePolicyTest).
 */
object UpdatePolicy {
    const val UPDATE_PRIORITY_IMMEDIATE_THRESHOLD = 4
    const val STALENESS_IMMEDIATE_DAYS = 60

    fun decide(
        signals: UpdateSignals,
        lastPromptedVersionCode: Int?,
    ): UpdateAction {
        if (signals.availability != UpdateAvailability.UPDATE_AVAILABLE) return UpdateAction.None
        val highPriority =
            signals.updatePriority >= UPDATE_PRIORITY_IMMEDIATE_THRESHOLD ||
                (signals.stalenessDays ?: 0) >= STALENESS_IMMEDIATE_DAYS
        if (highPriority && signals.isImmediateAllowed) return UpdateAction.Immediate
        if (signals.isFlexibleAllowed && signals.availableVersionCode != lastPromptedVersionCode) {
            return UpdateAction.Flexible
        }
        return UpdateAction.None
    }
}
