# Multi-machine bd / Dolt sync runbook

This repo tracks work in [beads](https://github.com/steveyegge/beads) (`bd`), backed by a
Dolt database that syncs to a DoltHub remote (`kikin/nubecita`) via `bd dolt push` /
`bd dolt pull`. The author works from **two computers**, which has caused the issue
database to **fork** (one clone ended up 10 commits ahead *and* 956 behind the remote,
from a months-old common ancestor, with incompatible schemas).

This page is the recovery + prevention runbook. The same rules live in the `bd remember`
memory `multi-machine-bd-sync` (surfaced by `bd prime`), but they're duplicated here in
git because **a machine whose `dolt` binary is too old can't even `bd dolt pull`** — so
the fix instructions have to arrive over the *git* channel, not the Dolt one.

> Canonical source of truth is the **DoltHub remote** (`refs/dolt/data`). `.beads/issues.jsonl`
> is an export for viewers/interchange — **never** a sync channel or backup. The repo
> deliberately gitignores it and sets `backup.git-push: false`.

---

## 0. First-time setup on each machine (do this BEFORE `bd dolt pull`)

The root cause of the fork was a **stale, shadowing `dolt` binary**. A standalone
`/usr/local/bin/dolt` (old, root-owned) sat ahead of Homebrew's on `PATH`, and bd launched
its Dolt server with that old binary — which **cannot read data written by a newer dolt**
(Dolt's compatibility is one-directional: newer reads older, not vice-versa). The symptom
is `ERROR 1105: table has unknown fields`, after which the clone silently forks instead of
fast-forwarding.

Fix on every machine:

```bash
brew upgrade dolt                       # match the version that wrote the remote (>= 2.x)

# Ensure Homebrew's dolt wins on PATH. On this repo's setup the culprit was a
# `export PATH=/usr/local/bin:${PATH}` line in ~/.zshrc re-prepending /usr/local/bin
# AHEAD of Homebrew. Remove that prepend (/usr/local/bin is still on PATH via /etc/paths).
# Optionally delete the stale binary outright:
#   sudo rm /usr/local/bin/dolt

zsh -lic 'which dolt; dolt version'     # MUST print /opt/homebrew/bin/dolt and 2.x
```

Only once `which dolt` reports the Homebrew 2.x binary is it safe to sync.

---

## 1. The session loop (every session, both machines)

```bash
# ── SESSION START — before any other bd command ──────────────────
bd dolt pull                            # fast-forward local main to the remote tip

# 10-second sanity check: did I leave the other machine ahead?
( cd .beads/dolt/nubecita && dolt fetch && \
  dolt sql -q "select * from dolt_branch_status('remotes/origin/main','main');" )
#   behind only   -> bd dolt pull again
#   ahead only    -> fine, you have local work to push
#   diverged      -> STOP, go to section 3 before doing any more bd work

# ── WORK ──────────────────────────────────────────────────────────
bd ready
bd update <id> --claim
#   ...work; leave notes for the next session/compaction...
bd update <id> --notes "what changed, why, where I stopped"
bd close <id> --reason "..."            # only after the PR merges (see bd-workflow)

# ── SESSION END — before you walk away ────────────────────────────
bd dolt push                            # so the other machine can fast-forward next time
```

**Invariant:** the machine you sit down at must fast-forward to `origin/main` before you
work, and must push `origin/main` to itself before you leave. If `pull` ever can't
fast-forward, you skipped a push on the other machine — reconcile (section 3), don't pile
work onto the divergence.

Why push even after a trivial session: Dolt runs with **auto-commit on**, so merely *using*
bd writes local commits. An "I only glanced at it" machine can silently be N commits ahead.

---

## 2. Detecting divergence early

Run against `.beads/dolt/nubecita`:

```bash
dolt fetch                                                    # refresh remote view, no merge
dolt status                                                   # "behind by N / fast-forward" or "have diverged"
dolt sql -q "select * from dolt_branch_status('remotes/origin/main','main');"  # exact ahead/behind
dolt sql -q "select dolt_merge_base('main','remotes/origin/main');"            # how old the fork is
```

Read it like git: **behind-only → pull · ahead-only → push · both non-zero → fork (section 3).**

---

## 3. Recovery when already diverged

| State | Action |
|---|---|
| Behind only | `bd dolt pull` — clean fast-forward |
| Ahead only | `bd dolt push` — remote fast-forwards to you |
| **Diverged (both)** | The side the *other* machine has been pushing is canonical (usually the larger *behind* count). On the **stale** machine: `dolt fetch origin && dolt reset --hard remotes/origin/main` to discard its fork. **Merge instead** (`dolt merge remotes/origin/main`, resolve `dolt_conflicts`) only if the local-only commits hold real, unpushed issue work. |

Expected noise: even clean multi-machine pulls can conflict on the `metadata` table rows
`dolt_auto_push_commit` / `dolt_auto_push_last` — those are machine-local. If they're the
**only** conflicts, take either side; your issue data is unaffected. Don't let it become a
routine `--force` habit.

For a fresh clone or post-fork rebuild, use `bd bootstrap` (non-destructive) — **not**
`bd init` (guarded as destructive; needs `--reinit-local` / `--discard-remote` + a
`DESTROY-<prefix>` token).

---

## 4. Why not just switch to git-tracked JSONL?

It doesn't fix sync — it relocates divergence (silent Dolt forks become git merge conflicts
on `issues.jsonl`) and reintroduces the auto-rewrite churn this repo deliberately disabled
(every 15-min snapshot rewrite conflicted on every stacked PR). bd's own docs say
`issues.jsonl` is an export, not a source of truth. Keep Dolt; the real fixes are **binary
parity (section 0)** + **pull-first/push-last discipline (section 1)**.

---

## Operating rules (the short version)

1. Same `dolt` version on both machines (section 0). This alone prevents the stale-binary fork.
2. `bd dolt pull` first, every session.
3. After pulling, `dolt fetch && dolt status` — diverged → stop.
4. `bd dolt push` last, every session, even trivial ones.
5. One active machine at a time; `origin/main` is canonical; no branch-per-machine.
6. Diverged → `dolt reset --hard remotes/origin/main` on the stale machine (merge only to save real local work).
7. Never sync via `issues.jsonl`. Keep `.beads/` off cloud-synced filesystems (Dropbox/iCloud → DB corruption).
