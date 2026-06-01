package net.kikin.nubecita.core.billing

/**
 * Outcome of a [BillingRepository.purchase] attempt. [Cancelled] is modelled
 * distinctly from [Error] because a user backing out of the Play purchase
 * sheet is the expected happy-path exit, not a failure to surface — the
 * paywall stays put silently on [Cancelled] but raises a message on [Error].
 */
public sealed interface PurchaseResult {
    /** Purchase completed and the `pro` entitlement is (or will become) active. */
    public data object Success : PurchaseResult

    /** The user dismissed the purchase flow before completing it. */
    public data object Cancelled : PurchaseResult

    /**
     * The purchase failed. [message] is a developer-facing diagnostic reason
     * (often the provider's English string) for logging — NOT user-ready copy.
     * The paywall maps failures to its own localized messaging and should not
     * surface this verbatim.
     */
    public data class Error(
        val message: String,
    ) : PurchaseResult
}
