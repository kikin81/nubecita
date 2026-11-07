package net.kikin.nubecita.bootstrap

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.AuthTestHarness
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * adb-driven trigger for [AuthTestHarness] (nubecita-zh7p), so a verification
 * run is a scriptable loop rather than a sequence of taps:
 *
 * ```
 * adb shell am broadcast -a net.kikin.nubecita.AUTH_HARNESS \
 *   --es scenario BadRefreshToken -p net.kikin.nubecita
 * ```
 *
 * Registered at runtime rather than in the manifest: a manifest entry is
 * merged from a source set that this build may not even compile, and an
 * exported receiver declared in the shipped manifest is exactly the kind of
 * thing that outlives the branch that added it.
 *
 * The Settings entry drives the same harness; this exists because repeating a
 * four-scenario matrix by hand is how a verification run quietly turns into a
 * verification run you only think you did.
 */
@Singleton
class AuthTestHarnessReceiver
    @Inject
    constructor(
        private val harness: AuthTestHarness,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) : BroadcastReceiver() {
        fun register(context: Context) {
            ContextCompat.registerReceiver(
                context,
                this,
                IntentFilter(ACTION),
                ContextCompat.RECEIVER_EXPORTED,
            )
            Timber.tag(TAG).w("auth test harness receiver registered — NOT a shippable build")
        }

        override fun onReceive(
            context: Context,
            intent: Intent,
        ) {
            if (intent.action != ACTION) return
            val requested = intent.getStringExtra(EXTRA_SCENARIO)
            val scenario = AuthTestHarness.Scenario.entries.firstOrNull { it.name == requested }
            if (scenario == null) {
                // Loud, not a default: silently running "some" scenario would
                // produce a capture nobody can attribute.
                Timber.tag(TAG).e(
                    "unknown scenario %s; expected one of %s",
                    requested,
                    AuthTestHarness.Scenario.entries.joinToString { it.name },
                )
                return
            }
            Timber.tag(TAG).w("running %s — expect: %s", scenario.name, scenario.expectation)
            scope.launch { harness.runAndHalt(scenario) }
        }

        private companion object {
            const val ACTION = "net.kikin.nubecita.AUTH_HARNESS"
            const val EXTRA_SCENARIO = "scenario"
            const val TAG = "AuthTestHarness"
        }
    }
