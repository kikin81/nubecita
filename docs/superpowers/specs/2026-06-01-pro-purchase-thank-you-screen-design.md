# Pro purchase thank-you screen — design

**Date:** 2026-06-01
**Status:** Approved (brainstorm) — pending spec review
**bd issue:** nubecita-ykpc (fast-follow under epic nubecita-q5ge — Nubecita Pro)

## Summary

After a **fresh** successful Nubecita Pro purchase, show a one-time celebratory
"thank you" screen — a centered 🎉 with a Compose confetti burst, a subtle
haptic pulse, brand-voice copy, and a **Continue** button that returns the user
to whatever they were doing. Today a successful purchase silently dismisses the
paywall back to the prior surface; for a *supporter*-framed product ("support
the developer", not "unlock features") a warm thank-you closes the emotional
loop, reinforces the indie-craftsman identity, and reduces buyer's remorse.

**Restore** that grants Pro is **not** celebrated — it keeps the existing silent
dismiss. A returning user reclaiming Pro on a reinstall/2nd device is not a new
act of support, and re-confetti on every restore would feel off.

## Scope / non-goals

- **In:** the success screen, its route, the purchase-success → success-route
  flow change, confetti + haptics, reduce-motion handling, tests.
- **Out (v1):**
  - No custom analytics event. The terminal purchase/revenue event is owned by
    RevenueCat's server-side GA4 integration (see nubecita-q5ge.13); a
    client `pro_welcome_shown` event is a deferred nicety, not needed here.
  - **Screen-view tracking: explicitly NOT tracked.** The host
    `NavKey → AnalyticsScreen` mapper (`NavKeyAnalytics.toAnalyticsScreenOrNull`)
    returns `null` for unmapped routes, so `PaywallSuccessRoute` would *not*
    auto-fire a `screen_view` — and `PaywallRoute` itself is already untracked.
    To keep that intent explicit (and because `NavKeyAnalyticsTest` must be
    updated for every new route), add `is PaywallSuccessRoute -> null` in the
    mapper's "deliberately NOT tracked" branch (compile-referenced, so a rename
    breaks it) plus a `NavKeyAnalyticsTest` assertion. A `paywall` /
    `paywall_success` `screen_view` pair is a clean future addition if a GA4
    funnel is wanted, but is out of scope here for consistency with the
    untracked paywall.
  - No plan-aware copy (annual vs monthly) — one generic thank-you.
  - Restore is unchanged (no celebration).

## Architecture

A dedicated **`PaywallSuccessRoute`** sub-route (decided over an in-paywall
terminal state), kept independently navigable and reusable.

- **`:feature:paywall:api`** — new `@Serializable data object PaywallSuccessRoute : NavKey`.
- **`:feature:paywall:impl`** — new stateless `PaywallSuccessScreen` composable
  (**no ViewModel**: pure presentation + a Continue callback) and its `@MainShell`
  entry provider in `PaywallNavigationModule`.

### Flow / navigation

```
prior surface (PiP video / Settings)
  └─ PaywallRoute  ── purchase success ──▶  PaywallSuccessRoute (🎉)
                                              └─ Continue / Back ──▶ prior surface
```

- `PaywallViewModel`: on `PurchaseResult.Success`, emit a **new**
  `PaywallEffect.PurchaseSucceeded` instead of `PaywallEffect.Dismiss`.
  `RestoreResult.Completed(isPro = true)` keeps emitting `Dismiss` — unchanged.
- `PaywallScreen` collects `PurchaseSucceeded` and performs a **replace** on the
  inner back stack: `LocalMainShellNavState.current.removeLast()` (pop the
  paywall) then `.add(PaywallSuccessRoute)`. Net stack: `[…prior, PaywallSuccessRoute]`.
  - Replacing (rather than stacking success on top of the paywall) means
    **both** the Continue button **and** system Back pop exactly once back to the
    prior surface — the user can never land on the now-pointless plan picker.
- `PaywallSuccessScreen`'s entry provider wires `onContinue = { navState.removeLast() }`.

This keeps navigation a screen concern (the VM never touches the nav state
holder), matching the existing paywall/profile pattern.

## The screen

`Scaffold(containerColor = MaterialTheme.colorScheme.surface)`, full-screen
`@MainShell` sub-route (bottom bar already hidden for sub-routes on phones).
Centered content column:

- A large **🎉** emoji (the confetti origin).
- **Headline** + **subline** in the paywall's established brand voice
  (CTA "Become a Supporter", headline "Keep Nubecita flying ☁️"): e.g.
  headline **"You're a Supporter"**, subline **"Thank you for keeping Nubecita
  flying ☁️"**. Final copy pinned at implementation; uses `MaterialTheme`
  typography, no hardcoded styles.
