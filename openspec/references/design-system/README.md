# Nubecita Design System

**Nubecita** ("little cloud" in Spanish) is a native Android Bluesky client built on **Material 3 Expressive** with adaptive layouts for phone, tablet, and foldables. It's a fresh brand on top of Google's M3 Expressive mechanics — own identity, own color, but strict on M3 motion and component anatomy.

---

## Source material

No codebase or Figma was attached. This system was built by grounding in public specs:
- **Material 3 Expressive** — Google's May 2025 evolution of Material You (saturated tonal palettes, shape-morphing, spring motion, emphasized type).
- **Bluesky / AT Protocol** — for app mental model only (feed, threads, handles, reposts, likes). None of Bluesky's brand is reused.

The user's direction:
- **Brand voice:** bold and expressive — saturated, big type, energetic motion.
- **Primary direction:** sky blue with white cloud accents.
- **Most important screens:** the **feed** (primary reading surface — accessibility + readability a top priority) and the **post detail** (threaded replies).
- **M3 fidelity:** mostly strict; custom shape, color, type.
- **Scope:** phone, tablet (dual-pane), foldable (three-pane), plus onboarding, settings, media viewer, notifications, search.
- **Logo:** literal little-cloud mark + wordmark.

---

## Quick index

| File / folder | What it is |
|---|---|
| [`colors_and_type.css`](./colors_and_type.css) | Every token: tonal palettes, semantic roles, shape, elevation, motion, type scale |
| [`assets/`](./assets) | Logos, logomark, cloud illustrations, icon sprite, brand art |
| [`fonts/`](./fonts) | Local font files (where applicable; most load via Google Fonts) |
| [`preview/`](./preview) | Individual design-system cards — one per token group / concept |
| [`ui_kits/nubecita-android/`](./ui_kits/nubecita-android) | Full Android UI kit: phone + tablet + foldable screens, JSX components |
| [`slides/`](./slides) | Pitch/marketing slide templates styled like Nubecita |
| [`SKILL.md`](./SKILL.md) | Agent-invocable skill manifest (cross-compatible with Claude Code) |

---

## Content fundamentals

Nubecita's voice is **warm, direct, and a little joyful**. It's an app for reading what people wrote; the UI should feel like it's helping you, not narrating at you.

### Rules of thumb

- **Second person, contracted.** "You're all caught up," not "The user has read all posts."
- **Sentence case everywhere.** Buttons, headers, section titles — "Post a skeet," not "Post A Skeet." The only exception is proper nouns and the product name.
- **Short.** Labels are 1–3 words. Empty states are ≤ 12 words. Error messages name the problem and offer one action.
- **Plain verbs over jargon.** "Reply," not "Compose response." "Mute," not "Suppress updates."
- **No exclamation marks** except in onboarding celebration and genuinely happy empty states ("You're all caught up ☁️"). Never in errors.
- **No emoji in product chrome.** Emoji belong in user content. The one brand exception is the little-cloud glyph ☁︎ in hero moments (splash, empty states).
- **Numbers are terse.** "1.2k replies," "3h ago," "Mar 4."

### Microcopy vocabulary

| Action | Word we use | Don't say |
|---|---|---|
| Create a post | **Post** (verb) / **a post** (noun) | "Tweet," "skeet," "compose" |
| Share with your network | **Repost** | "Retweet," "Boost" |
| Approve a post | **Like** | "Favorite," "Heart" |
| Respond inline | **Reply** | "Comment" |
| Out of the app | **Share** | "Send," "Export" |
| Filter your feed | **Feeds** / **Pinned feeds** | "Lists," "Timelines" |
| Your handle | **@handle** (always with the at-sign) | "username" |

### Tone examples

- Empty feed: **"Quiet up here. Try following a few feeds to fill the sky."**
- Send failed: **"Couldn't post. Check your connection and try again."**
- Profile with no posts: **"Nothing yet from @alice."**
- Confirmation on block: **"Blocked @trollbot. They won't see your posts."**
- Celebration (first post): **"☁︎ Your first post is live."**

