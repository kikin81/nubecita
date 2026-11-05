# On-device post translation

Design doc · 2026-11-05 · epic `nubecita-s6xk`

## Intent

A reader scrolling their timeline hits a post in a language they don't read. Today
Nubecita's only answer is the overflow menu's "Translate post", which deep-links out
to the Google Translate app — and in practice arrives there with no text, so the
reader gets an empty translator and has lost their place in the feed.

The target is the thing X ships and the thing the reader actually wants: the post
translates **in place**, in the feed, without leaving the app, without a network
round-trip, and without the reader asking every time.

The why behind the why: Nubecita's hard requirement is 120hz scrolling. Any feature
that makes the timeline stutter is worse than no feature. The whole shape of this
design — translate only when the list has settled, never during a fling — exists to
serve that constraint, and a spike (below) measured that it holds.

## What was measured

A throwaway spike wired ML Kit into the live feed on a **Pixel 11 Pro (`grizzly`),
120Hz panel, bench flavor**, with a fixture timeline carrying Japanese, German,
French, Portuguese and Italian posts. It has been reverted; these are its numbers.

### Scrolling cost: none measurable

Identical 14-swipe scroll, translation off vs on, `dumpsys gfxinfo`:

| | Frames | Janky | p50 | p90 | p95 | p99 |
|---|---|---|---|---|---|---|
| Control (off) | 1644 | **2.01%** | 5 ms | 8 ms | 12 ms | 23 ms |
| Translation on | 1638 | **1.77%** | 5 ms | 8 ms | 11 ms | 23 ms |
| On, second run | 969 | **1.65%** | 5 ms | 8 ms | 12 ms | 26 ms |

The "on" runs include two cold model downloads happening concurrently with the
scroll. The difference is inside the run-to-run noise. **The scroll-idle gate works.**

CPU: ~0% idle, brief bursts to ~110% of one core while a batch translates. Process
RSS reached ~400 MB with five translators loaded — translators must be closed when
idle (see D6).

### Latency

| Operation | Cost |
|---|---|
| Translate one post, warm model | 36–311 ms (36 ms avg for short JA posts) |
| Cold model download (Wi-Fi) | 2.3 s (de) – 3.2 s (ja) |
| A 24-post thread, warm, sequential | 877 ms total (36 ms/post) |

The thread number matters: translating a whole post-detail thread is cheap. The
`:api` seam must not foreclose it even though v1 ships feed-only.

### Storage: far worse than advertised

Five languages consumed **241.8 MB**, in `no_backup/com.google.mlkit.translate.models/`.
ML Kit downloads **both directions** of a pair even when only one is requested.

| Language | On disk |
|---|---|
| Japanese | 62.0 MB |
| Portuguese | 47.0 MB |
| German | 45.6 MB |
| French | 44.3 MB |
| Italian | 42.9 MB |

This is app data, not a cache we control, and it shows up in Android's storage
screen under Nubecita. It is in `no_backup/`, so it does not inflate cloud backups.
**~45 MB per language, 62 MB for Japanese — not the ~30 MB the ML Kit docs imply.**
Storage is therefore a first-class part of the UI, not a footnote (D6).

### Language identification is not trustworthy alone

The real-world post that started this — a German greeting followed by nine English
hashtags — was identified by ML Kit as **English** and skipped. A reader would see
no translate affordance on a post they can't read. This is exactly why the record's
`langs` has to participate (D4).

### Translation mangles facets — and no text-level trick fixes it

Forcing that post through `de→en`:

```
IN : Guten Morgen, happy day 🌞
     #ColorADay #YellowThu #PalacesandGardens #Autumn 🍂💛 #woodland #leaves #scape #nature #photography
OUT: Good Morning, Happy Day 🌞 #coloraday #Yellowthu #palaceandGarden #Autumn 🍂💛 #woodland #Leaves #scape #Nature #Photography
```

`#PalacesandGardens` became `#palaceandGarden` — the tag text itself changed, not
just its case. Newlines collapsed to spaces. Emoji survived untouched.

Probing mentions and URLs directly:

```
de  IN : Hallo @alice.bsky.social, schau https://example.com/a/b?c=1 an. #HalloWelt
de  OUT: Hello @ alice.bsky.social, look https://example.com/a/b?c=1. #Hallowelt

ja  IN : おはよう @alice.bsky.social さん、https://example.com/a/b を見て。#おはよう
ja  OUT: Good morning @ alice.bsky.social, https: //example.com/a/b. # Good morning
```

