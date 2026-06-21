package net.kikin.nubecita.feature.profile.impl.data

import io.github.kikin81.atproto.app.bsky.actor.ActorService
import io.github.kikin81.atproto.app.bsky.feed.FeedService
import io.github.kikin81.atproto.com.atproto.repo.GetRecordRequest
import io.github.kikin81.atproto.com.atproto.repo.PutRecordRequest
import io.github.kikin81.atproto.com.atproto.repo.RepoService
import io.github.kikin81.atproto.runtime.AtField
import io.github.kikin81.atproto.runtime.AtIdentifier
import io.github.kikin81.atproto.runtime.Blob
import io.github.kikin81.atproto.runtime.Cid
import io.github.kikin81.atproto.runtime.Nsid
import io.github.kikin81.atproto.runtime.RecordKey
import io.github.kikin81.atproto.runtime.XrpcError
import io.github.kikin81.atproto.runtime.present
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.image.ImageEncoder
import net.kikin.nubecita.core.moderation.ModerationPreferencesRepository
import net.kikin.nubecita.core.postinteractions.FollowRepository
import net.kikin.nubecita.feature.profile.impl.ProfileTab
import timber.log.Timber
import javax.inject.Inject
import kotlinx.serialization.json.Json as KotlinxJson

/**
 * Default [ProfileRepository] backed by the authenticated `XrpcClient`
 * provided by `:core:auth`. Mirrors the structure of
 * `:feature:feed:impl/data/DefaultFeedRepository` — same `runCatching`
 * + Timber error-identity logging pattern.
 *
 * No caching here; the upstream [net.kikin.nubecita.feature.profile.impl.ProfileViewModel]
 * holds the per-tab state and decides when to fetch. Repository
 * stays stateless so future multi-account swaps don't have to
 * invalidate any in-repo cache.
 *
 * Follow/unfollow delegate to the shared
 * `:core:post-interactions` [FollowRepository] so profile and
 * group-details (chats) write follows through one implementation.
 */
