package net.kikin.nubecita.core.feedmapping

import io.github.kikin81.atproto.app.bsky.feed.PostView
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins `ViewerStateUi.isOwnPost`, the signal that decides whether the overflow
 * menu offers Delete.
 *
 * Unlike its neighbours on `ViewerStateUi` this is not a server-computed flag —
 * the appview has no "this is yours" bit — so it is derived here by comparing
 * the post's author DID against the session DID. Two properties matter, and
 * they are not symmetric:
 *
 * - A false negative hides Delete on the user's own post. Annoying.
 * - A false positive offers Delete on somebody else's post. The request would
 *   be refused by the PDS, but the affordance is a lie and the confirm dialog
 *   would claim an action the user cannot take.
 *
 * So the absent-session and mismatched-DID cases are tested as deliberately as
 * the happy path.
 */
internal class PostOwnershipMappingTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    @Test
    fun `a post authored by the viewer is marked own`() {
        val view = decodePostView(POST)

        val post = requireNotNull(view.toPostUiCore(viewerDid = "did:plc:fake"))

        assertTrue(post.viewer.isOwnPost)
    }

    @Test
    fun `a post authored by somebody else is not marked own`() {
        val view = decodePostView(POST)

        val post = requireNotNull(view.toPostUiCore(viewerDid = "did:plc:someone-else"))

        assertFalse(post.viewer.isOwnPost)
    }

    /**
     * The fail-closed default. A projection path that forgets to supply the
     * viewer DID loses the Delete affordance rather than offering it on a post
     * the viewer does not own — the safe direction for a destructive action.
     */
    @Test
    fun `omitting the viewer did fails closed`() {
        val view = decodePostView(POST)

        val post = requireNotNull(view.toPostUiCore())

        assertFalse(post.viewer.isOwnPost)
    }

    /** Signed out: nobody owns anything, so nothing is offered. */
    @Test
    fun `a null viewer did fails closed`() {
        val view = decodePostView(POST)

        val post = requireNotNull(view.toPostUiCore(viewerDid = null))

        assertFalse(post.viewer.isOwnPost)
    }

    /**
     * DIDs are compared whole. A viewer DID that merely prefixes the author's
     * must not match — `did:plc:fake` vs `did:plc:fakeaccount` are different
     * accounts, and a `startsWith`-style comparison would hand one user the
     * Delete button on the other's posts.
     */
    @Test
    fun `a did that only prefixes the author did is not a match`() {
        val view = decodePostView(POST)

        val post = requireNotNull(view.toPostUiCore(viewerDid = "did:plc:fak"))

        assertFalse(post.viewer.isOwnPost)
    }

    private fun decodePostView(jsonString: String): PostView = json.decodeFromString(PostView.serializer(), jsonString)

    private companion object {
        /** Author `did:plc:fake`, matching the module's other inline fixtures. */
        const val POST = """
            {
              "uri": "at://did:plc:fake/app.bsky.feed.post/p1",
              "cid": "bafyfake",
              "author": {
                "did": "did:plc:fake",
                "handle": "fake.bsky.social"
              },
              "record": {
                "${'$'}type": "app.bsky.feed.post",
                "text": "hello",
                "createdAt": "2026-01-01T12:00:00.000Z"
              },
              "indexedAt": "2026-01-01T12:00:00.000Z"
            }
        """
    }
}
