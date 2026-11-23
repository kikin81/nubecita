package net.kikin.nubecita.feature.search.impl.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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
 * Every request carries the [net.kikin.nubecita.feature.search.impl.data.SubmittedSearch.submission]
 * it belongs to and that is part of the cache key: an explicit submit is a new
 * search even when the text is unchanged, so it fetches fresh.
 *
 * Every cache is cleared when the signed-in account changes or signs out, so
 * one account's results never reach another. [version] bumps on every
 * refresh and clear, so an observer holding a page can tell it is stale.
 *
 * The `refresh` parameters replace an entry instead of reusing it, as the design calls for;
 * no caller uses them yet (the result tabs have no pull-to-refresh, and a re-submitted search
 * gets a new submission number instead).
 */
@Singleton
internal class SearchFirstPages
    @Inject
    constructor(
        private val postsRepository: SearchPostsRepository,
        private val actorRepository: ActorRepository,
        private val feedsRepository: SearchFeedsRepository,
        private val packsRepository: SearchStarterPacksRepository,
        @ApplicationScope scope: CoroutineScope,
        sessionStateProvider: SessionStateProvider,
    ) {
        /**
         * Every cache key carries the signed-in did (null when signed out) read
         * at call time, so a page fetched for one account is never returned
         * once the session has moved on, even before [clear] has run.
         */
        private data class PostsKey(
            val did: String?,
            val query: String,
            val filters: PostSearchFilters,
            val submission: Int,
        )

        private data class QueryKey(
            val did: String?,
            val query: String,
            val submission: Int,
        )

        private val session = sessionStateProvider.state
        private val postCache = FirstPageCache<PostsKey, SearchPostsPage>(scope)
        private val peopleCache = FirstPageCache<QueryKey, ActorSearchPage>(scope)
        private val feedCache = FirstPageCache<QueryKey, SearchFeedsPage>(scope)
        private val packCache = FirstPageCache<QueryKey, SearchStarterPacksPage>(scope)

        private fun currentDid(): String? = (session.value as? SessionState.SignedIn)?.did

        private val _version = MutableStateFlow(0)

        /** Bumps on every refresh and clear. No caller refreshes yet, so today only a clear bumps it. */
        val version: StateFlow<Int> = _version

        init {
            // The baseline is read now, and the collector starts undispatched,
            // so a change between construction and the first collection is seen.
            var knownDid = currentDid()
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                session
                    .map { (it as? SessionState.SignedIn)?.did }
                    .distinctUntilChanged()
                    .collect { did ->
                        if (did != knownDid) {
                            knownDid = did
                            clear()
                        }
                    }
            }
        }

        suspend fun posts(
            query: String,
            filters: PostSearchFilters,
            submission: Int,
            refresh: Boolean = false,
        ): Result<SearchPostsPage> {
            val fetch: suspend () -> Result<SearchPostsPage> = {
                postsRepository.searchPosts(query = query, filters = filters, cursor = null)
            }
            val key = PostsKey(currentDid(), query, filters, submission)
            return if (refresh) postCache.refresh(key, fetch).also { bump() } else postCache.get(key, fetch)
        }

        suspend fun people(
            query: String,
            submission: Int,
            refresh: Boolean = false,
        ): Result<ActorSearchPage> {
            val fetch: suspend () -> Result<ActorSearchPage> = {
                actorRepository.searchActors(query = query, cursor = null)
            }
            val key = QueryKey(currentDid(), query, submission)
            return if (refresh) peopleCache.refresh(key, fetch).also { bump() } else peopleCache.get(key, fetch)
        }

        suspend fun feeds(
            query: String,
            submission: Int,
            refresh: Boolean = false,
        ): Result<SearchFeedsPage> {
            val fetch: suspend () -> Result<SearchFeedsPage> = {
                feedsRepository.searchFeeds(query = query, cursor = null)
            }
            val key = QueryKey(currentDid(), query, submission)
            return if (refresh) feedCache.refresh(key, fetch).also { bump() } else feedCache.get(key, fetch)
        }

        suspend fun packs(
            query: String,
            submission: Int,
            refresh: Boolean = false,
        ): Result<SearchStarterPacksPage> {
            val fetch: suspend () -> Result<SearchStarterPacksPage> = {
                packsRepository.searchStarterPacks(query = query, cursor = null)
            }
            val key = QueryKey(currentDid(), query, submission)
            return if (refresh) packCache.refresh(key, fetch).also { bump() } else packCache.get(key, fetch)
        }

        private fun clear() {
            postCache.clear()
            peopleCache.clear()
            feedCache.clear()
            packCache.clear()
            bump()
        }

        private fun bump() {
            _version.update { it + 1 }
        }
    }