internal class DefaultProfileRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        private val sessionStateProvider: SessionStateProvider,
        private val moderationPreferences: ModerationPreferencesRepository,
        private val encoder: ImageEncoder,
        private val followRepository: FollowRepository,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ProfileRepository {
        // replay = 0 (only live collectors react) + extraBufferCapacity = 1
        // so tryEmit never drops the signal under a momentary slow collector.
        private val _ownProfileUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        override val ownProfileUpdates: SharedFlow<Unit> = _ownProfileUpdates.asSharedFlow()

        override suspend fun fetchHeader(actor: String): Result<ProfileHeaderWithViewer> =
            withContext(dispatcher) {
                runCatching {
                    val client = xrpcClientProvider.authenticated()
                    val response = ActorService(client).getProfile(buildGetProfileRequest(actor))
                    response.toProfileHeaderWithViewer()
                }.onFailure { throwable ->
                    // `actor` is a raw DID or handle (PII). Log only the
                    // error identity — matches the redaction discipline
                    // applied to DIDs in `:core:auth/DefaultXrpcClientProvider`.
                    Timber.tag(TAG).w(throwable, "fetchHeader failed: %s", throwable.javaClass.name)
                }
            }

        override suspend fun fetchTab(
            actor: String,
            tab: ProfileTab,
            cursor: String?,
            limit: Int,
        ): Result<ProfileTabPage> =
            withContext(dispatcher) {
                runCatching {
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        FeedService(client).getAuthorFeed(
                            buildAuthorFeedRequest(actor, tab, cursor, limit),
                        )
                    // Drop hard-filtered posts and cover warned media off the
                    // render path, against the cached prefs + viewer DID.
                    val prefs = moderationPreferences.prefs.value
                    val viewerDid = (sessionStateProvider.state.value as? SessionState.SignedIn)?.did
                    ProfileTabPage(
                        items = response.feed.toTabItems(tab, prefs, viewerDid),
                        nextCursor = response.cursor,
                    )
                }.onFailure { throwable ->
                    // `actor` is a raw DID or handle (PII); `cursor` is
                    // opaque appview state, also withheld. `tab` is a
                    // closed enum — safe to include for triage.
                    Timber.tag(TAG).w(
                        throwable,
                        "fetchTab(tab=%s) failed: %s",
                        tab,
                        throwable.javaClass.name,
                    )
                }
            }

        // Follow/unfollow delegate to the shared :core:post-interactions
        // FollowRepository so profile and group-details (chats) write follows
        // through one path. The redaction discipline + redaction-safe URI
        // parsing live there.
        override suspend fun follow(subjectDid: String): Result<String> = followRepository.follow(subjectDid)

        override suspend fun unfollow(followUri: String): Result<Unit> = followRepository.unfollow(followUri)

        override suspend fun updateProfile(
            displayName: String?,
            description: String?,
            avatar: ImageChange,
            banner: ImageChange,
        ): Result<Unit> =
            withContext(dispatcher) {
                // Explicit try/catch (not runCatching) so cancellation
                // propagates structurally — and so the swap-conflict /
                // create-if-missing XrpcError discrimination stays at the
                // top level rather than buried in onFailure. All failures
                // map to a typed ProfileUpdateError; the raw record body,
                // DID, and AT-URI are never logged (only the error
                // identity), matching the file's redaction discipline.
                try {
                    val selfDid =
                        when (val state = sessionStateProvider.state.value) {
                            is SessionState.SignedIn -> state.did
                            else -> return@withContext Result.failure(ProfileUpdateError.Unauthorized)
                        }
                    val client = xrpcClientProvider.authenticated()
                    val repo = RepoService(client)

                    // Step 1+2: fetch the existing record (+ its CID for
                    // compare-and-swap), or fall into the create-if-missing
                    // path when a brand-new account has no profile record.
                    val (existing, swapCid) = fetchProfileRecord(repo, selfDid)

                    // Step 3: merge edits onto the fetched JsonObject,
                    // uploading only changed images. Unmanaged keys are
                    // copied verbatim, so pinnedPost / labels / createdAt /
                    // unknown future keys all survive.
                    val merged =
                        buildMergedRecord(
                            repo = repo,
                            existing = existing,
                            isCreate = swapCid == null,
                            displayName = displayName,
                            description = description,
                            avatar = avatar,
                            banner = banner,
                        )

                    // Step 4: write back. swapRecord is present only when we
                    // read a prior CID — the create path omits it so a
                    // first write can't fail on a missing-record swap.
                    repo.putRecord(
                        PutRecordRequest(
                            collection = Nsid(PROFILE_NSID),
                            repo = AtIdentifier(selfDid),
                            rkey = RecordKey(PROFILE_RKEY),
                            record = merged,
                            // Present only when we read a prior CID. On the
                            // create path we omit swapRecord entirely (leave
                            // the default AtField.Missing) so the first write
                            // can't fail on a missing-record compare-and-swap.
                            swapRecord = swapCid?.let { present(it) } ?: AtField.Missing,
                        ),
                    )
                    // Tell any live own-profile screen to refetch its header so
                    // the saved fields (incl. the new avatar/banner CDN URLs)
                    // show without a manual refresh.
                    _ownProfileUpdates.tryEmit(Unit)
                    Result.success(Unit)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    Timber.tag(TAG).w(throwable, "updateProfile failed: %s", throwable.javaClass.name)
                    Result.failure(mapToProfileUpdateError(throwable))
                }
            }

        /**
         * Fetches the `app.bsky.actor.profile`/`self` record. Returns the
         * raw record body paired with its CID for compare-and-swap. A
         * `RecordNotFound` XRPC error (brand-new account) is the
         * create-if-missing signal: returns an empty record with a `null`
         * CID so the caller writes without a swap.
         */
        private suspend fun fetchProfileRecord(
            repo: RepoService,
            selfDid: String,
        ): Pair<JsonObject, Cid?> =
            try {
                val response =
                    repo.getRecord(
                        GetRecordRequest(
                            collection = Nsid(PROFILE_NSID),
                            repo = AtIdentifier(selfDid),
                            rkey = RecordKey(PROFILE_RKEY),
                        ),
                    )
                response.value to response.cid
            } catch (error: XrpcError) {
                if (error.errorName == ERROR_RECORD_NOT_FOUND) {
                    // No profile record yet — start from an empty body and
                    // signal "no swap" via a null CID.
                    JsonObject(emptyMap()) to null
                } else {
                    throw error
                }
            }

        /**
         * Builds the record to write: a copy of [existing] with only the
         * managed keys overridden. Everything else (pinnedPost, labels,
         * createdAt, pronouns, unknown future keys) is preserved
         * byte-for-byte. On the create path `$type` is stamped so the
         * fresh record is a valid `app.bsky.actor.profile`.
         */
        private suspend fun buildMergedRecord(
            repo: RepoService,
            existing: JsonObject,
            isCreate: Boolean,
            displayName: String?,
            description: String?,
            avatar: ImageChange,
            banner: ImageChange,
        ): JsonObject {
            val merged = existing.toMutableMap()

            if (isCreate) {
                merged[KEY_TYPE] = JsonPrimitive(PROFILE_NSID)
            }

            mergeText(merged, KEY_DISPLAY_NAME, displayName)
            mergeText(merged, KEY_DESCRIPTION, description)
            mergeImage(merged, KEY_AVATAR, avatar, repo)
            mergeImage(merged, KEY_BANNER, banner, repo)

            return JsonObject(merged)
        }

        /** Set [key] to a non-blank [value], else drop it from [record]. */
        private fun mergeText(
            record: MutableMap<String, JsonElement>,
            key: String,
            value: String?,
        ) {
            if (value.isNullOrBlank()) {
                record.remove(key)
            } else {
                record[key] = JsonPrimitive(value)
            }
        }

        /**
         * Applies one [ImageChange] to [key]:
         *  - [ImageChange.Unchanged] — leave the existing value (or
         *    absence) untouched; no upload.
         *  - [ImageChange.Removed] — drop the key.
         *  - [ImageChange.Replaced] — encode under the blob cap, upload,
         *    and write the returned [Blob] ref as the new value.
         */
        private suspend fun mergeImage(
            record: MutableMap<String, JsonElement>,
            key: String,
            change: ImageChange,
            repo: RepoService,
        ) {
            when (change) {
                ImageChange.Unchanged -> Unit
                ImageChange.Removed -> record.remove(key)
                is ImageChange.Replaced -> {
                    val blob = uploadImage(repo, change)
                    record[key] = blobJson.encodeToJsonElement(Blob.serializer(), blob)
                }
            }
        }

        /**
         * Encodes a replaced image under Bluesky's ~1 MB blob cap, uploads
         * it, and returns the resulting [Blob] ref. A failure here surfaces
         * as [ProfileUpdateError.BlobUploadFailed] so the upstream form
         * stays populated.
         */
        private suspend fun uploadImage(
            repo: RepoService,
            change: ImageChange.Replaced,
        ): Blob =
            try {
                val encoded = encoder.encodeForUpload(bytes = change.bytes, sourceMimeType = change.mimeType)
                repo
                    .uploadBlob(
                        input = encoded.bytes,
                        inputContentType = ContentType.parse(encoded.mimeType),
                    ).blob
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                // Deliberately not logged here: nothing about a user's image upload
                // (byte sizes, mime, blob ids) should reach logcat. The failure
                // still surfaces via updateProfile()'s top-level catch, which logs
                // only the error identity (`throwable.javaClass.name`).
                throw ProfileUpdateError.BlobUploadFailed(throwable)
            }

        private fun mapToProfileUpdateError(throwable: Throwable): ProfileUpdateError =
            when (throwable) {
                is ProfileUpdateError -> throwable
                is NoSessionException -> ProfileUpdateError.Unauthorized
                is XrpcError ->
                    if (throwable.errorName == ERROR_INVALID_SWAP) {
                        ProfileUpdateError.SwapConflict
                    } else {
                        ProfileUpdateError.WriteFailed(throwable)
                    }
                else -> ProfileUpdateError.WriteFailed(throwable)
            }

        private companion object {
            const val TAG = "ProfileRepository"
            const val PROFILE_NSID = "app.bsky.actor.profile"
            const val PROFILE_RKEY = "self"

            const val KEY_TYPE = "\$type"
            const val KEY_DISPLAY_NAME = "displayName"
            const val KEY_DESCRIPTION = "description"
            const val KEY_AVATAR = "avatar"
            const val KEY_BANNER = "banner"

            // XRPC error names discriminated on the write path. getRecord
            // on a brand-new account returns RecordNotFound (→ create);
            // putRecord with a stale swapRecord CID returns InvalidSwap
            // (→ surfaced distinctly so we never silently overwrite a
            // concurrent edit).
            const val ERROR_RECORD_NOT_FOUND = "RecordNotFound"
            const val ERROR_INVALID_SWAP = "InvalidSwap"

            // Local Json used only to serialize an uploaded Blob into the
            // record's JsonObject. The SDK already serializes Blob this way
            // on its own records; we mirror its defaults (no pretty-print,
            // encodeDefaults so the `$type` discriminator is emitted).
            val blobJson =
                KotlinxJson {
                    encodeDefaults = true
                }
        }
    }
