package net.kikin.nubecita.core.billing.api

/**
 * Outcome of a [BillingRepository.restorePurchases] attempt. Restore always
 * "succeeds" in the sense of completing a sync; [Completed.isPro] reports
 * whether the Play account actually owns Pro afterward, so the UI can say
 * "Restored!" vs "Nothing to restore" without conflating either with [Error].
 */
public sealed interface RestoreResult {
    /** Restore synced successfully; [isPro] is the resulting entitlement state. */
    public data class Completed(
        val isPro: Boolean,
    ) : RestoreResult

    /** The restore call itself failed; [message] is an already-localized reason. */
    public data class Error(
        val message: String,
    ) : RestoreResult
}
