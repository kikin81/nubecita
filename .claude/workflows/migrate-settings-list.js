export const meta = {
  name: 'migrate-settings-list',
  description: 'Migrate Settings sub-pages to NubecitaListGroup, one PR per screen',
  whenToUse:
    'Epic nubecita-1ow5. Run AFTER nubecita-1ow5.1 (single-select mode on NubecitaListItem) has merged — the Feed preferences migration needs it. Pass the bd child ids to migrate via args.',
  phases: [
    { title: 'Migrate', detail: 'one agent per screen, isolated worktree, ends in a PR' },
    { title: 'Verify', detail: 'adversarial review of each pushed PR diff' },
  ],
}

// ---------------------------------------------------------------------------
// Targets. Override by passing args, e.g.
//   args: [{ bdId: "nubecita-1ow5.4", screen: "AboutScreen", ... }]
// ---------------------------------------------------------------------------
const SETTINGS_DIR =
  'feature/settings/impl/src/main/kotlin/net/kikin/nubecita/feature/settings/impl'

const DEFAULT_TARGETS = [
  {
    bdId: 'nubecita-1ow5.2',
    screen: 'FeedPreferencesScreen',
    slug: 'migrate-feedpreferences-to-nubecitalistgroup',
    handRolled: ['SwitchRow', 'ReplyVisibilityRow'],
    notes: [
      'Needs the single-select mode from nubecita-1ow5.1 for the three reply options.',
      'Suggested grouping: the three reply options as one labelled group, then Hide reposts + Hide quote posts as a second group.',
      'CRITICAL: FeedPreferencesViewModel guards `if (event.visibility != uiState.value.replyVisibility)` so re-selecting the active option does not fire a getPreferences+putPreferences round-trip. That guard is VM-side. Do NOT remove it, and do NOT reintroduce a UI-side guard. Two unit tests pin it; they must still pass.',
    ],
  },
  {
    bdId: 'nubecita-1ow5.3',
    screen: 'ContentFiltersScreen',
    slug: 'migrate-contentfilters-to-nubecitalistgroup',
    handRolled: ['AdultContentToggleRow'],
    notes: [
      'Its LabelVisibilityGroup connected button group is CORRECT as-is: the labels are Show / Warn / Hide, short and comparable, which is what button groups are for. Do not convert it reflexively. If you do convert it for consistency, say so explicitly in the PR body and justify it.',
    ],
  },
  {
    bdId: 'nubecita-1ow5.4',
    screen: 'AboutScreen',
    slug: 'migrate-about-to-nubecitalistgroup',
    handRolled: ['NavRow'],
    notes: [
      'Pure click rows — NubecitaListItem\'s existing onClick mode covers these, no design-system work needed.',
      'Group related entries rather than emitting one flat run.',
    ],
  },
]

// Target selection is FAIL-LOUD on purpose.
//
// The first live run of this workflow migrated all three default screens when
// only one was asked for, because `args` did not arrive as an array and the
// old guard quietly fell back to DEFAULT_TARGETS. "Caller asked for something
// specific but I could not read it" and "caller asked for nothing" are
// completely different situations, and defaulting the first one to *migrate
// everything* is the most destructive choice available. So: absent args means
// the defaults, malformed args is an error.
//
// The `typeof` check stays because an UNdeclared reference would throw a bare
// ReferenceError on line one, which is a much worse diagnostic than this.
function resolveTargets() {
  const raw = typeof args === 'undefined' ? undefined : args
  if (raw === undefined || raw === null) return DEFAULT_TARGETS

  // The Workflow tool wants real JSON, but a stringified array is the easy
  // mistake to make; recover from it rather than migrating the wrong screens.
  let value = raw
  if (typeof value === 'string') {
    try {
      value = JSON.parse(value)
    } catch (e) {
      throw new Error(
        `args was a string that is not JSON (${JSON.stringify(raw.slice(0, 80))}). ` +
          'Pass a real JSON array of target objects, not a stringified one.',
      )
    }
  }

  if (!Array.isArray(value)) {
    throw new Error(
      `args must be an array of target objects, got ${typeof value}. ` +
        'Omit args entirely to migrate the default set.',
    )
  }
  if (!value.length) {
    throw new Error('args was an empty array. Omit args entirely to migrate the default set.')
  }

  // Validate at the boundary and name the offending field. The alternative is a
  // TypeError deep in the pipeline — `"NavRow".join()` — which surfaces only
  // after agents have already spawned and cost a worktree each.
  const problems = []
  value.forEach((t, i) => {
    const at = `args[${i}]`
    if (!t || typeof t !== 'object') {
      problems.push(`${at} is not an object`)
      return
    }
    for (const field of ['bdId', 'screen', 'slug']) {
      const v = t[field]
      // Truthiness alone lets a number through, which then builds a branch
      // name like `chore/123-…` rather than failing.
      if (typeof v !== 'string' || !v.trim()) {
        problems.push(`${at}.${field} must be a non-empty string, got ${JSON.stringify(v)}`)
      }
    }
    for (const field of ['handRolled', 'notes']) {
      // These have defaults below, but a spread lets a caller's wrong-typed
      // value REPLACE the default rather than fall back to it.
      if (t[field] !== undefined && !Array.isArray(t[field])) {
        problems.push(`${at}.${field} must be an array if provided, got ${typeof t[field]}`)
      }
    }
  })
  if (problems.length) {
    throw new Error(`Invalid args:\n  - ${problems.join('\n  - ')}`)
  }
  return value.map((t) => ({ handRolled: [], notes: [], ...t }))
}

