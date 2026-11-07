## Context

Full design: `docs/superpowers/specs/2026-11-06-followers-following-lists-design.md`. Plan: `docs/superpowers/plans/2026-11-06-followers-following-lists.md`.

## Goals / Non-Goals

**Goals:** see who follows an account and who it follows, and follow people from there.

**Non-Goals:** search, a Mutual tab, sorting, offline caching, deep links.

## Decisions

- **No search.** Bluesky has no API to search within one account's followers, and client-side search would mean walking pages in the background.
- **Segmented buttons.** Reuse `ProfilePillTabs`, matching the profile and Chats screens.
- **Follow state is a per-DID overlay in the ViewModel**, read at render time, so optimistic state survives page reloads and covers both tabs without rebuilding `PagingData`.
- **Counts travel in the route**, because `getFollowers` and `getFollows` return `ProfileView`, which has no counts. They are a snapshot.
- **Unfollow is immediate**, matching the profile's Follow button.

## Risks / Trade-offs

- [Counts can be stale after follows made here] → acceptable for v1; the next profile refresh corrects them.
- [Labels with large counts may not fit in es-419 or pt-BR] → screenshot coverage on a long label.
