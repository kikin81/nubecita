package net.kikin.nubecita.core.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Launch-time pass that converts a session clear which never reported itself
 * into a non-fatal (nubecita-yv4p).
 *
 * `EncryptedOAuthSessionStore.clear()` writes a durable marker before wiping
 * the session and consumes it once the loss is recorded. A marker still
 * present at the next start therefore means the process died in between — a
 * silent logout that, before this, produced no telemetry at all.
 *
 * The same pass reports a token refresh the previous process sent and never
 * finished, from the marker [TokenRefreshTelemetry] writes before each POST.
 *
 * Exists as a public seam purely so `:app` can wire it into the `AppInitializer`
 * multibinding: [SessionTelemetry] itself is `internal` to this module.
 *
 * [start] only schedules — the read happens on [scope], never on the app's
 * single-threaded initializer pass.
 */
@Singleton
class SessionClearTelemetryRecovery
    @Inject
    internal constructor(
        private val telemetry: SessionTelemetry,
        private val refreshTelemetry: TokenRefreshTelemetry,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) {
        private val started = AtomicBoolean(false)

        /**
         * Idempotent one-shot. Guarded by a CAS rather than an "is the job still
         * active" check: the pass is short, so a second [start] could easily
         * land after the first finished and re-report a clear that was already
         * consumed.
         */
        fun start() {
            if (!started.compareAndSet(false, true)) return
            scope.launch {
                telemetry.reportLostClearTelemetry()
                refreshTelemetry.reportRefreshDiedInFlight()
            }
        }
    }
