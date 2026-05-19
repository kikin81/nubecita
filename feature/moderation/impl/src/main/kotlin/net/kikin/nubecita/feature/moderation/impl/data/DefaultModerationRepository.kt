package net.kikin.nubecita.feature.moderation.impl.data

import io.github.kikin81.atproto.com.atproto.admin.RepoRef
import io.github.kikin81.atproto.com.atproto.moderation.CreateReportModTool
import io.github.kikin81.atproto.com.atproto.moderation.CreateReportRequest
import io.github.kikin81.atproto.com.atproto.moderation.ModerationService
import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import io.github.kikin81.atproto.runtime.AtField
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Cid
import io.github.kikin81.atproto.runtime.Did
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.feature.moderation.impl.ModerationRepository
import net.kikin.nubecita.feature.moderation.impl.data.internal.GraphemeText
import timber.log.Timber
import javax.inject.Inject

/**
 * Submits `com.atproto.moderation.createReport` via the authenticated
 * [io.github.kikin81.atproto.runtime.XrpcClient]. Wire-layer types are
 * constructed only inside this class — the call surface deals in plain
 * `String`s and the [ModerationRepository] interface is free of SDK
 * dependencies.
 *
 * Failure handling mirrors [DefaultLikeRepostRepository] —
 * `runCatching { ... }.onFailure { Timber.e(...) }` preserves the
 * underlying throwable for the caller's `Result.exceptionOrNull()` while
 * surfacing the failure class in logs for observability.
 */
internal class DefaultModerationRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ModerationRepository {
        override suspend fun reportPost(
            uri: String,
            cid: String,
            reasonToken: String,
            details: String?,
        ): Result<Unit> =
            submit(
                subject = { StrongRef(uri = AtUri(uri), cid = Cid(cid)) },
                reasonToken = reasonToken,
                details = details,
                subjectLabel = "post",
            )

        override suspend fun reportAccount(
            did: String,
            reasonToken: String,
            details: String?,
        ): Result<Unit> =
            submit(
                subject = { RepoRef(did = Did(did)) },
                reasonToken = reasonToken,
                details = details,
                subjectLabel = "account",
            )

        // Both methods share the same envelope — only the subject union
        // variant and the log label differ. A lambda for `subject`
        // keeps the wire-type construction lazy so the runCatching
        // captures any `AtUri` / `Cid` / `Did` parsing failure too.
        private suspend inline fun submit(
            crossinline subject: () -> io.github.kikin81.atproto.com.atproto.moderation.CreateReportRequestSubjectUnion,
            reasonToken: String,
            details: String?,
            subjectLabel: String,
        ): Result<Unit> =
            withContext(dispatcher) {
                runCatching {
                    val client = xrpcClientProvider.authenticated()
                    val request =
                        CreateReportRequest(
                            reasonType = reasonToken,
                            subject = subject(),
                            reason = encodeReason(details),
                            modTool = AtField.Defined(MOD_TOOL),
                        )
                    ModerationService(client).createReport(request)
                    Unit
                }.onFailure { throwable ->
                    Timber.tag(TAG).e(
                        throwable,
                        "createReport(%s) failed: %s",
                        subjectLabel,
                        throwable.javaClass.name,
                    )
                }
            }

        // Null OR blank details → omit the field entirely from the wire
        // payload. Otherwise truncate to the lexicon's 2000-grapheme
        // max before encoding. The UI's 300-grapheme cap is enforced
        // separately at the call surface; this is the server-contract
        // floor, not a redundant cap.
        private fun encodeReason(details: String?): AtField<String> =
            when {
                details.isNullOrBlank() -> AtField.Missing
                else -> AtField.Defined(GraphemeText.truncate(details, max = REASON_MAX_GRAPHEMES))
            }

        private companion object {
            const val TAG = "ModerationRepository"

            // Lexicon `reason.maxGraphemes` for createReport. Audit
            // against ~/code/kikinlex/.../moderation/createReport.json
            // when this constant is touched.
            const val REASON_MAX_GRAPHEMES = 2000

            // modTool.name identifies the source of the report to
            // Bluesky moderators. BuildConfig.VERSION_NAME is
            // intentionally NOT included — see the change's design
            // Decision 7.
            const val MOD_TOOL_NAME = "nubecita/android"

            val MOD_TOOL: CreateReportModTool = CreateReportModTool(name = MOD_TOOL_NAME)
        }
    }