---

## Visual foundations

### Colors

Nubecita uses **M3's tonal palette system** — every color role (primary, secondary, tertiary, error, neutral) has 14 tones (0, 10, 20, … 99, 100). Semantic roles (`--primary`, `--surface`, `--on-surface`, etc.) map into that palette and **flip automatically in dark mode**.

- **Primary — Sky `#0A7AFF`** (tone 50). The brand blue. Pure, saturated, high-chroma. Used for FABs, primary actions, active tabs, links.
- **Secondary — Peach `#C06C00`** (tone 50). The warm counterweight — accents on engagement (reposts, saves), highlight chips, onboarding moments.
- **Tertiary — Lilac `#6250B0`** (tone 40). Soft third accent for categories, badges, subtle decoration. Never a primary action.
- **Neutral surfaces.** Light mode runs a warm-cool-neutral from `#FFFFFF` → `#E5E3E9` across 5 tonal surface containers. Dark mode is a near-black `#111318` base lifting to `#32353A`.

**No gradients** in UI chrome. Gradients appear only in brand art (logo wash, onboarding hero). Solid tonal surfaces do the work.

### Type

- **Display:** **Fraunces** (variable serif, `SOFT 50`) — used only for display-large and display-medium. Adds the "expressive" warmth to big moments (onboarding, section heroes, empty states).
- **Body + headlines:** **Roboto Flex** (variable sans) — optical sizing tuned for every role.
- **Mono:** **JetBrains Mono** — for handles with `did:` prefixes, debug, dev.