Mentions gain a space after `@` in both languages. Japanese **breaks the URL**
(`https: //`) and **translates the hashtag text**.

The obvious escape — substitute placeholder tokens before translating, put the
originals back after — was probed and **does not work**:

```
de  IN : Guten Morgen! ⟦0⟧ und ⟦1⟧ sind schön. Schau mal ⟦2⟧ an.
de  OUT: Good morning! ⟦0⟧ and ⟦1⟧ are beautiful. Look at ⟦2⟧.        ← survives

ja  IN : おはよう ⟦0⟧ と ⟦1⟧ を見てください。
ja  OUT: Please look at Ogaki 0 and ⟦1⟧ 1.                            ← destroyed
```

`{0}` behaves the same way. **Placeholders are not language-safe**, so the facet
strategy cannot rest on them (D5).

### Output quality is uneven

`de→en` and `pt→en` read well. `ja→en` is noticeably worse than cloud Google
Translate — the Japanese post that prompted this came back only roughly coherent.
That is the price of on-device and it is the right trade here (D1), but the UI must
not present a translation as authoritative: attribution and a one-tap "Show
original" are load-bearing, not decoration.

## Goals

- A post in the feed whose language the reader does not read is translated **in
  place**, automatically, once the list settles.
- The reader controls it: an **auto-translate** list and a **never-translate** list,
  both editable in Settings.
- First contact is a **chip on the post** ("Translate from Japanese"), not an
  overflow-menu item. Tapping it offers to make that language automatic.
- A tap on any link, mention or hashtag inside translated text goes to the
  **original** target, or does not link at all. Never somewhere else.
- Models download **on Wi-Fi only**, and the reader is told that is why they are
  waiting.
- The original is always one tap away, and the engine is always credited.
- Scrolling stays at 120hz.

## Non-goals (v1)

Threads, quoted posts, DM messages, profile bios, image alt text, OCR of images.
The seam is post-shaped so these are additive later — the thread measurement says
post detail is affordable, and it is the obvious second surface.

## Anti-goals

Things that make this a failure even if it technically works:

- A tap that goes somewhere other than where the original pointed.
- Hundreds of megabytes accumulating without the reader having agreed to each one,
  or without a way to see and delete them.
- Any translation traffic leaving the device. Nubecita talks to atproto and
  RevenueCat; a translation proxy would be new infrastructure, new key custody, new
  cost, and would mean routing what a user reads through our server.
- Measurable scroll regression.
- Translating during a fling.

## Constraints

- Kotlin 2.3 / Compose / MVI on `MviViewModel`, Hilt, Room, Navigation 3.
- `:data:models` may not take a Compose or service dependency beyond
  `compose-runtime`.
- No engine type may escape `:core:translation` (the `:core:billing` precedent).
- New strings need `es-419` and `pt-BR` in the same commit.
- Screenshot baselines are generated by CI's `update-baselines` label, never
  committed from macOS.
- ML Kit's terms require visible Google attribution wherever a translation is shown.

## Decisions

**D1 — Engine: ML Kit on-device, behind an SDK-agnostic seam.**
`:core:translation` exposes `TranslationEngine` (`translate(text, from, to)`),
`TranslationModelRepository` (what is downloaded, how big, delete), and
`LanguageIdentifier`. ML Kit types stay inside. A server proxy is rejected per the
anti-goals. *Rationale: mirrors `:core:billing`'s RevenueCat containment, which has
held.*

**D2 — Trigger: scroll-idle only.**
Dispatch on `listState.isScrollInProgress` going false, for visible items only,
onto `Dispatchers.Default`. Nothing is translated during a fling. Measured above as
free. *This decision is the 120hz guarantee; it is not negotiable without a new
measurement.*

**D3 — The reader's languages are plural, and come from the device plus one local list.**

*Corrected 2026-11-05 after a builder check.* The original note said to prefer "the
account's atproto content-language preference". **That preference does not exist.**
`app.bsky.actor` at SDK pin 9.13.0 ships exactly thirteen preference types —
`AdultContentPref`, `BskyAppStatePref`, `ContentLabelPref`, `DeclaredAgePref`,
`FeedViewPref`, `HiddenPostsPref`, `InterestsPref`, `LabelersPref`, `MutedWordsPref`,
`PersonalDetailsPref`, `PostInteractionSettingsPref`, `SavedFeedsPref`,
`ThreadViewPref` — and not one language-related class. The official client keeps
content languages client-local. The standing "prefer atproto state" rule cannot
apply to state the protocol does not model.