const targets = resolveTargets()

// ---------------------------------------------------------------------------
// Repo rules every agent must follow. Encoded here so a drifting agent cannot
// quietly skip one; each is a real trap this repo has already been bitten by.
// ---------------------------------------------------------------------------
const GROUND_RULES = `
REPO RULES — follow every one; each has bitten this repo before.

Branch and commits
- You are already in an isolated git worktree. Do NOT create another worktree,
  do NOT spawn sub-agents, and do NOT switch to or push to main. main is
  protected.
- First verify your base is current: \`git fetch origin main\` and confirm
  \`git log origin/main..HEAD\` is empty before you start. If your base is stale,
  rebase onto origin/main before doing any work.
- Branch name: \`chore/<bd-id>-<slug>\` (slug given below, already <=50 chars).
- Conventional Commits. Put \`Refs: <bd-id>\` in the commit footer.
  \`Closes: <bd-id>\` goes in the PR BODY ONLY — putting it in a commit
  double-closes on squash-merge.
- NEVER use \`git commit --no-verify\`. If a hook fails, fix the cause. The
  pre-commit hook runs spotless and can take >2 minutes; let it finish.
- Do NOT run any \`bd\` command. The beads database is a single Dolt DB shared
  across all worktrees, so concurrent bd writes from parallel agents corrupt it.
  Issue claiming and closing happen outside this workflow.

Strings
- Any new or changed user-facing string needs \`values/\`, \`values-b+es+419/\` and
  \`values-pt-rBR/\` updated in the SAME commit.
- Verify with the touched module's own lint: \`./gradlew :feature:settings:impl:lintDebug\`.
  The :app lint task does NOT catch MissingTranslation.

Screenshots
- Regenerate baselines: \`./gradlew :feature:settings:impl:updateDebugScreenshotTest\`.
- That task regenerates EVERY baseline in the module, not just the ones you
  changed. Afterwards run \`git diff --cached --name-status -- '*.png'\` (or
  \`git status\`) and revert any PNG you did not intend to change.
- Baselines generated on macOS are sometimes rejected by CI (a host-render
  rounding difference, nubecita-0z9x). If the Screenshot job fails on your
  baselines, do NOT regenerate locally again — say so in your report and the
  human will add the \`update-baselines\` label so CI regenerates on its own
  renderer.

Verification before you push
- \`./gradlew :feature:settings:impl:testDebugUnitTest\`
- \`./gradlew :feature:settings:impl:lintDebug\`
- \`./gradlew :feature:settings:impl:validateDebugScreenshotTest\`
- \`./gradlew :app:assembleDebug\`
All must pass. Do NOT do a device pass — there is one physical device and it
cannot be shared between parallel agents. The human does that serially.
`