Body-large is **17 px / 26 px line-height** (one step up from M3's 16 / 24) — deliberate, because the feed is the primary surface and users read it for a long time. Type ramp follows M3 exactly otherwise.

### Shape

M3 Expressive's shape scale, rounded a touch harder:

| Token | Radius | Usage |
|---|---|---|
| `--shape-xs` | 4 px | Tooltips, tags, dividers with rounding |
| `--shape-sm` | 8 px | Chips, dense inputs |
| `--shape-md` | 12 px | Text fields, menus |
| `--shape-lg` | 16 px | Cards, sheets, dialogs |
| `--shape-xl` | 28 px | Large cards, bottom-sheet corners |
| `--shape-2xl` | 36 px | Hero surfaces, focus tiles |
| `--shape-full` | 999 px | All buttons (M3 Expressive pills), avatars, FABs when floating |

Buttons are **fully pill-shaped** — this is the single strongest visual tell of M3 Expressive and we commit to it.

### Elevation

Mild tonal elevation, minimal shadow. We use **5 surface-container tones** (M3's `surface-container-lowest` through `surface-container-highest`) for card hierarchy, and reserve **shadow** (`--elev-1` through `--elev-5`) for truly floating surfaces (FAB, menus, bottom sheets over content).

### Motion

**M3 Expressive springs.** Motion is central to the personality.

- **Default transition:** `var(--ease-spring-fast)` — a quick overshoot that settles. ~300 ms.
- **Emphasized (sheets, page transitions):** `var(--ease-spring-slow)` — larger overshoot, ~500 ms.
- **Bouncy (celebration, FAB tap):** `var(--ease-spring-bouncy)` — a satisfying wobble.
- **Shape morphing:** buttons squish on press (scale 0.96, shape-md → shape-sm). The FAB morphs into a bottom sheet when opened.
- **Reduced motion:** the `prefers-reduced-motion` branch uses `ease-standard` with no overshoot and halved durations. All spring effects degrade to linear-ish fades.

### Backgrounds & imagery

- **Surface is the hero, not imagery.** The app is mostly clean tonal surfaces with content.
- **Brand illustration appears in three moments only:** (1) onboarding hero, (2) empty states, (3) splash. Always a flat cloud motif — no gradients, no 3D, no grain.
- **Post media** uses rounded corners (`--shape-lg`) and an outline-variant border at 1 px inside.
- **No full-bleed images in chrome.** No blur-behind header patterns. Clarity first.

### Hover, press, focus, disabled

- **Hover** (desktop/web): 8% primary overlay (`--state-hover`). Cursor pointer. Never shift layout.
- **Press:** 12% primary overlay + scale `0.96` + 100 ms duration. Pill buttons also briefly morph toward `shape-sm` then back.
- **Focus:** 2 px primary ring, offset 2 px. Always visible, never removed.
- **Disabled:** 38% opacity on-surface, 12% opacity on container. No pointer events.

### Borders & outlines

- Default outline: `1 px solid var(--outline-variant)`.
- Strong outline (input focus, selected chip): `2 px solid var(--primary)`.
- No borders on cards by default — they separate via elevated surface tone.

### Transparency & blur

Used **sparingly**.
- **Scrim** over sheets: `--scrim` (50% `#000D1F` light, 60% black dark). No blur.
- **Top app bar on scroll:** backdrop-blur 20 px + surface-container at 80% opacity. Only while content is under it.
- **Media viewer** uses full-opacity black chrome — no blur.

### Layout rules

- **Top app bar:** 64 px (compact), 72 px (medium), 80 px (expanded).
- **Navigation:** bottom bar on compact (phone), nav rail on medium (tablet), extended drawer on expanded (foldable, large tablet).
- **Content max width:** 640 px for feed, 840 px for post detail with reply tree.
- **Gutters:** 16 px (compact), 24 px (medium), 32 px (expanded).
- **Safe areas:** always respected; bottom bar includes bottom inset.
- **Adaptive breakpoints:** <600 dp compact, 600–839 dp medium, ≥840 dp expanded (M3 canonical).

---

## Iconography

**Material Symbols** (Google's official M3 icon pair) — rounded, filled/outlined variants, weight 400, grade 0, optical size 24. Loaded via Google Fonts as a variable icon font for tree-shaken use at runtime.

Why:
- It's the **M3 canonical pair**, tuned to match Roboto Flex visually.
- The "rounded" style matches Nubecita's softer shape scale.
- Variable axes (`FILL`, `wght`, `GRAD`, `opsz`) let us switch filled ↔ outlined on state (filled = active, outlined = inactive) without swapping assets.

Rules:
- **Outlined** = inactive / default.
- **Filled** (`FILL 1`) = active / selected — e.g. selected tab, liked post.
- **24 px** is the default icon size in nav & actions; **20 px** in-line with body text; **18 px** inside chips.
- Stroke/weight: `wght 400` default, `wght 500` when paired with bold text.
- **Never** recolor an icon outside the color-role system.

**Emoji:** only in user-generated content. Not in chrome. The one exception is the ☁︎ cloud glyph in brand moments (rendered as text, not an image).

**Unicode glyphs as icons:** avoided. Use Material Symbols or nothing.

---

## Index of design-system cards (rendered in the Design System tab)

See [`preview/`](./preview). Cards are grouped into:

- **Brand** — logo, logomark, cloud illustration
- **Colors** — primary / secondary / tertiary scales, neutrals, semantic roles, dark mode
- **Type** — display, headlines, body, labels, mono
- **Spacing** — scale, shape scale, elevation
- **Components** — buttons, chips, FAB, cards, inputs, nav bar, top app bar, post card, reply composer

---

## UI Kit index

- [`ui_kits/nubecita-android/`](./ui_kits/nubecita-android) — the Android client. Phone + tablet dual-pane + foldable three-pane. Includes feed, post detail, composer, profile, notifications, search, settings, onboarding, media viewer.

---

## Slides

- [`slides/`](./slides) — pitch & marketing templates: title slide, big-quote slide, comparison, stat, section header, product shot.

---

## Caveats

- **No source codebase or Figma was attached.** This is a from-scratch brand on top of M3 Expressive specs. If you have brand anchors — existing logos, color preferences, specific screens you've mocked — share them and I'll reconcile.
- **Fraunces, Roboto Flex, JetBrains Mono** are all loaded from Google Fonts. If you want them bundled locally, download and drop into `fonts/`.
- **Material Symbols** loads via Google Fonts. If you need it offline, download the variable font file and reference locally.
