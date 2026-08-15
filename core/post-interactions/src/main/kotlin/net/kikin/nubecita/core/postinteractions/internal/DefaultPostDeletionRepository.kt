package net.kikin.nubecita.core.postinteractions.internal

import io.github.kikin81.atproto.com.atproto.repo.DeleteRecordRequest
import io.github.kikin81.atproto.com.atproto.repo.RepoService
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Nsid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.common.coroutines.runCatchingCancellable
import net.kikin.nubecita.core.postinteractions.PostDeletionRepository
import timber.log.Timber
import javax.inject.Inject

internal class DefaultPostDeletionRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : PostDeletionRepository {
        override suspend fun deletePost(postUri: AtUri): Result<Unit> =
            withContext(dispatcher) {
                runCatchingCancellable {
                    val (repo, rkey) = postUri.repoAndRkey()
                    RepoService(xrpcClientProvider.authenticated()).deleteRecord(
                        DeleteRecordRequest(
                            collection = Nsid(POST_NSID),
                            repo = repo,
                            rkey = rkey,
                        ),
                    )
                    Unit
                }.onFailure { throwable ->
                    // The URI carries the author's DID, so it is kept out of the
                    // message — same redaction policy as DefaultXrpcClientProvider.
                    // The throwable's stack carries the underlying cause.
                    Timber.tag(TAG).w(throwable, "deletePost failed: %s", throwable.javaClass.name)
                }
            }

        private companion object {
            const val POST_NSID = "app.bsky.feed.post"
            const val TAG = "PostDeletionRepository"
        }
    }