So: resolve as an ordered set of **device `LocaleListCompat`** union **the reader's
never-translate list**. That list does double duty — it *is* "languages I read", so
there is no fourth list in Settings. BCP-47 matched on the language subtag (`es`
matches `es-419`). The target language is the first `LocaleListCompat` entry.

**It is not overridable in v1** — an earlier draft of this decision said it was,
and nothing implements that. A reader whose primary device language is not the
one they want translations *into* is not served by v1; a target selector on the
Translation page is the obvious follow-up. Stated rather than quietly dropped,
because the two are easy to confuse: the never-translate list changes what is
left alone, not what the output language is.

The union is re-evaluated on every read and is **one-way**: a reader can add a
language to their read-set but cannot remove a device locale from it. The
alternative — seeding the list from the device once and letting it drift
independently — was considered and rejected, because it trades a rare annoyance
(a device locale the reader does not actually read) for a silent one (a language
added to the device later never joins the set, so its posts start being offered
with no setting that explains why).

Reuse the legacy-alias normalisation already in
`:core:post-interactions/TranslationUrl.kt` (`iw`→`he`, `in`→`id`, `ji`→`yi`).

**D4 — Detection uses `langs` as a hint, identification as a fallback, neither as a gate.**
Order: the record's `langs` if present and unambiguous → ML Kit language ID →
no affordance. The spike's mis-identified German post is the evidence: `langs`
alone misses unlabelled posts, identification alone misses hashtag-heavy ones.
Requires plumbing `langs` into `PostUi` and the feed cache, which does not exist today.

**D5 — Facets: count-matched ordinal restoration, never offset remapping.**
Given the mangling above:

1. Post has **no facets** → translate the whole text, render plain. (Covers the
   common case, including the post that prompted this.)
2. Post **has facets** → translate the whole text, then, per facet kind
   (link / mention / tag) independently:
   - find candidate spans in the translated output, tolerating the artifacts the
     spike documented (`@ ` and `# ` with an inserted space, `https: //`);
   - if the candidate count equals the original count **for that kind**, replace
     each candidate's text with the **original** span text and attach the
     **original** facet feature, in order. The output is **built by appending
     segments**, not patched in place, and each facet's byte slice is computed
     against the string being built — so restoring a span longer than the
     candidate it replaces cannot shift the spans after it. (Patching in place
     left-to-right *would* invalidate later offsets; reversing the order instead
     would reverse the emitted facet list. Both are wrong, and tests pin against
     both.)
   - if the counts disagree, that kind renders as **plain text** for this post.
3. Original byte offsets are never remapped onto translated text.

So the reader sees `#PalacesandGardens`, not `#palaceandGarden`, and tapping it
goes where the author pointed. A post we cannot reconcile loses its links rather
than acquiring wrong ones. Property-test against mentions adjacent to punctuation,
CJK, and posts where a kind appears zero times.

**D6 — Storage is a visible, consented, reversible cost.**
Download is Wi-Fi-only and per-language consented — the reader is told the size
before it starts and told *why* it is waiting when they are not on Wi-Fi. Settings
lists each downloaded language with its actual on-disk size and a delete action,
plus a total. Translators are closed when the feed leaves the foreground (the spike
reached ~400 MB RSS with five loaded).

**D7 — Cache translations in Room keyed by (post URI, target language).**
Target language must be in the key or a reader who switches languages gets a stale
hit. Bounded and evictable; translations are disposable.

**D8 — Attribution and reversal are permanent UI.**
"Translated from <language> · Google" with "Show original" alongside, wherever a
translation is displayed. Required by ML Kit's terms and, given the `ja→en` quality,
by honesty.

**D9 — Settings: a Translation sub-page.**
Master toggle; auto-translate languages; never-translate languages; downloaded
models with sizes and delete. Reuses the composer's existing BCP-47 language picker
(tags, localized display names, search). Per the project's standing rule, settings
sub-pages use `NubecitaListGroup`.

**D10 — Progressive opt-in, with a free discovery path.**

*Sharpened 2026-11-05 after a builder check*, which caught that "default off" and
"the chip is how people discover this" contradict each other if the master toggle
gates everything. Resolution — the toggle changes **how a foreign post is
detected**, not whether the chip can appear:

| Master toggle | Detection | Auto-translate | Cost to a reader who never uses it |
|---|---|---|---|
| **Off** (default) | the record's `langs` only — a field comparison | no | **zero**: no inference, no model resident |
| On | `langs`, then ML Kit language ID for unlabelled posts | yes, on scroll-idle | language-ID model resident |

With the toggle off, a post that *declares* a language the reader doesn't read
shows the chip. That costs nothing — no model, no inference, just a string compare
— so discovery exists on day one for every reader without anyone paying for it.
Turning the feature on is what buys the language-ID fallback that catches
unlabelled posts (the case that the motivating post falls into).

Tapping the chip translates that post **and** offers the language as automatic, in
one prompt that states the size (D6): *"Always translate Japanese? This downloads a
62 MB language pack over Wi-Fi. You can change this in Settings."* Accepting adds
the language to the auto list and turns the master toggle on. One prompt, not two.

Off Wi-Fi, accepting is still recorded: the chip shows **"Waiting for Wi-Fi"** and
the download runs when Wi-Fi arrives. It does not silently fail and it does not
bounce the reader to the Google Translate deep link — replacing that experience is
the point of the epic.

## Known gaps

- **No target-language selector.** The output language is the reader's primary
  device locale, full stop (D3).
- **A device locale cannot be un-read** (D3).

Both are follow-ups, not blockers.

## Left to the builder

- Room schema and eviction policy for D7 (size bound, LRU vs TTL).
- Exact candidate-span regexes for D5 and the tolerance set; the spike documents
  the artifacts, not the grammar.
- Chip visual tokens and placement within `PostCard`'s existing layout, and whether
  the shimmer reuses `:designsystem`'s `Shimmer` as-is.
- String wording (plus `es-419` / `pt-BR`).
- Whether the swap animation is a crossfade or a height-animating content swap;
  the reader asked for "swap with an animation" and nothing finer.

**D11 — Translation state lives in a feed-local wrapper, not on `PostUi`.**
`PostUi.langs` is the only `:data:models` change, because a post's declared
language is intrinsic post data. Translation state (translated text, in-flight,
show-original) is per-surface runtime state and lives in a feed-local wrapper.
`PostCard` gains optional translation params defaulting to null, so every other
surface — post detail, profile, notifications, bookmarks, search, widgets, Wear —
renders byte-identically and their screenshot baselines do not churn. Post detail
later supplies its own wrapper.

**D12 — "Show original" is session state.**
Held in the feed-local wrapper at screen scope, so scrolling away and back keeps
the reader's choice; not persisted across process death. Reverting to the original
is cheap and the translation is cached (D7).

**D13 — Instrument the silent-failure branch.**
Chip shown, chip tapped, auto-opt-in accepted/declined, model downloaded, model
deleted — and, load-bearing, a counter on D5's *counts disagree* branch. That
branch silently strips a post's links; without a counter there is no way to learn
how often it fires. Reuse existing `AnalyticsEvent` param conventions; **no new GA4
custom dimensions**, which would need an out-of-repo Admin-API registration.

## Open for the reader

**Is translation free, or Nubecita Pro?** Not decided here. The design assumes
**free**, on the grounds that it costs the project nothing at runtime (on-device,
no server, no per-call cost) and that comprehension is closer to accessibility than
to a premium extra. Gating is additive if the answer is Pro — an `isPro` gate in
`:core:translation`'s consumers plus a paywall entry from the chip — but it would
change the most visible surface in the feature, so it is called out rather than
assumed silently.

## Calls made without the reader

Noted so they can be checked rather than discovered:

- **Feed-only in v1**, despite the thread measurement being favourable — one
  surface, one set of baselines, one risk. Post detail is the obvious follow-up.
- **Default off.** The storage numbers make an opt-out default indefensible.
- **`en` is not special.** The target language is the reader's first resolved
  language, not English.
- **The existing `googleTranslateUrl` deep link stays** as the overflow fallback for
  language pairs ML Kit does not support, rather than being deleted.
- **App-size cost of the bundled ML Kit artifacts is ~2 MB**, measured as the delta
  between spike APKs (213.8 MB → 215.9 MB, bench debug — a release build will
  differ). Every reader pays it, including those who never translate. Judged
  acceptable; the ~45 MB per-language models are the cost that actually matters and
  those are opt-in.
