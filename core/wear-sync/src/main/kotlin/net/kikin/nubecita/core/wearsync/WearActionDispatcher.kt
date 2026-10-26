package net.kikin.nubecita.core.wearsync

import kotlinx.coroutines.CancellationException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.wearcontract.WearAction
import net.kikin.nubecita.core.wearcontract.WearActionFailure
import net.kikin.nubecita.core.wearcontract.WearActionRequest
import net.kikin.nubecita.core.wearcontract.WearActionResult
import net.kikin.nubecita.core.wearcontract.WearCodec
import net.kikin.nubecita.core.wearcontract.WearDecodeResult
import net.kikin.nubecita.core.wearcontract.WearPaths
import net.kikin.nubecita.core.wearsync.internal.WearDataLayer
import timber.log.Timber
import kotlin.time.Clock

/**
 * Runs one kind of watch action on the phone. Feature modules contribute these
 * with `@IntoSet`, so `:core:wear-sync` never depends on a feature `impl`.
 */
interface WearActionHandler {
    fun canHandle(action: WearAction): Boolean

    /**
     * Performs [action]. Success carries the record URI a like or repost
     * created (null otherwise); failure is reported to the watch as
     * [WearActionFailure.ERROR].
     */
    suspend fun execute(action: WearAction): Result<String?>
}

/**
 * Answers the action items the watch writes (nubecita-hohv.1): run the action
 * through a [WearActionHandler], write a [WearActionResult], then delete the
 * action item.
 *
 * - An action for any account but the signed-in one is never run
 *   ([WearActionFailure.ACCOUNT]).
 * - An action this build can't read or has no handler for is never run
 *   ([WearActionFailure.UNSUPPORTED]).
 * - The result is written before the action item is deleted, and an action
 *   that already has a result is only deleted, so a redelivered item (a crash
 *   between the two writes, or a repeated change event) never likes twice.
 */
class WearActionDispatcher internal constructor(
    private val dataLayer: WearDataLayer,
    private val sessionStateProvider: SessionStateProvider,
    private val handlers: Set<@JvmSuppressWildcards WearActionHandler>,
    private val clock: Clock,
) {
    /**
     * Handles the item at [path], written by the watch node [sourceNodeId],
     * whose payload is [json] (null when the item carried none).
     */
    suspend fun onActionItem(
        sourceNodeId: String,
        path: String,
        json: String?,
    ) {
        val (accountDid, actionId) = WearPaths.parseAction(path) ?: return
        // pathsUnder matches by prefix ("a1" would also find "a10"); require the exact path.
        val resultPath = WearPaths.actionResult(accountDid, actionId)
        if (resultPath in dataLayer.pathsUnder(resultPath)) {
            dataLayer.deleteFromNode(sourceNodeId, path)
            return
        }

        val request = json?.let(::decode)?.takeIf { it.accountDid == accountDid && it.actionId == actionId }
        val signedInDid = (sessionStateProvider.state.value as? SessionState.SignedIn)?.did
        val handler = request?.let { r -> handlers.firstOrNull { it.canHandle(r.action) } }
        val outcome: Result<String?> =
            when {
                request == null -> Result.failure(Refused(WearActionFailure.UNSUPPORTED))
                accountDid != signedInDid -> Result.failure(Refused(WearActionFailure.ACCOUNT))
                handler == null -> Result.failure(Refused(WearActionFailure.UNSUPPORTED))
                else -> run(handler, request.action)
            }

        val result =
            WearActionResult(
                actionId = actionId,
                accountDid = accountDid,
                completedAtMs = clock.now().toEpochMilliseconds(),
                failure = outcome.exceptionOrNull()?.let { (it as? Refused)?.failure ?: WearActionFailure.ERROR },
                recordUri = outcome.getOrNull(),
            )
        dataLayer.put(WearPaths.actionResult(accountDid, actionId), WearCodec.encode(WearActionResult.serializer(), result), urgent = true)
        dataLayer.deleteFromNode(sourceNodeId, path)
    }

    private fun decode(json: String): WearActionRequest? =
        when (val decoded = WearCodec.decode(WearActionRequest.serializer(), json)) {
            is WearDecodeResult.Decoded -> decoded.value
            is WearDecodeResult.UnsupportedVersion, is WearDecodeResult.Malformed -> null
        }

    private suspend fun run(
        handler: WearActionHandler,
        action: WearAction,
    ): Result<String?> =
        try {
            handler.execute(action)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Timber.w(error, "watch action %s failed", action::class.simpleName)
            Result.failure(error)
        }

    // An action refused before reaching a handler, with the reason the watch sees.
    private class Refused(
        val failure: WearActionFailure,
    ) : Exception(failure.name)
}
