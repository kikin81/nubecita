package net.kikin.nubecita.feature.bookmarks.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.analytics.PostSurface
import net.kikin.nubecita.core.bookmarks.BookmarkRepository
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.postinteractions.PostInteractionHandler
import net.kikin.nubecita.core.postinteractions.PostInteractionState
import net.kikin.nubecita.core.postinteractions.PostInteractionsCache
import net.kikin.nubecita.core.postinteractions.mergeInteractionState
import net.kikin.nubecita.data.models.PostUi
import javax.inject.Inject

/**
 * Presenter for the Bookmarks list. Fetches the signed-in user's
 * bookmarked posts via [BookmarkRepository.getBookmarks] on
 * construction, paginates on scroll, and keeps every rendered card in
 * sync with the shared [PostInteractionsCache] so a like / repost /
 * bookmark toggled on another surface reflects here without a refetch.
 *
 * Mirrors `:feature:search:impl/SearchPostsViewModel`, minus the
 * query/sort machinery — a bookmarks list has a single, stable data
 * source, so there is no `FetchKey` / `mapLatest` pipeline.
 *
 * `PostInteractionHandler by handler`: like / repost / bookmark / share /
 * overflow are delegation-forwarded to an injected
 * [net.kikin.nubecita.core.postinteractions.internal.DefaultPostInteractionHandler];
 * the screen collects `interactionEffects` via the shared
 * `rememberPostInteractions` helper. This VM's own effect channel is
 * reserved for screen-specific navigation (post detail, author profile).
 */
@HiltViewModel
internal class BookmarksViewModel
    @Inject
    constructor(
        private val repository: BookmarkRepository,
        private val cache: PostInteractionsCache,
        private val handler: PostInteractionHandler,
    ) : MviViewModel<BookmarksState, BookmarksEvent, BookmarksEffect>(BookmarksState()),
        PostInteractionHandler by handler {
        init {
            // Bind the handler to the Bookmarks surface and this VM's scope.
            handler.bind(PostSurface.Bookmarks, viewModelScope)

            // Mirror cache state into the rendered items so a like / repost /
            // bookmark toggled on another surface reflects here without a
            // refetch. Read + merge happen atomically inside setState so a
            // concurrent loadMore that lands between the snapshot read and the
            // state write can never be clobbered.
            viewModelScope.launch {
                cache.state.collect { snapshot ->
                    setState {
                        val status = loadStatus as? BookmarksLoadStatus.Loaded ?: return@setState this
                        copy(loadStatus = status.copy(items = status.items.applyInteractions(snapshot)))
                    }
                }
            }

            loadFirstPage()
        }

        override fun handleEvent(event: BookmarksEvent) {
            when (event) {
                BookmarksEvent.LoadMore -> loadMore()
                BookmarksEvent.Retry -> loadFirstPage()
                is BookmarksEvent.PostTapped ->
                    sendEffect(BookmarksEffect.NavigateToPost(event.uri))
                is BookmarksEvent.OnAuthorTapped ->
                    sendEffect(BookmarksEffect.NavigateToProfile(event.handle))
            }
        }

        private var loadJob: Job? = null

        private fun loadFirstPage() {
            // Single-flight: a rapid second Retry cancels the prior in-flight
            // load so two responses can't race to set the final state.
            loadJob?.cancel()
            setState { copy(loadStatus = BookmarksLoadStatus.InitialLoading) }
            loadJob =
                viewModelScope.launch {
                    repository
                        .getBookmarks(cursor = null)
                        .onSuccess { page ->
                            cache.seed(page.posts)
                            // Dedupe by post id (the AT-URI the LazyColumn keys on): a
                            // bookmarks page could echo the same post twice, and duplicate
                            // LazyColumn keys crash Compose.
                            val deduped = page.posts.distinctBy { it.id }.toImmutableList()
                            setState {
                                // Project the live cache INSIDE setState (reading
                                // cache.state.value fresh) so a cache emission that lands
                                // during the fetch can't be clobbered by a value computed
                                // from a stale snapshot.
                                val merged = deduped.applyInteractions(cache.state.value)
                                copy(
                                    loadStatus =
                                        if (merged.isEmpty()) {
                                            BookmarksLoadStatus.Empty
                                        } else {
                                            BookmarksLoadStatus.Loaded(
                                                items = merged,
                                                nextCursor = page.cursor,
                                                endReached = page.cursor == null,
                                            )
                                        },
                                )
                            }
                        }.onFailure { throwable ->
                            setState {
                                copy(loadStatus = BookmarksLoadStatus.InitialError(throwable.toBookmarksError()))
                            }
                        }
                }
        }

        private fun loadMore() {
            val status = uiState.value.loadStatus
            if (status !is BookmarksLoadStatus.Loaded) return
            if (status.endReached) return
            if (status.isAppending) return

            val cursor = status.nextCursor
            setState {
                // Re-read inside setState so flipping isAppending can't clobber a
                // cache emission that landed after the guard checks above.
                val current = loadStatus as? BookmarksLoadStatus.Loaded ?: return@setState this
                copy(loadStatus = current.copy(isAppending = true))
            }
            viewModelScope.launch {
                repository
                    .getBookmarks(cursor = cursor)
                    .onSuccess { page ->
                        cache.seed(page.posts)
                        setState {
                            // Read + append INSIDE setState so a concurrent cache merge
                            // can't be clobbered by a value computed from a stale read.
                            // Existing items win on dedupe so order stays stable.
                            val current = loadStatus as? BookmarksLoadStatus.Loaded ?: return@setState this
                            val appended =
                                (current.items + page.posts)
                                    .distinctBy { it.id }
                                    .toImmutableList()
                                    .applyInteractions(cache.state.value)
                            copy(
                                loadStatus =
                                    current.copy(
                                        items = appended,
                                        nextCursor = page.cursor,
                                        endReached = page.cursor == null,
                                        isAppending = false,
                                    ),
                            )
                        }
                    }.onFailure { throwable ->
                        setState {
                            // Clear isAppending against the freshest state so a cache
                            // emission during the failed append isn't clobbered.
                            val current = loadStatus as? BookmarksLoadStatus.Loaded ?: return@setState this
                            copy(loadStatus = current.copy(isAppending = false))
                        }
                        sendEffect(BookmarksEffect.ShowAppendError(throwable.toBookmarksError()))
                    }
            }
        }
    }

/**
 * Project a [PostInteractionsCache] snapshot onto the bookmark list.
 * Replaces interaction fields (like / repost / bookmark counts and viewer
 * flags) on every [PostUi] whose id appears in [interactions]; returns the
 * same instance otherwise, preserving reference equality so LazyColumn can
 * skip recomposition for unchanged items.
 */
private fun ImmutableList<PostUi>.applyInteractions(
    interactions: PersistentMap<String, PostInteractionState>,
): ImmutableList<PostUi> =
    map { post -> interactions[post.id]?.let { post.mergeInteractionState(it) } ?: post }
        .toImmutableList()
