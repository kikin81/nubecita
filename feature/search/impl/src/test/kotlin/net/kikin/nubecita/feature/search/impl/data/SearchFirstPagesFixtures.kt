package net.kikin.nubecita.feature.search.impl.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import net.kikin.nubecita.core.actors.ActorRepository
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider

/**
 * A real [SearchFirstPages] over the given repositories for ViewModel tests,
 * so the tests exercise the same first-page path production does. A tab under
 * test passes its own fake; the other two default to inert ones.
 */
internal fun testSearchFirstPages(
    scope: CoroutineScope,
    posts: SearchPostsRepository = FakeSearchPostsRepository(),
    actors: ActorRepository = FakeSearchActorsRepository(),
    feeds: SearchFeedsRepository = FakeSearchFeedsRepository(),
): SearchFirstPages =
    SearchFirstPages(
        postsRepository = posts,
        actorRepository = actors,
        feedsRepository = feeds,
        scope = scope,
        sessionStateProvider =
            object : SessionStateProvider {
                override val state = MutableStateFlow<SessionState>(SessionState.SignedOut)

                override suspend fun refresh() = Unit
            },
    )