const DESIGN_BRIEF = `
WHAT YOU ARE CHANGING

The root SettingsScreen renders M3 Expressive segmented lists through the design
system component \`NubecitaListGroup\` + \`NubecitaListItem\`
(\`designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/NubecitaListGroup.kt\`).
Settings SUB-pages instead hand-roll Row composables separated by
HorizontalDivider on the bare surface canvas, which is the flat pre-Expressive
look. Tapping from Settings into a sub-page is a visual downgrade.

Read \`NubecitaListGroup.kt\` first. Its row modes, in precedence order:
- non-null \`onCheckedChange\` -> toggleable segment, \`Role.Switch\`. Any trailing
  Switch must be display-only (\`onCheckedChange = null\`) so there is ONE
  interactive node, not two.
- otherwise non-null \`onClick\` -> interactive button-role segment.
- otherwise the non-interactive overload for read-only rows.
The group computes each row's \`ListItemShapes\`; callers never compute
first/middle/last positions themselves — forward the passed \`shapes\` into
\`NubecitaListItem\`.

Grouping is the point, not just the row style. Split related rows into separate
\`NubecitaListGroup\`s with \`label\` captions instead of emitting one flat run.
Mirror \`SettingsSection.kt\`, which is the reference implementation.

PRESERVE BEHAVIOUR EXACTLY. In particular do not lose:
- accessibility semantics — the interactive node owns the gesture AND the label,
  so a screen reader announces both (a bare "switch, off" with no label is the
  failure mode),
- any ViewModel-side guards,
- existing strings and their translations.
This is a styling and grouping migration, not a behaviour change.
`

const MIGRATION_SCHEMA = {
  type: 'object',
  required: ['screen', 'branch', 'pushed', 'summary'],
  properties: {
    screen: { type: 'string' },
    branch: { type: 'string' },
    pushed: { type: 'boolean', description: 'true only if the branch is on origin AND a PR exists' },
    prUrl: { type: 'string' },
    summary: { type: 'string', description: 'what changed, in 2-4 sentences' },
    groupsCreated: {
      type: 'array',
      items: { type: 'string' },
      description: 'each group and its label, e.g. "Replies: All / People you follow / None"',
    },
    rowsMigrated: { type: 'array', items: { type: 'string' } },
    baselinesChanged: { type: 'integer' },
    stringsTouched: { type: 'boolean' },
    checksPassed: {
      type: 'array',
      items: { type: 'string' },
      description: 'gradle tasks that passed, verbatim',
    },
    deviations: {
      type: 'array',
      items: { type: 'string' },
      description: 'anything you did that the brief did not ask for, or any rule you could not satisfy. Empty array if none. Be honest — an unreported deviation is worse than a reported one.',
    },
    blocked: { type: 'string', description: 'why you could not finish, if you did not' },
  },
}

const VERDICT_SCHEMA = {
  type: 'object',
  required: ['screen', 'behaviourPreserved', 'findings'],
  properties: {
    screen: { type: 'string' },
    behaviourPreserved: { type: 'boolean' },
    findings: {
      type: 'array',
      items: {
        type: 'object',
        required: ['severity', 'detail'],
        properties: {
          severity: { type: 'string', enum: ['critical', 'major', 'minor'] },
          detail: { type: 'string' },
          file: { type: 'string' },
        },
      },
    },
    notes: { type: 'string' },
  },
}

// ---------------------------------------------------------------------------
// One agent per screen carries its work all the way to a PR inside its own
// worktree, because the branch has to stay in one filesystem. A second agent
// then reviews the PUSHED diff, which needs no worktree — so the review is
// genuinely independent of the agent that wrote the code.
// ---------------------------------------------------------------------------
// Name the screens, not just the count. The first live run spawned three
// agents when one was expected, and a bare count would not have made that
// obvious until worktrees appeared.
log(
  `Migrating ${targets.length} screen(s)${typeof args === 'undefined' || args === null ? ' (DEFAULT set — no args passed)' : ''}: ` +
    targets.map((t) => `${t.screen} [${t.bdId}]`).join(', '),
)

