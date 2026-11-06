# Design — On-Device Post Translation

The full design, including the spike's measurements and the reasoning behind each
decision, lives at:

`docs/superpowers/specs/2026-11-05-on-device-post-translation-design.md`

That document is the source of truth for D1–D13. This file records only what a
reader of the change needs that the proposal does not already carry.

## The two corrections that changed the plan

**1. The feed is not off-limits.** The epic was written (2026-08-02) with the note
that feed translation "risks the 120hz scrolling requirement". That was caution
without data. Measured on a 120Hz Pixel 11 Pro, scroll-idle-gated translation costs
nothing detectable: 2.01% janky with it off, 1.77% and 1.65% with it on, p50 5 ms
throughout, with cold model downloads running concurrently in the "on" runs. The
gate — never during a fling — is what buys this, and it is a requirement, not an
implementation detail.

**2. There is no atproto content-language preference.** The epic said to prefer it
over the device locale, per the project's standing "prefer atproto state" rule.
`app.bsky.actor` at SDK pin 9.13.0 defines thirteen preference types and none is
language-related; the official client keeps content languages client-local. The
rule cannot apply to state the protocol does not model, so the reader's languages
resolve from `LocaleListCompat` unioned with a local never-translate list — which
doubles as "languages I read", so Settings gains one list, not two.

## Why facets are the hard part

Translation does not preserve them, and the damage is language-specific:

| Input | German output | Japanese output |
|---|---|---|
| `#PalacesandGardens` | `#palaceandGarden` | — |
| `@alice.bsky.social` | `@ alice.bsky.social` | `@ alice.bsky.social` |
| `https://example.com/a/b` | intact | `https: //example.com/a/b` |
| `#おはよう` | — | `# Good morning` |
| `⟦0⟧` placeholder | survives | `Ogaki 0` — destroyed |

The placeholder escape hatch was probed specifically and fails, so the design
cannot rest on it. What remains is count-matched ordinal restoration with a
per-kind fallback to plain text: a post either shows its author's links working, or
shows no links at all. It never shows a link pointing somewhere the author did not
write. An unreconcilable post is counted so we can find out how often this happens.

## Why default off, and why the chip is still free

~45 MB per language (62 MB for Japanese; five languages measured at 241.8 MB,
because ML Kit fetches both directions of each pair) makes an opt-out default
indefensible. But a feature nobody discovers is also a failure. The split: with the
toggle off, detection is a comparison against the record's declared `langs` — no
model, no inference, no residency — and the chip still appears. Turning it on buys
the language-identification fallback for unlabelled posts and auto-translation on
scroll-idle. A reader who never translates pays one string comparison per post.
