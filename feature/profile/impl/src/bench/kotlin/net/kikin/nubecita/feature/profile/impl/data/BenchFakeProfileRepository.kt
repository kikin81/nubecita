package net.kikin.nubecita.feature.profile.impl.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.data.models.VerifierUi
import net.kikin.nubecita.feature.profile.impl.ProfileTab
import net.kikin.nubecita.feature.profile.impl.VerifierRef
import timber.log.Timber
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class BenchFakeProfileRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ProfileRepository {
        private val mutex = Mutex()
        private var cachedProfile: BenchProfileDto? = null

        override val ownProfileUpdates: SharedFlow<Unit> = MutableSharedFlow<Unit>().asSharedFlow()

        override suspend fun fetchHeader(actor: String): Result<ProfileHeaderWithViewer> =
            ensureLoaded().map { dto ->
                BenchProfileMapper.toProfileHeaderWithViewer(dto.header)
            }

        // Bench: resolve each ref to a deterministic fake verifier so the verification
        // sheet renders offline. Empty refs → empty (no fake).
        override suspend fun resolveVerifiers(refs: ImmutableList<VerifierRef>): Result<ImmutableList<VerifierUi>> =
            Result.success(
                refs
                    .map { ref ->
                        VerifierUi(
                            did = ref.did,
                            handle = "verifier.bsky.social",
                            displayName = "Bluesky",
                            verifiedAt = ref.verifiedAt,
                        )
                    }.toImmutableList(),
            )

        override suspend fun fetchTab(
            actor: String,
            tab: ProfileTab,
            cursor: String?,
            limit: Int,
        ): Result<ProfileTabPage> =
            ensureLoaded().map { dto ->
                val items =
                    when (tab) {
                        ProfileTab.Posts -> BenchProfileMapper.toTabItemPosts(dto.posts)
                        ProfileTab.Replies -> BenchProfileMapper.toTabItemPosts(dto.replies)
                        ProfileTab.Media -> BenchProfileMapper.toTabItemMediaCells(dto.media)
                        // Bench Likes reuses the sample posts so the own-profile
                        // Likes tab renders offline (getActorLikes has no fake wire).
                        ProfileTab.Likes -> BenchProfileMapper.toTabItemPosts(dto.posts)
                    }.toPersistentList()
                ProfileTabPage(items = items, nextCursor = null)
            }

        override suspend fun follow(subjectDid: String): Result<String> = Result.success("at://bench/follow/$subjectDid")

        override suspend fun unfollow(followUri: String): Result<Unit> = Result.success(Unit)

        override suspend fun updateProfile(
            displayName: String?,
            description: String?,
            avatar: ImageChange,
            banner: ImageChange,
        ): Result<Unit> = Result.success(Unit)

        private suspend fun ensureLoaded(): Result<BenchProfileDto> =
            withContext(dispatcher) {
                cachedProfile?.let { return@withContext Result.success(it) }
                mutex.withLock {
                    cachedProfile?.let { return@withLock Result.success(it) }
                    runCatching { loadFromAsset(PROFILE_ASSET_PATH) }
                        .onSuccess { cachedProfile = it }
                        .onFailure { Timber.tag(TAG).e(it, "Failed to load bench profile: %s", PROFILE_ASSET_PATH) }
                }
            }

        private fun loadFromAsset(assetPath: String): BenchProfileDto {
            val stream =
                try {
                    context.assets.open(assetPath)
                } catch (e: FileNotFoundException) {
                    throw FixtureLoadException(
                        "Bench profile fixture not packaged: assets/$assetPath not found. " +
                            "Verify :feature:profile:impl's bench source set includes the asset.",
                        e,
                    )
                }
            return stream.use { input ->
                @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
                JSON.decodeFromStream(BenchProfileDto.serializer(), input)
            }
        }

        private companion object {
            private const val TAG = "BenchFakeProfileRepo"
            private const val PROFILE_ASSET_PATH = "profile.json"

            private val JSON =
                Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                }
        }
    }

internal class FixtureLoadException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
