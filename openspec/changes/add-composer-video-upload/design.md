# Design — composer video upload

## Context

Nubecita can play Bluesky video in four places and post it in none. Closing that gap is
not a matter of adding a MIME type to the picker: Bluesky video bypasses
`com.atproto.repo.uploadBlob` entirely and runs through a separate service with its own
host, its own auth scheme, and an asynchronous transcode job.

Verified flow (sources: [docs.bsky.app video tutorial](https://docs.bsky.app/docs/tutorials/video),
[mozzius direct-upload gist](https://gist.github.com/mozzius/5cbbd15e12cdc0cb1d0d992b7c3b1d0f)):

1. `com.atproto.server.getServiceAuth` on the user's **PDS** — `aud = did:web:<pds-host>`,
   `lxm = "com.atproto.repo.uploadBlob"`, `exp = now + 1800`.
2. `POST https://video.bsky.app/xrpc/app.bsky.video.uploadVideo?did=…&name=…` —
   `Authorization: Bearer <serviceAuthJwt>`, `Content-Type: video/mp4`, explicit
   `Content-Length`.
3. Poll `GET https://video.bsky.app/xrpc/app.bsky.video.getJobStatus?jobId=…` until
   `jobStatus.blob` is present.
4. `createRecord` with `app.bsky.embed.video { video, alt, aspectRatio }`.

Two parameters in step 1 are actively counter-intuitive and were confirmed against both
sources before being written down: `aud` is the user's own PDS, **not**
`did:web:video.bsky.app`; and `lxm` is the blob-upload method, **not** the video method.

### SDK state — `atproto-kotlin` 9.7.5

| Piece | Status |
|---|---|
| `VideoService`, `JobStatus`, `GetUploadLimitsResponse`, `app.bsky.embed.Video` | generated and usable |
| `com.atproto.server.getServiceAuth` | **absent** — `generator/lexicons/com/atproto/server/` holds only createAccount, createSession, defs, deleteSession, describeServer, getSession, refreshSession |
| `VideoService.uploadVideo` | generated with `NoXrpcParams`, so it cannot send the required `did` / `name` query parameters |
| `XrpcClient` | single `baseUrl`, one shared authenticated `HttpClient`; no per-call host override, no upload-progress callback |

### Existing code this lands against

- `core/posting/.../internal/DefaultPostingRepository.kt:471` — `resolveEmbed`, which
  already encodes the images / gallery / external precedence as a single `when`.
- `core/posting/.../internal/ComposerEmbedIntent.kt:37` — `(images, quote, external)`.
- `core/posting/.../ComposerAttachment.kt` — `(uri, mimeType, alt)`.
- `core/image/.../ImagePicker.kt` — the picker abstraction, currently images-only.
- Media3 is pinned at `1.10.1` for playback; `media3-transformer` is not yet a dependency.

## Goals / Non-Goals

**Goals:**

- A user can attach one video to a post, see honest progress, and publish it.
- Real phone recordings succeed. A design that only works for pre-compressed clips is a
  demo, not a feature.
- Every failure the pipeline can produce maps to a message the user can act on.
- The composer stays on the MVI baseline; the pipeline's three-host complexity does not
  leak past one repository interface.
- The atproto SDK change is the smallest one that is upstream-faithful.

**Non-Goals:**

- Captions/subtitles, in-app trimming, in-composer recording, video outside the post
  composer, resumable upload, multi-video posts. All enumerated with reasons in
  `proposal.md`.
- Optimising transcode speed beyond "acceptable on a mid-range device". Correctness and
  bounded output size come first.

## Decisions

### D1 — Own the pipeline in `:core:video-upload`, exposed as one cold `Flow`

The pipeline spans three transports, two auth schemes, a transcoder, and a polling loop.
Exposing that as `upload(uri): Flow<VideoUploadState>` means the composer sees a linear
progression and a terminal result, and cancellation is just coroutine cancellation.

*Alternative — extend `:core:posting`.* Rejected: `:core:posting` is a thin wrapper over
SDK calls, and folding a transcoder plus a bespoke HTTP client into it would triple its
dependency surface for one embed type. Video also has a consumer beyond posting later
(profile videos, DMs), which a separate module serves without a further split.

*Alternative — a WorkManager job.* Rejected for v1: the upload's lifetime is the
composer's lifetime, and the user is watching it. WorkManager buys survival across process
death, which matters only once drafts exist (`nubecita-4ok`) — and the standing project
constraint is that background work must not fight Doze. Revisit with drafts, not before.

### D2 — Check upload limits before transcoding, not after

`getUploadLimits` is cheap; transcoding is the most expensive thing the app will ever do
to the battery and the thermal budget. Both real rejection causes — unverified account
email and exhausted daily quota — are knowable up front. Ordering the stages
limits → compress → upload is therefore not stylistic; re-encoding a 3-minute clip and
*then* learning the account cannot post video would be a straightforward defect.

### D3 — Compress with Media3 Transformer, bitrate derived from duration

```
targetBitrate =
    if (durationSeconds > 0) min(DEFAULT_BITRATE, (SIZE_BUDGET_BYTES * 8) / durationSeconds)
    else DEFAULT_BITRATE
```

with the longest edge capped at 1080 px, H.264 video, AAC audio.

`durationSeconds` comes from `MediaMetadataRetriever`, which returns null for a
corrupt or unreadable container — so the divisor must be guarded. But falling
back to `DEFAULT_BITRATE` silently abandons the size bound this decision exists
to provide. The guard is therefore paired with a **post-transcode size check**:
if the encoded file still exceeds the cap, the pipeline fails with
`CompressionFailed` rather than starting an upload the service will reject.
That check also turns the bound from *computed* into *enforced*, which is worth
having even when the duration is known.

The arithmetic is the whole point. 1080p30 phone video runs around 20 Mbps, so three
minutes is roughly 450 MB against a 100 MB cap — most real recordings fail without
compression, which is why "reject oversized files" was rejected as a shipping strategy.
And a *fixed* target bitrate cannot work either: any value high enough to look acceptable
on a 15-second clip overflows the cap near the duration limit. Deriving the bitrate from
duration makes the bound structural rather than aspirational.

*Alternative — trim-only, no re-encode.* Rejected: muxing a sub-range leaves bitrate
untouched, so a high-bitrate 4K clip exceeds the cap even trimmed to a few seconds.

*Alternative — server-side only.* Not available; the 100 MB cap is enforced at upload.

### D4 — Raw Ktor for the upload leg; the SDK for everything else

The `uploadVideo` request is the one call in the app that targets a non-PDS host with a
non-DPoP credential, and it is the only one that needs byte-level progress. Ktor's
`onUpload` provides that; `XrpcClient.procedure` does not expose it, and `XrpcClient` is
constructed around a single `baseUrl` with an `HttpClient` that installs OAuth/DPoP
credentials — sending those to `video.bsky.app` would be wrong even if it were possible.

`getJobStatus` and `getUploadLimits` still go through the SDK, via a second
`XrpcClient(baseUrl = "https://video.bsky.app")`. They are ordinary typed queries and
there is no reason to hand-roll them.

That client is **not** built on a bare `HttpClient`. `getUploadLimits` is documented
"for the authenticated user" and the video service will reject it unauthenticated, but
`XrpcClient` takes credentials from its `HttpClient`'s plugins and has no per-call header
parameter. So the client is constructed with a `defaultRequest` block that attaches
`Authorization: Bearer <serviceAuthToken>` — the same token minted for the upload leg,
supplied lazily so a single 30-minute token covers the probe, the upload, and the poll
loop. It must not reuse the PDS `HttpClient`, whose DPoP-bound OAuth credentials are
wrong for this host.

So the exception to "all networking goes through the SDK" is exactly one function, and
`design.md` plus the capability spec both say why. This is deliberate scoping, not drift.

### D5 — Add only `getServiceAuth` to the SDK

One lexicon JSON in `generator/lexicons/com/atproto/server/getServiceAuth.json`, faithful
to upstream, then regenerate. Nothing else is needed, because D4 routes the one call the
SDK models badly around the SDK entirely.

*Alternative — also patch the `uploadVideo` lexicon to declare `did` / `name`.* Rejected:
upstream does not declare them (the official client appends them manually), so adding them
would make our fork diverge from the canonical lexicon to enable a code path D4 does not
use. Divergence should buy something.

Dev loop is the established cross-repo one: publish `:models` to mavenLocal with the
signing-disabled init script, consume with `-PuseMavenLocal=true`, then bump the pin to a
real release before the nubecita PR merges.

### D6 — Eager upload on selection

Compression plus upload plus server processing is plausibly a minute on a large clip.
Behind a submit-time progress dialog that is a minute of blocked composer; started at
selection it overlaps with the user typing, and Post is usually instant.

The cost is a real state machine: cancel on remove, cancel on discard, restart on retry,
and a defined interaction with drafts. Those are enumerated as requirements in the
`feature-composer` delta rather than left implicit.

### D7 — Video outranks all other media in `resolveEmbed`

`resolveEmbed` gains a video branch above images. The composer's mutual-exclusion rules
make the conflict unreachable through the UI, so the precedence is defence in depth: if a
reachable state ever produced both, dropping the video — the most expensive thing the user
contributed — would be the worst available outcome.

### D8 — Rotation-aware aspect ratio

`MediaMetadataRetriever` reports a portrait recording as 1920×1080 with
`METADATA_KEY_VIDEO_ROTATION = 90`. Width and height must be swapped for 90/270. Getting
this wrong letterboxes every portrait video in *every* AT Protocol client that reads the
record, not just in ours — the aspect ratio is published data, not a local rendering hint.

## Risks / Trade-offs

- **Device-specific encoder failures.** `MediaCodec` behaviour varies by chipset and some
  devices reject otherwise valid configurations. → `CompressionFailed` is a distinct error
  with a retry affordance; verify on the Pixel Fold in both postures, and log the codec
  and configuration on failure so field reports are diagnosable.
- **Transcode is slow and hot on mid-range devices.** → Progress is honest and per-stage;
  D2 guarantees we never transcode for a post that cannot be published.
- **`video.bsky.app` is undocumented surface that can change.** The `did`/`name` query
  parameters are not in the lexicon and are known only from the official client. → Isolate
  in one module behind one interface; `UploadFailed` carries the server response.
- **Unknown job states.** The lexicon says any unrecognised state means "still running",
  so a strict enum would fail on a state Bluesky adds later. → Treat unknown as
  in-progress; only an explicit failure state fails the pipeline.
- **100 MB / ~3 min limits are not lexicon-enforced constants.** The 100 MB figure comes
  from the `app.bsky.embed.Video` KDoc, which also records that it used to be 50 MB. →
  Keep both as named constants in one file; assume they will move again.
- **Collision with drafts (`nubecita-4ok`).** A draft holding a half-uploaded video is
  undefined today. → Flagged in `proposal.md`; whichever change lands second owns the
  resolution. This change deliberately does not pre-build for it (D1's WorkManager note).
- **Bench flavor.** The composer must keep working offline in the bench build. → Ship a
  bench fake alongside the real binding; adding an enum case without updating bench-only
  exhaustive `when`s fails CI Lint while passing a local `productionDebug` build.

## Migration Plan

Slice 1 (`getServiceAuth` in `atproto-kotlin`) blocks everything else and lands first;
during development the rest consumes it via mavenLocal, and the version pin is bumped to a
published release before the nubecita PR merges. There is no data migration and no stored
state, so rollback is reverting the feature commits — the composer's video slot is additive
and no existing post record shape changes.

## Open Questions

- **Duration ceiling.** The service limit is understood to be about 3 minutes but is not
  stated in the lexicon. Confirm empirically against a real account and encode the answer
  as a named constant with the observed value and date. Until confirmed, reject
  conservatively and let `NotPermitted` carry the server's own message.
- **`DEFAULT_BITRATE` and `SIZE_BUDGET_BYTES` values.** Pick from device testing on real
  recordings; the formula is settled, the constants are not.
- **Retry granularity.** Retry currently restarts from the limits check. Resuming from the
  compressed artifact when only the upload leg failed would save a re-encode; deferred
  until device testing shows whether upload-only failures are common enough to matter.
