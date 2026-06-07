package net.kikin.nubecita.core.posting.internal

import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import io.github.kikin81.atproto.runtime.AtUri
import net.kikin.nubecita.core.posting.ComposerAttachment
import net.kikin.nubecita.core.posting.PostAudience
import net.kikin.nubecita.core.posting.PostingRepository
import net.kikin.nubecita.core.posting.ReplyRefs
import java.util.UUID
import javax.inject.Inject

/**
 * Bench-flavor [PostingRepository]: returns a synthetic AtUri with no network.
 *
 * The bench flavor fakes reads but not writes — `FakeXrpcClientProvider`
 * (`:core:auth` bench) throws on `authenticated()`, so the real
 * `DefaultPostingRepository` would fail every submit with
 * `ComposerError.Unauthorized`. This fake makes the offline bench build's
 * compose → submit flow (new post, reply, quote) succeed end to end, which is
 * what an on-device walkthrough of quote-compose needs.
 *
 * It ignores [attachments] (no blob upload) and the [audience] / [quote] gates
 * (no threadgate/postgate/embed records) — the composer's success path only
 * needs a non-null URI to fire `OnSubmitSuccess` and close. Each call mints a
 * random rkey so URIs stay unique within and across app launches (stateless —
 * no collisions even if a future surface persists created posts by URI).
 */
internal class BenchFakePostingRepository
    @Inject
    constructor() : PostingRepository {
        override suspend fun createPost(
            text: String,
            attachments: List<ComposerAttachment>,
            replyTo: ReplyRefs?,
            langs: List<String>?,
            audience: PostAudience,
            quote: StrongRef?,
        ): Result<AtUri> = Result.success(AtUri("at://did:plc:bench/app.bsky.feed.post/${UUID.randomUUID()}"))
    }
