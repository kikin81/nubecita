// ============================================================
// Nubecita — fake data (mirrors data.jsx in the HTML UI kit)
// Replace with AT Proto models when you wire up the network layer.
// ============================================================
package app.nubecita.data

data class QuoteCardData(
    val author: String,
    val timeAgo: String,
    val title: String,
    val body: String,
)

data class Post(
    val id: String,
    val name: String,
    val handle: String,
    val hue: Int,
    val timeAgo: String,
    val body: String,
    val replies: Int = 0,
    val reposts: Int = 0,
    val likes: Int = 0,
    val liked: Boolean = false,
    val reposted: Boolean = false,
    val following: Boolean = true,
    val repostedBy: String? = null,
    /** "linear-gradient(...)" style placeholder; null = no image. */
    val imageGradient: List<Long>? = null,
    /** Hashtags rendered inline above the actions. */
    val tags: List<String> = emptyList(),
    /** Embedded quote card. */
    val quoteCard: QuoteCardData? = null,
    /** Self-thread metadata. */
    val threadId: String? = null,
    val connectAbove: Boolean = false,
    val connectBelow: Boolean = false,
)

object FakeData {
    val posts: List<Post> = listOf(
        Post("p1","Alice Chen","alice.nubecita.app",210,"3h",
            "The thing about building a Bluesky client in 2026 is you realize how much of the web we gave up trying to fix. Small clients, good defaults, readable text. ☁︎",
            replies = 12, reposts = 4, likes = 86, liked = true, following = true),
        Post("p2","Marco Díaz","marco.bsky.social",35,"4h",
            "Mastodon has an onboarding problem. Bluesky has a discoverability problem. Both are fixable. Here's what we changed in Nubecita this week →",
            replies = 28, reposts = 14, likes = 210, following = false,
            imageGradient = listOf(0xFFA6C8FF, 0xFF6250B0)),
        Post("p3","Sana Okafor","sana.nubecita.app",320,"5h",
            "Tiny victory: got adaptive layouts working on the Fold 6. Feed + thread + composer all open at once. No more losing context switching between them.",
            replies = 3, reposts = 18, likes = 124, repostedBy = "Alice Chen"),
        Post("p4","Devon Park","devon.dev",150,"7h",
            "Shoutout to whoever decided pill buttons in M3 Expressive. The press-squish haptic feels right every single time.",
            replies = 6, reposts = 2, likes = 47, following = false),
        Post("p5","Priya Ramanathan","priya.bsky.social",270,"9h",
            "Reading recs for the plane: a novel, a book of essays, or the Bluesky custom feeds documentation? All three, obviously.",
            replies = 15, reposts = 3, likes = 91, liked = true, following = false),
    )

    val replies: List<Post> = listOf(
        Post("r1","Marco Díaz","marco.bsky.social",35,"2h",
            "This is exactly it. The readable-text part especially — it's not just a font choice, it's a whole posture.",
            replies = 2, likes = 18),
        Post("r2","Sana Okafor","sana.nubecita.app",320,"2h",
            "Can't wait to try this on my Fold. The three-pane view is going to be incredible for long threads.",
            replies = 1, reposts = 1, likes = 34, liked = true),
        Post("r3","Devon Park","devon.dev",150,"1h",
            "What's your default feed set up like? Asking for a friend who is me.",
            likes = 7),
    )

    data class Feed(val id: String, val name: String, val icon: String)
    val feeds: List<Feed> = listOf(
        Feed("fyp","For you","auto_awesome"),
        Feed("following","Following","group"),
        Feed("art","Art","palette"),
        Feed("tech","Tech","memory"),
        Feed("books","Books","menu_book"),
    )

    /**
     * A self-thread: same author, multiple posts linked by a connector line
     * through the avatar gutter, with a "View full thread" fold between
     * the kicker and continuation.
     *
     * Render with: ThreadPost(...) for `Post` items, ThreadFold(...) for
     * `ThreadItem.Fold` items.
     */
    sealed interface ThreadItem {
        data class PostItem(val post: Post) : ThreadItem
        data class Fold(val id: String, val count: Int) : ThreadItem
    }

    val selfThread: List<ThreadItem> = listOf(
        ThreadItem.PostItem(Post(
            id = "t1", threadId = "matt-darkroom",
            name = "Matt — Play DARK ROOMS", handle = "pmrd.net",
            hue = 0, timeAgo = "10h",
            body = "I really don't know what to do with this kind of feedback. The puzzle isn't that hard, I really don't want to put yellow paint everywhere and make things too easy — I want people to feel smart when they solve them. People had no issues with Myst and Riven lol",
            tags = listOf("#indiegame", "#gamedev", "#darkrooms"),
            quoteCard = QuoteCardData(
                author = "ReploidArmada",
                timeAgo = "21 hours ago",
                title = "Neat concept, but having no feedback while playing hurts",
                body = "I've gotten through four loops of the first five rooms, and just about nothing is coming together. I figured out the first minor task, I think??? Getting the clue to appear on the bed? But there's no feedback I can tell from the game whether something worked or didn't work.",
            ),
            replies = 10, reposts = 3, likes = 24,
            connectBelow = true,
        )),
        ThreadItem.Fold(id = "t-fold", count = 2),
        ThreadItem.PostItem(Post(
            id = "t2", threadId = "matt-darkroom",
            name = "Matt — Play DARK ROOMS", handle = "pmrd.net",
            hue = 0, timeAgo = "1h",
            body = "But my main issue was that it did not feel like you were making progress after a while.",
            replies = 3, likes = 1,
            connectAbove = true, connectBelow = true,
        )),
        ThreadItem.PostItem(Post(
            id = "t3", threadId = "matt-darkroom",
            name = "Matt — Play DARK ROOMS", handle = "pmrd.net",
            hue = 0, timeAgo = "59m",
            body = "In my game you gradually unlock rooms after solving survival horror style puzzles. I think I'm just making the game I expected Blue Prince would be.",
            connectAbove = true,
        )),
    )
}
