package net.kikin.nubecita.feature.chats.impl.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import timber.log.Timber

/**
 * Debug-only broadcast receiver to trigger a DM-poll manually via adb:
 * `adb shell am broadcast -n net.kikin.nubecita/net.kikin.nubecita.feature.chats.impl.worker.DmPollDebugReceiver`
 *
 * Logs the runner's [DmPollRunner.Outcome] to logcat under the "DmPoll" tag.
 * Uses an entry point to pull the runner and application scope from the Hilt
 * graph since manifest receivers can't use constructor injection.
 */
internal class DmPollDebugReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DmPollDebugEntryPoint {
        fun dmPollRunner(): DmPollRunner

        @ApplicationScope
        fun applicationScope(): CoroutineScope
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val entryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                DmPollDebugEntryPoint::class.java,
            )
        val runner = entryPoint.dmPollRunner()
        val scope = entryPoint.applicationScope()
        val pendingResult = goAsync()

        Timber.tag("DmPoll").d("Debug trigger: starting manual poll")
        scope.launch {
            try {
                val outcome = runner.run()
                Timber.tag("DmPoll").d("Debug trigger: poll finished with outcome %s", outcome)
            } catch (e: Exception) {
                Timber.tag("DmPoll").e(e, "Debug trigger: poll failed with exception")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
