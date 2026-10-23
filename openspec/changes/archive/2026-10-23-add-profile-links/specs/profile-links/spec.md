## ADDED Requirements

### Requirement: Profile header maps `ProfileViewDetailed.links` into an ordered, moderation-filtered list

`ProfileHeaderUi` SHALL expose `links: ImmutableList<ProfileLinkUi>`, mapped from `ProfileViewDetailed.links` preserving server order. Each `ProfileLinkUi` SHALL carry `uri`, `url`, `title?`, `iconUrl?`, a resolved `label` (the title when non-blank, otherwise the URL host with a leading `www.` removed), and `isSupportLink`. A link whose labels resolve to `WARN` or `HIDE` through `ContentModerator` for the viewer SHALL be omitted.

#### Scenario: Order and fallback label

- **WHEN** the profile view returns links `[A(title="Shop"), B(url="https://www.example.com/x", no title)]`
- **THEN** `header.links` is `[A, B]` in that order, with labels `"Shop"` and `"example.com"`

#### Scenario: Moderated link is dropped

- **WHEN** a link carries a label the viewer's preferences resolve to `HIDE` or `WARN`
- **THEN** that link is absent from `header.links` and the remaining links keep their relative order

#### Scenario: No links

- **WHEN** the profile view has no `links` (absent or empty)
- **THEN** `header.links` is empty

### Requirement: Profile hero renders at most four link pills plus a "+N" overflow pill

The profile hero SHALL render `header.links` as `NubecitaLinkChip`s in a `FlowRow` directly below the bio. With four or fewer links all SHALL render; with more than four, exactly the first four SHALL render followed by a "+N" overflow chip where N is the remaining count. The row SHALL NOT measure content to decide how many pills fit. When `header.links` is empty the row SHALL NOT render. Pill labels longer than 30 characters SHALL be truncated with an ellipsis.

#### Scenario: Four links

- **WHEN** the profile has 4 links
- **THEN** 4 link pills render and no overflow pill renders

#### Scenario: Seven links

- **WHEN** the profile has 7 links
- **THEN** the first 4 link pills render followed by a "+3" overflow pill

#### Scenario: Long title truncated in the pill

- **WHEN** a link's label is 40 characters long
- **THEN** the pill shows the first 30 characters followed by an ellipsis

### Requirement: Overflow pill opens a bottom sheet listing every link

Tapping the "+N" pill SHALL open a modal bottom sheet listing all of the profile's visible links in order as full-width rows showing icon, full label, and full URL. Rows SHALL support the same tap and long-press behavior as pills.

#### Scenario: Sheet lists all links

- **WHEN** the user taps "+3" on a profile with 7 links
- **THEN** a bottom sheet opens listing all 7 links in profile order

### Requirement: Link pills open the destination and reveal the full URL on long-press

Tapping a link pill or sheet row SHALL open the URL in an in-app Custom Tab using the same path as bio links. Long-pressing SHALL reveal the full URL. Pills SHALL use `Role.Button` with an `onClickLabel` that includes the destination host, so a custom title cannot disguise the destination, and SHALL keep the leading icon decorative (`contentDescription = null`). A pill without `iconUrl`, or whose icon fails to load, SHALL show the globe glyph.

#### Scenario: Tap opens a Custom Tab

- **WHEN** the user taps a link pill
- **THEN** the link's URL opens in a Custom Tab

#### Scenario: Long-press reveals URL

- **WHEN** the user long-presses a pill titled "Support our work!" pointing at `https://patreon.com/sarah`
- **THEN** the full URL `https://patreon.com/sarah` is shown

#### Scenario: Screen reader announces label once, with host

- **WHEN** TalkBack focuses a pill titled "Support our work!" pointing at `https://patreon.com/sarah`
- **THEN** it announces the label once as a button whose click action names `patreon.com`, and the icon is not announced

### Requirement: Support-platform links render with the emphasized pill variant

A link SHALL be a support link when its host equals or is a subdomain of a domain in the support-provider table and, where the entry defines a path prefix, its path starts with that prefix. Support links SHALL render with the emphasized `NubecitaLinkChip` variant; all others with the standard variant.

#### Scenario: Recognized provider

- **WHEN** a link points at `https://www.patreon.com/sarah`
- **THEN** `isSupportLink` is true and the pill is emphasized

#### Scenario: Look-alike domain rejected

- **WHEN** a link points at `https://ko-fi.com.example.net/x`
- **THEN** `isSupportLink` is false

#### Scenario: Path-prefix provider

- **WHEN** a link points at `https://paypal.com/paypalme/sarah`
- **THEN** `isSupportLink` is true, and `https://paypal.com/signin` is not a support link

### Requirement: Edit Profile loads the owner's link records with a sealed load status

Edit Profile SHALL load the owner's links from their repo (the `profile/self` record's `links` refs, then each referenced `app.bsky.actor.link` record), skipping refs whose record is not found, and expose a sealed `LinksLoadStatus` (`Loading`, `Loaded`, `Failed`). While not `Loaded`, link editing SHALL be disabled and `Failed` SHALL offer Retry.

