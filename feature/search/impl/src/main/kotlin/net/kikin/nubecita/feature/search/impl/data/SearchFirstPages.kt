package net.kikin.nubecita.feature.search.impl.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.actors.ActorRepository
import net.kikin.nubecita.core.actors.ActorSearchPage
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The first page of each search tab, shared between everything that asks for
 * it. A tab and the summary of the same search resolve to the same
 * [FirstPageCache] entry, so the network is hit once. Later pages go straight
 * to the repositories.
 *
 * Every cache is cleared when the signed-in account changes or signs out, so
 * one account's results never reach another. [version] bumps on every
 * refresh and clear, so an observer holding a page can tell it is stale.
 */
@Singleton
internal class SearchFirstPages
    @Inject
    constructor(
        private val postsRepository: SearchPostsRepository,
        private val actorRepository: ActorRepository,
        private val feedsRepository: SearchFeedsRepository,
        @ApplicationScope scope: CoroutineScope,
        sessionStateProvider: SessionStateProvider,
    ) {
        private data class PostsKey(
            val query: String,
            val filters: PostSearchFilters,
        )

        private val postCache = FirstPageCache<PostsKey, SearchPostsPage>(scope)
        private val peopleCache = FirstPageCache<String, ActorSearchPage>(scope)
        private val feedCache = FirstPageCache<String, SearchFeedsPage>(scope)

        private val _version = MutableStateFlow(0)

        /** Bumps on every refresh and clear. */
        val version: StateFlow<Int> = _version

        init {
            scope.launch {
                // The first value is the account the app starts with; only a
                // change after that invalidates anything.
                sessionStateProvider.state
                    .map { (it as? SessionState.SignedIn)?.did }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { clear() }
            }
        }

        suspend fun posts(
            query: String,
            filters: PostSearchFilters,
            refresh: Boolean = false,
        ): Result<SearchPostsPage> {
            val fetch: suspend () -> Result<SearchPostsPage> = {
                postsRepository.searchPosts(query = query, filters = filters, cursor = null)
            }
            val key = PostsKey(query, filters)
            return if (refresh) postCache.refresh(key, fetch).also { bump() } else postCache.get(key, fetch)
        }

        suspend fun people(
            query: String,
            refresh: Boolean = false,
        ): Result<ActorSearchPage> {
            val fetch: suspend () -> Result<ActorSearchPage> = {
                actorRepository.searchActors(query = query, cursor = null)
            }
            return if (refresh) peopleCache.refresh(query, fetch).also { bump() } else peopleCache.get(query, fetch)
        }

        suspend fun feeds(
            query: String,
            refresh: Boolean = false,
        ): Result<SearchFeedsPage> {
            val fetch: suspend () -> Result<SearchFeedsPage> = {
                feedsRepository.searchFeeds(query = query, cursor = null)
            }
            return if (refresh) feedCache.refresh(query, fetch).also { bump() } else feedCache.get(query, fetch)
        }

        private fun clear() {
            postCache.clear()
            peopleCache.clear()
            feedCache.clear()
            bump()
        }

        private fun bump() {
            _version.update { it + 1 }
        }
    }
