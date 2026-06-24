package net.kikin.nubecita.core.video

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.kikin.nubecita.core.billing.EntitlementRepository
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Hilt qualifier for the one-shot "does this device support Picture-in-Picture"
 * boolean, feature-detected from the [Context] at graph construction. Injecting
 * the resolved boolean (rather than a [Context]) keeps [PipController] free of
 * any Android platform call, so its truth-table logic is unit-testable.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class PipDeviceSupport

/**
 * Whether the device supports Picture-in-Picture. `FEATURE_PICTURE_IN_PICTURE`
 * exists from API 24 and phone PiP from API 26; the project's `minSdk` is 28, so
 * the SDK gate is belt-and-suspenders and the system feature is the real signal.
 */
public fun Context.supportsPip(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

/**
 * The single declarative gate for every Picture-in-Picture call site (design D4).
 * [isEnabled] folds the two independent conditions — the device physically
 * supports PiP, and the user holds Pro — into one boolean, so no call site
 * re-derives the entitlement branch. PiP is offered only while [isEnabled].
 *
 * PiP itself is driven from the Activity / Compose layer (design D5); this
 * `@Singleton` holds the shared state those layers read. [isInPip] is set by the
 * Activity's `onPictureInPictureModeChanged` bridge (a later task) and read by
 * the `SharedVideoPlayer` background-pause seam and the player chrome.
 */
@Singleton
public class PipController
    @Inject
    internal constructor(
        @param:PipDeviceSupport private val deviceSupportsPip: Boolean,
        entitlementRepository: EntitlementRepository,
        @ApplicationScope scope: CoroutineScope,
    ) {
        /**
         * `deviceSupports && isPro`, kept hot so the ~4 PiP call sites can read
         * `.value` synchronously. On a device without PiP this is constant `false`
         * regardless of entitlement; otherwise it tracks [EntitlementRepository.isPro].
         */
        public val isEnabled: StateFlow<Boolean> =
            entitlementRepository.isPro
                .map { isPro -> deviceSupportsPip && isPro }
                // Seed from the current entitlement so `.value` is correct at construction
                // (no false→true flicker if Pro is already active before the collector runs).
                .stateIn(scope, SharingStarted.Eagerly, deviceSupportsPip && entitlementRepository.isPro.value)

        private val _isInPip = MutableStateFlow(false)

        /** Whether the Activity is currently in PiP mode. Source of truth for the background-pause seam. */
        public val isInPip: StateFlow<Boolean> = _isInPip.asStateFlow()

        /** Set by the Activity PiP bridge on `onPictureInPictureModeChanged`. */
        public fun setInPip(value: Boolean) {
            _isInPip.value = value
        }
    }