#### Scenario: Load succeeds

- **WHEN** the profile record references 3 link records that all exist
- **THEN** the editor's status is `Loaded` with those 3 links in reference order

#### Scenario: Dangling ref skipped

- **WHEN** one of 3 refs points at a deleted record
- **THEN** the editor loads the other 2 links in order

### Requirement: Saving before links load never modifies links

While `LinksLoadStatus` is not `Loaded`, Save SHALL remain available for the other profile fields and SHALL treat links as unchanged: no link record and no `links` key SHALL be modified.

#### Scenario: Save during failed load preserves links

- **WHEN** the links load failed and the user edits the display name and saves
- **THEN** the profile write preserves the existing `links` value verbatim and no link record is created, modified, or deleted

### Requirement: Owners can add, edit, and remove links in a draft

The Links section SHALL list the draft links and offer "Add link" while fewer than 10 links exist. Add, edit, remove, and reorder SHALL change only the draft, mark it dirty, and be covered by the existing discard-changes dialog; nothing SHALL be written before Save.

#### Scenario: Add normalized link

- **WHEN** the user adds `patreon.com/sarah` with no title
- **THEN** the draft gains a link with URL `https://patreon.com/sarah` and the editor is dirty

#### Scenario: Limit reached

- **WHEN** the draft has 10 links
- **THEN** "Add link" is not offered

#### Scenario: Discard

- **WHEN** the user adds a link and then backs out choosing discard
- **THEN** nothing is written to the repo

### Requirement: The link form validates and warns

The add/edit form SHALL normalize the URL to `https://`, reject unusable URLs and exact duplicates of another draft link, and limit the optional title to 40 graphemes with a live counter. It SHALL show a non-blocking warning for `http://` URLs and for known URL-shortener domains.

#### Scenario: Duplicate rejected

- **WHEN** the user enters a URL already present in the draft
- **THEN** an inline error shows and confirm is disabled

#### Scenario: Over-limit title

- **WHEN** the title field holds 41 graphemes
- **THEN** confirm is disabled

#### Scenario: Shortener warning does not block

- **WHEN** the user enters `https://bit.ly/abc`
- **THEN** a shortener warning shows and confirm remains enabled

### Requirement: Favicons are fetched at confirm and uploaded at Save

On confirming a new link or a URL change, the editor SHALL request `cardyb.bsky.app/v1/favicon?url=<url>&fresh=true` (8 s timeout) and keep the bytes only for a 2xx `image/png` response of at most 100 KB; otherwise the link SHALL have no icon, without blocking the add. Pending bytes SHALL be uploaded via `uploadBlob` during Save. Editing only the title SHALL keep the existing icon. The bench flavor SHALL make no network call.

#### Scenario: Usable favicon

- **WHEN** CardyB returns a 4 KB `image/png`
- **THEN** the draft link previews that icon and Save uploads it and writes the blob ref into the link record

#### Scenario: Unusable favicon

- **WHEN** CardyB times out or returns a non-PNG
- **THEN** the link is added without an icon

#### Scenario: Icon upload fails at Save

- **WHEN** `uploadBlob` fails for a pending icon during Save
- **THEN** that link is saved without an icon and the save succeeds

### Requirement: Save writes link records before the profile and deletes removed records after

When links changed, Save SHALL upload pending icons; reuse each unchanged link's strongRef, `putRecord` edited links at the same rkey, and `createRecord` new ones; write the profile via the existing compare-and-swap merge with `links` set to the ordered strongRefs (key removed when empty); then, only after that write succeeds, `deleteRecord` each originally loaded link no longer referenced. Delete failures SHALL NOT fail the save.

#### Scenario: Mixed changes

- **WHEN** the draft keeps link A unchanged, retitles B, adds C, and removes D
- **THEN** A's ref is reused, B is `putRecord`ed at its rkey, C is created, the profile is written with `[A, B', C]`, and D is deleted afterwards

#### Scenario: Profile write conflict

- **WHEN** the profile `putRecord` fails with `InvalidSwap`
- **THEN** no link record is deleted and the save surfaces the existing conflict error

#### Scenario: All links removed

- **WHEN** the user removes every link and saves
- **THEN** the profile record no longer has a `links` key and the former link records are deleted

### Requirement: Owners can reorder links

The editor SHALL let the owner drag links into a new order and SHALL offer move-up / move-down accessibility actions. Reordering SHALL change only draft order; saving a reorder alone SHALL write the profile with the new ref order and SHALL NOT write any link record.

#### Scenario: Reorder only

- **WHEN** the user drags the third link to the top and saves
- **THEN** the profile `links` refs are written in the new order and no link record is created, modified, or deleted

#### Scenario: Accessible reorder

- **WHEN** a TalkBack user invokes "Move up" on the second link
- **THEN** it becomes the first link in the draft
