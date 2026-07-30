## ADDED Requirements

### Requirement: The feed renders at most one item per thread root within a session

The feed SHALL render at most one item per thread root, retaining the FIRST
occurrence in list order.

This exists because the timeline returns entries in post-time order, so several
replies into the same thread arrive as separate `FeedViewPost` entries. Each
would otherwise project to its own `FeedItemUi.ReplyCluster` and re-render that
thread's root as context, drawing the same post repeatedly.

Because the timeline is newest-first and
pagination walks backward in time, the retained item is always the newest reply
in that thread and every dropped sibling is strictly older.

The thread root of an item SHALL be derived as:

- `ReplyCluster` — the `root` post's id
- `SelfThreadChain` — the first chained post's id
- `Single` — the post's own id, so a standalone post reserves its own thread
- `Blocked` / `NotFound` — no thread root; these are never dropped

An item whose leaf carries a repost attribution SHALL NOT be dropped, but SHALL
still register its thread root. This mirrors the official client, where an
endorsement by someone the viewer follows is treated as its own signal.

The de-duplication SHALL be a pure function over `List<FeedItemUi>` applied to
the accumulated list, so that it spans pagination without a stateful tuner and
resets naturally on refresh.

#### Scenario: Two replies to the same thread arrive in one page

- **WHEN** a page contains two `ReplyCluster` items whose `root` is the same post
- **THEN** only the first SHALL be rendered and the second SHALL be dropped

#### Scenario: Replies to the same thread span two pages

- **WHEN** a reply into thread R is rendered from page 1 and a second reply into
  thread R arrives in page 2
- **THEN** the page-2 item SHALL be dropped, because it is older than the item
  already shown

#### Scenario: A standalone post reserves its own thread root

- **WHEN** a `Single` for post P is rendered and a later item is a
  `ReplyCluster` whose root is P
- **THEN** the later cluster SHALL be dropped

#### Scenario: A repost is never dropped by thread-root de-duplication

- **WHEN** an item whose leaf carries a repost attribution shares a thread root
  with an item already rendered
- **THEN** the reposted item SHALL still be rendered

#### Scenario: Refresh resurfaces the newest reply in a thread

- **WHEN** the viewer refreshes the feed and a newer reply into an
  already-seen thread has since been posted
- **THEN** that newer reply SHALL be rendered, because the accumulated list is
  rebuilt and no seen-root state persists across refreshes

#### Scenario: Tombstones are never de-duplicated

- **WHEN** the list contains `Blocked` or `NotFound` items
- **THEN** they SHALL be retained regardless of any thread root, because they
  carry no post from which a root can be derived

### Requirement: A de-duplicated feed item reports how many sibling replies were suppressed

A surviving feed item SHALL carry a suppressed-reply count, and the feed SHALL
surface that count as an affordance leading to the full thread.

Dropping sibling replies hides real content — one thread root was observed with
seven replies on a production account — so the count exists to keep that content
discoverable rather than silently lost.

This is a deliberate divergence from the official client, which drops silently.

The count SHALL be measured in **posts, not feed items**, because the affordance
is read by a viewer who has no notion of the app's internal grouping. A dropped
`SelfThreadChain` therefore contributes each of its posts, not one.

A suppressed post that is already rendered elsewhere in the list SHALL NOT be
counted. In particular, when a `Single` is dropped because its post is already
shown as the surviving item's `root` or `parent` context, the viewer can already
see it, so the count SHALL NOT increase.

The count SHALL be carried by every rendered variant that can survive
de-duplication, including `Single`. A standalone post can reserve a thread root
and suppress later replies into that thread; without a count on `Single` those
replies would be hidden with no affordance, which is the exact failure this
requirement exists to prevent.

#### Scenario: Suppressed siblings are counted in posts, not items

- **WHEN** two items into the same thread are dropped, one a `ReplyCluster` and
  one a `SelfThreadChain` of three posts
- **THEN** the surviving item SHALL report a suppressed-reply count of four

#### Scenario: A dropped standalone already visible as context is not counted

- **WHEN** a `Single` for post P is dropped because P is already rendered as the
  surviving item's `root` or `parent`
- **THEN** the suppressed-reply count SHALL NOT include P

#### Scenario: A surviving standalone post carries the count

- **WHEN** a `Single` for post P reserves thread root P and a later
  `ReplyCluster` rooted at P is dropped
- **THEN** the `Single` SHALL report a suppressed-reply count of one and SHALL
  render the affordance

#### Scenario: An item with no suppressed siblings shows no affordance

- **WHEN** an item's thread root appears exactly once in the list
- **THEN** its suppressed-reply count SHALL be zero and no affordance SHALL be
  rendered
