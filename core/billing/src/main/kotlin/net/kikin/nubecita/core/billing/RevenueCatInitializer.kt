package net.kikin.nubecita.core.billing

import android.content.Context
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single sanctioned `Purchases.configure` seam (design: SDK init in
 * `Application.onCreate`). Kept here so the RevenueCat SDK stays confined to
 * `:core:billing`; `:app`'s production-flavor `AppInitializer` injects this and
 * calls [initialize] with the `BuildConfig.REVENUECAT_API_KEY` it owns. The
 * bench flavor never registers that initializer, so configure never runs and
 * the APK stays network-silent.
 *
 * Binds Pro to the Google Play account via an anonymous `appUserID` (design D3:
 * no `logIn(did)`), so the entitlement survives a deleted Bluesky account and
 * follows the Play account across devices via Restore.
 */
@Singleton
public class RevenueCatInitializer
    @Inject
    internal constructor(
        @param:ApplicationContext private val context: Context,
        private val entitlementRepository: RevenueCatEntitlementRepository,
    ) {
        /**
         * Configure the SDK and start observing the entitlement. A blank
         * [apiKey] (the default for a build that didn't supply
         * `-PrevenueCatApiKey=…`) is a no-op: configure is skipped and Pro stays
         * inert rather than crashing a keyless dev build. [verboseLogging] maps
         * to the SDK log level (pass `BuildConfig.DEBUG`).
         */
        public fun initialize(
            apiKey: String,
            verboseLogging: Boolean = false,
        ) {
            if (apiKey.isBlank()) {
                Timber.tag(TAG).w("RevenueCat API key absent — skipping configure; Pro features stay inert.")
                return
            }
            Purchases.logLevel = if (verboseLogging) LogLevel.VERBOSE else LogLevel.INFO
            Purchases.configure(
                PurchasesConfiguration
                    .Builder(context, apiKey)
                    .appUserID(null) // anonymous: bind to the Play account, never the DID (D3)
                    .build(),
            )
            entitlementRepository.startObserving()
        }

        /**
         * Link this install's Firebase **app-instance id** to the RevenueCat
         * customer so RevenueCat's server-side GA4 integration attributes
         * subscription/revenue events to the same analytics user (launch
         * checklist F4 / q5ge.13). No-op when [firebaseAppInstanceId] is null
         * (analytics disabled / bench) or the SDK isn't configured (keyless
         * build) — keeps the "inert, not crashing" promise. The composition
         * root reads the id via `:core:analytics` and passes it here, so the
         * RevenueCat SDK stays confined to this module.
         */
        fun linkFirebaseAppInstanceId(firebaseAppInstanceId: String?) {
            if (firebaseAppInstanceId.isNullOrBlank() || !Purchases.isConfigured) return
            Purchases.sharedInstance.setFirebaseAppInstanceID(firebaseAppInstanceId)
        }

        private companion object {
            const val TAG = "RevenueCatInit"
        }
    }
