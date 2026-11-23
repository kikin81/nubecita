package net.kikin.nubecita.feature.starterpack.impl.data

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import net.kikin.nubecita.data.models.ActorUi
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Hand-written [StarterPackRepository] fake. A pack and each members page are registered
 * through a [CompletableDeferred] (so a test can hold a call open with [gateMembers]), and
 * every call is recorded.
 */
internal class FakeStarterPackRepository : StarterPackRepository {
    private val packs = mutableMapOf<String, CompletableDeferred<Result<StarterPackDetail>>>()
    private val pages = mutableMapOf<Pair<String, String?>, CompletableDeferred<Result<PackMembersPage>>>()

    val packCalls: MutableList<String> = CopyOnWriteArrayList()
    val memberCalls: MutableList<Pair<String, String?>> = CopyOnWriteArrayList()

    fun respondPack(
        uri: String,
        detail: StarterPackDetail,
    ) {
        packs.getOrPut(uri) { CompletableDeferred() }.complete(Result.success(detail))
    }

    fun failPack(
        uri: String,
        throwable: Throwable,
    ) {
        packs.getOrPut(uri) { CompletableDeferred() }.complete(Result.failure(throwable))
    }

    fun clearPack(uri: String) {
        packs.remove(uri)
    }

    fun respondMembers(
        listUri: String,
        cursor: String?,
        members: List<ActorUi>,
        nextCursor: String? = null,
    ) {
        gateMembers(listUri, cursor).complete(Result.success(PackMembersPage(members.toImmutableList(), nextCursor)))
    }

    fun failMembers(
        listUri: String,
        cursor: String?,
        throwable: Throwable,
    ) {
        gateMembers(listUri, cursor).complete(Result.failure(throwable))
    }

    fun gateMembers(
        listUri: String,
        cursor: String?,
    ): CompletableDeferred<Result<PackMembersPage>> = pages.getOrPut(listUri to cursor) { CompletableDeferred() }

    fun clearMembers(
        listUri: String,
        cursor: String?,
    ) {
        pages.remove(listUri to cursor)
    }

    override suspend fun getStarterPack(uri: String): Result<StarterPackDetail> {
        packCalls += uri
        return packs[uri]?.await() ?: Result.failure(IllegalStateException("no pack registered for $uri"))
    }

    override suspend fun getMembers(
        listUri: String,
        cursor: String?,
        limit: Int,
    ): Result<PackMembersPage> {
        memberCalls += listUri to cursor
        return pages[listUri to cursor]?.await() ?: Result.success(PackMembersPage(persistentListOf(), null))
    }
}

internal const val PACK_URI = "at://did:plc:creator/app.bsky.graph.starterpack/pack1"
internal const val LIST_URI = "at://did:plc:creator/app.bsky.graph.list/list1"

internal fun detailFixture(
    uri: String = PACK_URI,
    name: String = "Android developers",
    description: String? = "People building Android.",
    listUri: String? = LIST_URI,
    memberCount: Int? = 3,
    feeds: List<PackFeedUi> = emptyList(),
) = StarterPackDetail(
    uri = uri,
    name = name,
    creatorHandle = "creator.bsky.social",
    description = description,
    memberCount = memberCount,
    listUri = listUri,
    feeds = feeds.toImmutableList(),
)

internal fun memberFixture(
    did: String,
    isFollowing: Boolean = false,
    followUri: String? = null,
) = ActorUi(
    did = did,
    handle = "${did.substringAfterLast(':')}.bsky.social",
    displayName = did.substringAfterLast(':'),
    avatarUrl = null,
    bio = "Bio of $did",
    isFollowing = isFollowing,
    followUri = followUri,
)
