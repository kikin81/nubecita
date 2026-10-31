package net.kikin.nubecita.core.posts

import net.kikin.nubecita.data.models.PostUi

/**
 * Bench-only lookup that lets other modules' fixtures answer
 * [PostRepository.getPost] under the `bench` flavor.
 *
 * The bench single-post fake only knows the post-detail fixtures in this
 * module. Posts rendered from another module's fixture — the feed's
 * `timeline.json` / `discover.json` — would otherwise resolve to the video
 * fallback, so tapping a feed image opened the media viewer on "This post has
 * no images". Owning modules contribute a source with
 * `@Binds @IntoSet` from their own `src/bench` Hilt module; the fixture data
 * stays where it is parsed.
 */
interface BenchPostSource {
    /** The fixture post whose id is [uri], or `null` if this source doesn't have it. */
    suspend fun findPost(uri: String): PostUi?
}
