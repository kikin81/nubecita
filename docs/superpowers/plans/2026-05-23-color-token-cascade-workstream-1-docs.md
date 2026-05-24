# Color Token Cascade — Workstream 1 (Docs) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the canonical reference for the Material 3 surface-token cascade contract so subsequent workstreams have a single source of truth to cite. Produces (a) KDoc on `Color.kt` above the brand color-scheme definitions and (b) a `docs/design-system/surface-roles.md` reference page served via GitHub Pages.

**Architecture:** Pure documentation. No runtime change. The KDoc is where engineers will land when reading `Color.kt`; the reference page is where the contract is normatively stated. The reference page lives under `docs/design-system/` and ships via the existing GitHub Pages setup (see `docs/README.md`).

**Tech Stack:** Markdown (GitHub-flavored), KDoc.

**Spec:** `docs/superpowers/specs/2026-05-23-color-token-cascade-design.md`

**bd:** Create a new bd issue `nubecita-XXXX` of type `decision`, title "Add KDoc and reference page for surface-token role contract", with the body pointing at the spec. Branch off `main` as `docs/nubecita-XXXX-<slug>` via the `bd-workflow` skill's start flow. Do NOT reuse `nubecita-kkla` — that one closes when the spec PR merges.

---

## File Structure

| File | Action | Responsibility |
|---|---|---|
| `docs/design-system/surface-roles.md` | Create | Normative reference for the role contract. Full role table, structural nesting rule, reserved-token policy, tonal-elevation invariant, links to the spec. |
| `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/Color.kt` | Modify (add KDoc, no code change) | Short KDoc directly above `nubecitaLightColorScheme()` (the first of the two scheme functions) that names each surface role and links to the reference page. KDoc applies to both light and dark schemes; one anchor is enough because they're co-located. |
| `docs/superpowers/specs/2026-05-23-color-token-cascade-design.md` | Read-only reference | Source of truth this PR materializes. Do not edit. |

The `docs/design-system/` directory does not exist yet — `docs/` today only holds `oauth/`, the OAuth client metadata, and the Pages landing page. Creating the subdirectory is part of this PR.

---

## Preconditions

1. The design spec exists on `main`. Verify with `git fetch origin main && git show origin/main:docs/superpowers/specs/2026-05-23-color-token-cascade-design.md > /dev/null` — a non-zero exit means the spec hasn't landed yet, so the spec PR (originally opened as #288 on branch `docs/nubecita-kkla-...`, but the durable check is the file path, not the PR number) must merge first. This plan references the spec by path and the path must resolve on `main`.
2. Working tree is clean (`git status` shows no tracked changes). Untracked is fine.
3. Current branch is `main` and up to date (`git pull --ff-only`).

---

## Task 1: Create the bd issue and branch

**Files:** none yet — this is the bd-workflow start flow.

- [ ] **Step 1: Create the bd issue**

Run:
```bash
cat > /tmp/bd-surface-roles-desc.md <<'EOF'
Materializes workstream 1 of the color-token-cascade spec (nubecita-kkla):
add KDoc on Color.kt + a docs/design-system/surface-roles.md reference page
that documents the depth-role contract for every M3 surface token.

Spec: docs/superpowers/specs/2026-05-23-color-token-cascade-design.md
Plan: docs/superpowers/plans/2026-05-23-color-token-cascade-workstream-1-docs.md

No runtime change. Reference page ships via the existing GitHub Pages
setup (docs/README.md).
EOF
bd create "Add KDoc and reference page for surface-token role contract" \
  --type decision --priority 2 \
  --body-file /tmp/bd-surface-roles-desc.md \
  --json | jq -r '.id'
```
Expected: prints a new bd id like `nubecita-XXXX`. Record it as `BD_ID` for the rest of the plan.

- [ ] **Step 2: Add dependency edge so this issue can't be picked up before the spec lands**