const results = await pipeline(
  targets,

  (target) =>
    agent(
      `Migrate \`${target.screen}\` to the design system's M3 Expressive list groups.

File: \`${SETTINGS_DIR}/${target.screen}.kt\`
Hand-rolled row composables to replace: ${target.handRolled.join(', ')}
bd issue: ${target.bdId}
Branch: chore/${target.bdId}-${target.slug}

Screen-specific notes:
${target.notes.map((n) => `- ${n}`).join('\n')}

${DESIGN_BRIEF}
${GROUND_RULES}

FINISH BY: committing, pushing the branch, and opening a PR with
\`gh pr create --base main\`. The PR body must explain what grouping you chose
and why, list the gradle checks that passed, and end with \`Closes: ${target.bdId}\`.
Then report the structured result. If you could not finish, set \`blocked\` and
report honestly rather than pushing something half-done.`,
      {
        label: `migrate:${target.screen}`,
        phase: 'Migrate',
        isolation: 'worktree',
        schema: MIGRATION_SCHEMA,
      },
    ),

  (migration, target) => {
    if (!migration || !migration.pushed) {
      // NOT `behaviourPreserved: false` — that reads identically to "verified
      // and found broken". Nothing was verified, so the answer is unknown.
      return {
        screen: target.screen,
        behaviourPreserved: null,
        findings: [],
        skipped: true,
        blocked: (migration && migration.blocked) || 'agent returned no result',
        notes: 'not pushed — nothing to verify',
      }
    }
    return agent(
      `Adversarially review the migration of \`${target.screen}\` to NubecitaListGroup.

PR: ${migration.prUrl || `branch ${migration.branch}`}
Read the diff with \`gh pr diff\` (or \`git fetch origin ${migration.branch} && git diff origin/main...origin/${migration.branch}\`).

This was meant to be a STYLING AND GROUPING migration with NO behaviour change.
Your job is to find where that is not true. Default to reporting a finding when
uncertain. Check specifically:

1. Accessibility. Does each interactive row still own BOTH the gesture and the
   label, so a screen reader announces the label plus its state? A row whose
   control is separately interactive (two nodes), or whose label is no longer
   part of the interactive node, is a CRITICAL regression. Check role
   correctness too (Switch vs Button vs RadioButton).
2. Lost guards. Was any conditional dropped in the swap — especially a guard
   that lived inside the old widget's contract rather than in explicit code?
   ${target.screen === 'FeedPreferencesScreen' ? 'For this screen: the ViewModel MUST still guard re-selecting the active reply visibility, and its two tests must still exist and pass.' : ''}
3. Strings. Any new/changed string present in values/, values-b+es+419/ and
   values-pt-rBR/? Any string silently dropped?
4. Screenshot baselines. Were unrelated baselines regenerated as collateral?
   The update task rewrites every baseline in the module.
5. Scope. Anything changed that the brief did not ask for.

Report only findings you can point at in the diff. Do not invent hypotheticals.`,
      { label: `verify:${target.screen}`, phase: 'Verify', schema: VERDICT_SCHEMA },
    ).then((verdict) => ({ ...verdict, migration }))
  },
)

const done = results.filter(Boolean)
const critical = done.flatMap((r) =>
  (r.findings || []).filter((f) => f.severity === 'critical').map((f) => ({ screen: r.screen, ...f })),
)

// A screen that never shipped must be impossible to mistake for one that
// passed review. The agents are asked to explain themselves via `blocked`;
// surfacing it here is the only thing that makes that explanation reach a human.
const unfinished = done.filter((r) => r.skipped).map((r) => ({ screen: r.screen, blocked: r.blocked }))

log(
  `${done.length} screen(s) processed, ${unfinished.length} unfinished, ${critical.length} critical finding(s)`,
)

return {
  screens: done.map((r) => ({
    screen: r.screen,
    prUrl: r.migration && r.migration.prUrl,
    // true / false / null — null means verification never ran.
    behaviourPreserved: r.behaviourPreserved,
    groups: r.migration && r.migration.groupsCreated,
    deviations: r.migration && r.migration.deviations,
    findings: r.findings,
  })),
  unfinished,
  critical,
  humanFollowUp: [
    'Device pass each PR on the Pixel Fold (one device, serial) before merging.',
    'bd claim/close each child issue outside the workflow — the Dolt DB is shared across worktrees.',
    'If a Screenshot CI job fails on regenerated baselines, add the `update-baselines` label rather than regenerating on macOS (nubecita-0z9x).',
  ],
}