- **Confetti burst** — hand-rolled Compose. A `Canvas` over the content draws
  ~120 particles; each has a launch angle, velocity, and a brand-palette color;
  position is `origin + velocity·t + ½·gravity·t²` with alpha fading to 0, all
  driven by **one** `Animatable` `progress` 0→1 over ~1.2s (one-shot).
  Emanates from the emoji. Decorative (`contentDescription = null`).
  - **Particle params are deterministic and stable.** Launch angle / velocity /
    color are generated **once** in a `remember { ... }` (NOT recomputed per
    frame) — only `progress` animates over the fixed set. Generation uses a
    **fixed `Random(seed)`** so the burst is reproducible (required for the
    pinned-frame screenshot baseline; a varying burst would make the baseline
    flaky). The content composable also accepts an explicit `progress`
    parameter so a screenshot can render a fixed frame.
  - **Invalidation stays in the draw phase.** `progress` (the `Animatable.value`)
    is read **only inside the `Canvas` draw lambda** (`DrawScope`) — never in
    the composable body — so each frame invalidates *draw*, not *composition*.
    Reading `.value` in the parent scope would recompose the whole screen every
    frame; this keeps it to a redraw of the `Canvas` only.
- A **subtle haptic** pulse fired once on entry via `LocalHapticFeedback`
  (a confirm/long-press style feedback — exact `HapticFeedbackType` pinned
  against the project's Compose version at implementation). No `VIBRATE`
  permission, no `Vibrator`.
- **Window insets.** As a `@MainShell` sub-route the bottom bar is hidden
  (phones) / on the rail (tablets), so this screen owns its insets (per the
  CLAUDE.md sub-route insets contract). The bottom-pinned **Continue** button
  gets `Modifier.navigationBarsPadding()` (or consumes the Scaffold's
  `safeDrawing` inner padding) so it clears the gesture-nav pill and stays a
  full hit target — never drawn under the system bar.
- **Plays exactly once, glitch-free across config changes.** A
  `var hasPlayed by rememberSaveable { mutableStateOf(false) }` flag guards the
  **haptic** (fires exactly once, never re-fires on rotation/split-screen/dark
  toggle). The confetti `Animatable` is **initialized from that flag** —
  `remember { Animatable(if (hasPlayed) 1f else 0f) }` — and the driving
  `LaunchedEffect(Unit)` runs **only when `!hasPlayed`** (sets the flag, fires
  the haptic, animates 0→1).
  - *Why init-from-flag, not "guard the animation with the flag":* the
    `Animatable` is `remember`ed (not saveable), so a config change recreates it
    at its initial value. If the flag merely skipped the effect and the
    Animatable defaulted to `0f`, the burst would **freeze at the emoji origin**
    (full-alpha particles) — a visible glitch. Initializing to `1f` when
    `hasPlayed` lands the screen on the *completed/invisible* state: rotating
    mid-burst just finishes it instantly; rotating after it's done shows no
    confetti. No replay, no frozen frame.
  - *Rejected — saving the live `progress` float via `rememberSaveable` to
    resume mid-flight:* you can't `rememberSaveable` an `Animatable` (needs a
    custom `Saver`), and continuously mirroring its value into observable state
    to persist it reintroduces the per-frame recomposition the draw-phase
    isolation above exists to prevent. Resume-from-exact-frame fidelity isn't
    worth that for a 1.2s burst.
  - Reduce-motion short-circuits all of this (no Canvas burst, no animation, no
    haptic — static 🎉), so the flag/Animatable logic only applies when motion
    is allowed.
- A bottom-pinned **Continue** button (`NubecitaPrimaryButton` or the M3
  equivalent already used) → `onContinue()`.

## Accessibility / reduce-motion

When the system **reduce-motion** setting is on:
- Skip the particle animation entirely — render a **static 🎉** (no `Canvas`
  burst, no progress animation).
- Skip the entry haptic.

Reuse the app's existing reduce-motion signal (the same one
`NubecitaMotionScheme(isReduced = …)` is built from in `Theme.kt`); the exact
public accessor is confirmed at implementation. The confetti is purely
decorative, so a screen reader reads only the headline, subline, and Continue.

## Error handling

None beyond the existing paywall flow. The screen has no data load, no I/O, no
failure modes — it renders static content + a local animation. If the nav
replace somehow leaves an empty back stack (it can't: the paywall always sits
above a prior surface inside `MainShell`), Continue's `removeLast()` is a no-op
guarded by the nav state holder.

## Testing

- **Screenshot tests** (`:feature:paywall:impl` screenshotTest):
  - Success screen at a **pinned confetti progress frame** (so the baseline is
    deterministic — pass an explicit `progress` to the content composable),
    light + dark.
  - The **reduce-motion static** variant (no particles), to pin that branch.
- **`PaywallViewModelTest`** (unit):
  - `PurchaseResult.Success` now emits `PaywallEffect.PurchaseSucceeded`
    (not `Dismiss`).
  - `RestoreResult.Completed(isPro = true)` still emits `Dismiss`.
  - (The nav replace is a screen-Composable concern, exercised via the existing
    instrumentation pattern, not the VM unit test.)
- **`NavKeyAnalyticsTest`** (unit): add `PaywallSuccessRoute → null` to pin that
  it is intentionally untracked (matches the new mapper branch).
- **Device note (not asserted):** haptic behavior varies by OEM, the user's
  "touch feedback" setting, and Battery Saver — some devices soften or suppress
  `LocalHapticFeedback`. Fail-silent is the intended behavior; do not assert the
  haptic in tests, and sanity-check the feel on a couple of physical devices.

## Module boundaries

No new module. `PaywallSuccessRoute` joins the existing `:feature:paywall:api`
NavKeys; the screen + provider join `:feature:paywall:impl`. No new dependency
(confetti is hand-rolled Compose; haptics via the Compose runtime). No analytics
or billing coupling.
