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
  - No analytics event. The terminal purchase/revenue event is owned by
    RevenueCat's server-side GA4 integration (see nubecita-q5ge.13); a
    client `pro_welcome_shown` event is a deferred nicety, not needed here.
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
  driven by **one** `Animatable` `progress` 0→1 over ~1.2s (one-shot, started in
  a `LaunchedEffect`). Emanates from the emoji. Decorative
  (`contentDescription = null`); no recomposition storm (single animated float,
  particles are `remember`ed).
- A **subtle haptic** pulse fired once on entry via `LocalHapticFeedback`
  (a confirm/long-press style feedback — exact `HapticFeedbackType` pinned
  against the project's Compose version at implementation). No `VIBRATE`
  permission, no `Vibrator`.
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

## Module boundaries

No new module. `PaywallSuccessRoute` joins the existing `:feature:paywall:api`
NavKeys; the screen + provider join `:feature:paywall:impl`. No new dependency
(confetti is hand-rolled Compose; haptics via the Compose runtime). No analytics
or billing coupling.
