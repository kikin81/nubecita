package net.kikin.nubecita.core.auth

import io.github.kikin81.atproto.runtime.XrpcError
import io.github.kikin81.atproto.runtime.XrpcErrorListener
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.ApiError
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reports every XRPC call that finally fails, after any DPoP retry, as an
 * [ApiError] event. Features catch these errors and show at most a snackbar,
 * so without this a regression only surfaces when someone notices it.
 */
@Singleton
internal class XrpcFailureReporter
    @Inject
    constructor(
        private val analytics: AnalyticsClient,
    ) : XrpcErrorListener {
        override fun onError(
            nsid: String,
            error: XrpcError,
        ) {
            analytics.log(ApiError(endpoint = nsid, errorCode = error.errorName, status = error.status))
        }
    }