Run (substitute `<BD_ID>`):
```bash
bd dep add <BD_ID> blocker nubecita-kkla
```
Expected: `Added dependency: <BD_ID> blocked-by nubecita-kkla`. (Per the memory `feedback_bd_create_deps_reverse_direction.md` — don't use `--deps` on create; add the edge after.)

- [ ] **Step 3: Use the bd-workflow start flow to create the branch + claim**

Invoke the `bd-workflow` skill with: "start `<BD_ID>`". The skill will:
- Verify tree-clean and on main
- Derive branch `docs/<BD_ID>-add-kdoc-and-reference-page-for-surface-token-rol` (50-char slug cap; trailing chars truncated)
- `git checkout -b <branch>` then `bd update <BD_ID> --claim`

Expected: switched to new branch, bd issue claimed.

---

## Task 2: Write the reference page

**Files:**
- Create: `docs/design-system/surface-roles.md`

- [ ] **Step 1: Create the directory and write the file in one shot**

Use the `Write` tool to create `docs/design-system/surface-roles.md` with the following exact content:

````markdown
# Surface token role contract

Nubecita maps each Material 3 `surface*` token to exactly one **depth role**. Code should reason about depth by role and pick the token from this table — never the other way around.

Reading order top→bottom is back→front in the visual stack.

| Depth role | M3 token | What lives there |
|---|---|---|
| **Screen canvas** | `surface` | Scaffold root, modal root, full-screen routes. Every screen paints exactly one of these. |
| **Item card** | `surfaceContainer` | Discrete content objects sitting on the canvas: post cards, settings section cards, chat convo rows, post-position placeholders (`BlockedPostCard`, `NotFoundPostCard`). |
| **Recessed inset** | `surfaceContainerLow` | Anything nested *inside* an item card: quoted posts, external link embeds, "record unavailable" / "unsupported embed" placeholders inside a post body. In dark mode this token is darker than `surfaceContainer`, producing the carved-in feel. |
| **Raised affordance** | `surfaceContainerHigh` | Things sitting on top of an item card: chat message bubbles, day-separator chips, video-poster top gradient stop. |
| **Strong fill** | `surfaceContainerHighest` | Thumbnail placeholders, shimmer base, image-load placeholders, character-counter track, disabled fills. Independent of nesting depth. |
| **Reserved** | `surfaceDim`, `surfaceBright`, `surfaceContainerLowest` | Documented unused in v1. The detekt rule rejects external usage. |

## Structural rule

> **Anything nested directly inside an item card uses the recessed-inset role**, with one exception: thumbnail / shimmer / placeholder fills always use the strong-fill role regardless of nesting depth.

This is the rule the lint reviewer applies. Code review enforces the nesting check; detekt enforces the reserved-token check and the `Scaffold` `containerColor` check.

## `background` is a synonym for `surface`

The brand color scheme defines `background` and `surface` to identical values in both light and dark modes. The contract picks `surface` for all canvas paints. Detekt forbids `colorScheme.background` outside design-system internals so the canonical name is the only one in code.

## Tonal elevation: windowed surfaces only

Material 3's tonal elevation lift (`Surface(tonalElevation = …)`) is allowed **only** on `surface` tokens representing windowed bounds — `Dialog`, `BottomSheet`, modal `Surface`. It is the depth cue for "this is a floating window."

For in-layout content hierarchy (cards, embeds, rows), tonal elevation is **not** used. Pick the explicit `surfaceContainer*` token for the depth role and skip the elevation knob. This removes the ambiguity of "use a container token or use tonal elevation?" and keeps every in-layout depth step traceable in code.

## Scaffold canvas contract

Every `Scaffold(` call sets `containerColor = MaterialTheme.colorScheme.surface` explicitly. This codifies that Scaffolds own the screen-canvas paint and prevents the silent `background` → `surface` slippage from M3's default. The detekt rule fails the build if a `Scaffold(` omits `containerColor`.

## Where this lives

- **Source of truth (this page)**: `docs/design-system/surface-roles.md`
- **Design spec (decisions and rationale)**: `docs/superpowers/specs/2026-05-23-color-token-cascade-design.md`
- **In code**: KDoc on `nubecitaLightColorScheme()` / `nubecitaDarkColorScheme()` in `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/Color.kt` points back here.
- **Preview / screenshot wrapper**: `NubecitaScreenPreviewTheme` in `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/preview/` paints the screen-canvas role for previews and screenshot fixtures (workstream 2).
````

- [ ] **Step 2: Verify the file exists and is well-formed**

Run:
```bash
ls -la docs/design-system/surface-roles.md
wc -l docs/design-system/surface-roles.md
```
Expected: file exists, ≈40 lines. The actual count depends on wrapping; the assertion is just "non-empty and read it back made sense."

- [ ] **Step 3: Commit just this file**

Run:
```bash
git add docs/design-system/surface-roles.md
git commit -m "$(cat <<'COMMIT'
docs(designsystem): add surface token role contract reference page

Materializes workstream 1 of the color-token-cascade spec. Documents
each surface* token's depth role (screen / item card / recessed inset
/ raised affordance / strong fill), the "anything inside a post body
is a recessed inset" structural rule, the windowed-surface invariant
for tonal elevation, and the `Scaffold` containerColor contract.

Reference page is the normative source of truth that subsequent
workstreams (preview wrapper, call-site migration, detekt rule) cite.

Refs: <BD_ID>
COMMIT
)"
```
Expected: pre-commit hooks pass (no Kotlin / openspec changes — only file-format + commitlint checks fire). Commit lands.

---

## Task 3: Add KDoc to Color.kt

**Files:**
- Modify: `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/Color.kt:77` (above `nubecitaLightColorScheme()`)

- [ ] **Step 1: Verify the target line is still where we expect**

Run:
```bash
grep -n "^internal fun nubecitaLightColorScheme" designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/Color.kt
```
Expected: prints `77:internal fun nubecitaLightColorScheme() =`. If the line has shifted, adjust the Edit anchor accordingly — the KDoc still goes directly above the function.

- [ ] **Step 2: Add the KDoc**

Use the `Edit` tool on `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/Color.kt`.

`old_string`:
```kotlin
internal fun nubecitaLightColorScheme() =
    lightColorScheme(
```

`new_string`:
```kotlin
/**
 * The M3 `surface*` tokens in these color schemes are assigned to depth
 * roles — pick the role first, then the token follows from the table.
 * Full contract: `docs/design-system/surface-roles.md`.
 *
 * Role → token at a glance:
 *
 * - **Screen canvas** → `surface` (Scaffold/modal root)
 * - **Item card** → `surfaceContainer` (post card, settings section, convo row)
 * - **Recessed inset** → `surfaceContainerLow` (anything nested inside an item card)
 * - **Raised affordance** → `surfaceContainerHigh` (message bubble, day chip)
 * - **Strong fill** → `surfaceContainerHighest` (thumbnails, shimmer, placeholders)
 *
 * `surfaceDim`, `surfaceBright`, and `surfaceContainerLowest` are reserved and
 * rejected by the detekt rule outside design-system internals.
 *
 * `background` is set equal to `surface` and is treated as a synonym; new code
 * should reference `surface` (the detekt rule enforces this).
 */
internal fun nubecitaLightColorScheme() =
    lightColorScheme(
```

Expected: Edit applies cleanly (the `internal fun nubecitaLightColorScheme()` declaration is unique in the file).

- [ ] **Step 3: Run ktlint locally to verify the KDoc doesn't trip a rule**

Run:
```bash
./gradlew :designsystem:spotlessKotlinCheck
```
Expected: `BUILD SUCCESSFUL`. If ktlint complains about KDoc formatting (e.g. line length, blank line after summary), fix the formatting in the KDoc and re-run.

- [ ] **Step 4: Commit**

Run:
```bash
git add designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/Color.kt
git commit -m "$(cat <<'COMMIT'
docs(designsystem): KDoc surface token roles on color scheme functions

Adds the role → token cheat sheet directly above the brand color
scheme definitions so engineers reading Color.kt land on the contract
without leaving the file. Full reference: docs/design-system/surface-roles.md.

Refs: <BD_ID>
COMMIT
)"
```
Expected: pre-commit hooks pass (spotless on the changed file, commitlint). Commit lands.

---

## Task 4: Final verification

**Files:** none.

- [ ] **Step 1: Verify both commits are on the branch**

Run:
```bash
git log --oneline main..HEAD
```
Expected: exactly two commits, in order:
1. `docs(designsystem): add surface token role contract reference page`
2. `docs(designsystem): KDoc surface token roles on color scheme functions`

If there are extras or missing commits, stop and investigate.

- [ ] **Step 2: Re-read the diff one more time**

Run:
```bash
git diff main...HEAD
```
Expected: only the two files (`docs/design-system/surface-roles.md` added; `designsystem/.../Color.kt` KDoc added). No accidental edits elsewhere.

- [ ] **Step 3: Verify the spec link in the reference page resolves**

Run:
```bash
test -f docs/superpowers/specs/2026-05-23-color-token-cascade-design.md && echo OK
```
Expected: `OK`. (This proves the spec PR merged before this plan ran; if it prints nothing, the precondition was skipped — stop and merge the spec first.)

- [ ] **Step 4: Run the full local quality gate**

Run:
```bash
pre-commit run --all-files
```
Expected: all hooks pass (or `Skipped` for hooks with no matching files). If a hook fails, fix the underlying issue — **never use `git commit --no-verify`** per the project's `feedback_no_verify_breaks_lint` memory.

---

## Task 5: Push and open PR

**Files:** none.

- [ ] **Step 1: Use the bd-workflow finish flow**

Invoke the `bd-workflow` skill with: "open the PR". The skill will:
- Verify the branch is not `main`, tree is clean, ≥1 commit ahead
- Skip the gradle preflight (no Kotlin code touched beyond a KDoc; spotless already ran in task 3)
- `git push -u origin <branch>`
- `gh pr create --base main --title "<first-commit-subject>" --body "Closes: <BD_ID>"`
- Request Copilot reviewer via the REST endpoint
- **Do NOT spin up the CI cron monitor** per `feedback_ci_monitor_only_when_afk` — the user is interactive

Expected: PR URL printed.

- [ ] **Step 2: Report PR + remind about post-merge close**

After the PR opens, report the URL to the user and note: "`bd <BD_ID>` stays open until merge; close with `bd close <BD_ID>` after."

---

## Out of scope for this plan

Each item below is a separate workstream with its own plan; this plan covers docs only.

- **Workstream 2** — `NubecitaScreenPreviewTheme` wrapper + screenshot fixture migration. Plan to be written after this lands.
- **Workstream 3 (a–g)** — call-site migration across feed, post-detail, composer, chats, search, profile, login/onboarding. Seven separate PRs; plans to be written per surface as they come up.
- **Workstream 4** — custom detekt rule in `build-logic/detekt-rules/`. Plan to be written last, after all call sites conform.

## Risks specific to this plan

- **Slug truncation creates an awkward branch name** (`docs/<bd-id>-add-kdoc-and-reference-page-for-surface-token-rol`). The bd-workflow skill's slug rule caps at 50 chars; the branch name is cosmetic and the bd id makes it unambiguous. No action required.
- **The KDoc summary may run afoul of ktlint's `kdoc-wrapping` rule** if a line exceeds the configured max. If task 3 step 3 fails, shorten lines in the KDoc and re-run; the content can be tightened without losing meaning. Do not bypass spotless.
- **The reference page describes contracts not yet enforced in code.** Workstream 1 ships the doc; workstreams 2–4 land the code that conforms. Until workstream 4 lands the detekt rule, reviewers manually enforce. This is the trade-off for the doc-first sequence chosen in the spec.
