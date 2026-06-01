# Nubecita Pro — sandbox purchase smoke test

Manual on-device verification for **nubecita-q5ge.12** (epic **nubecita-q5ge**,
design `openspec/changes/pro-supporter-first-iap/design.md`). This is the
**live** end-to-end purchase smoke the unit tests can't cover: RevenueCat talks
directly to Google Play Billing + RevenueCat servers, and Play's purchase sheet
can't be driven by an instrumented test — so this is a human-in-the-loop runbook,
not an automated test.

> **Gate:** this is checklist item **F1** in `docs/pro-launch-checklist.md`.
> **Do not ship Nubecita Pro to production until this smoke passes** (and section
> E of the launch checklist is confirmed). The free app must stay fully usable
> throughout; moderation/safety is never gated.

## What "pass" proves

A Play **license tester** can buy a Nubecita Pro subscription in the Play
**sandbox** (no real charge), the `pro` entitlement flips `isPro → true`, the
three Pro surfaces unlock, and the entitlement survives a restore. Cancellation
flips it back without yanking an in-flight feature.

## Reference state (from `docs/pro-launch-checklist.md`, verified 2026-06-01)

| Thing | Value |
|---|---|
| Package | `net.kikin.nubecita` |
| Entitlement id | `pro` (matches `PRO_ENTITLEMENT_ID` in `RevenueCatMappers.kt`) |
| Play products | `nubecita_pro:monthly` ($1.99, P1M) · `nubecita_pro:annual` ($19.99, P1Y) |
| Offering | `default` → `$rc_monthly` / `$rc_annual` |
| SDK | `com.revenuecat.purchases:purchases` 10.7.0; `goog_` public key via `BuildConfig.REVENUECAT_API_KEY` |

The app is **inert without the key**: `RevenueCatInitializer` only calls
`Purchases.configure` on the production flavor when the key is non-empty, so a
keyless/bench build never touches the SDK and `isPro` stays `false` (by design).
The build under test **must carry the `goog_` key**.

## Prerequisites (one-time)

1. **License tester added.** Play Console → *Settings → License testing* → add the
   tester's Google account (or use a closed-testing track tester). Sandbox
   purchases on a license-tester account are **not charged** and renew on Google's
   accelerated [sandbox cadence](https://developer.android.com/google/play/billing/test)
   (e.g. a monthly sub renews every ~5 minutes, expires after ~6 renewals).
2. **Keyed build available.** Either:
   - the **internal-track** build the release workflow produces (it passes
     `REVENUECAT_API_KEY` from the `release` environment secret — #386), **or**
   - a **local release build** with the key in `local.properties`
     (`revenueCatApiKey=goog_…`) or `-PrevenueCatApiKey=goog_…`, e.g.
     `./gradlew :app:bundleProductionRelease -PrevenueCatApiKey=goog_…`
     (debug-signed release is fine for the smoke; see the OAuth-on-debug caveat
     in project memory if sign-in misbehaves).
3. **Device** signed in with the **license-tester** Google account, Play Store
   updated, app installed from the keyed build above.

## Smoke steps

Record PASS/FAIL + notes for each. `isPro` itself isn't shown directly — observe
it through the three surfaces that gate on it.

### A. Configure + offerings load  *(no purchase)*

1. Cold-start the app, sign in.
2. Open **Settings → Nubecita Pro** row → tap it to open the paywall.
3. **Assert:**
   - [ ] Paywall renders **two plans** with **store-localized** prices —
         **$1.99/mo** and **$19.99/yr (~16% off)** — not placeholder/empty.
         (Proves `Purchases.configure` succeeded and `loadPlans()` mapped the
         `default` offering; a config/offering misconfig shows the retryable
         error state instead.)
   - [ ] No crash / ANR on the billing init path.

### B. Sandbox purchase grants Pro

1. On the paywall, select **Annual**, tap **Become a Supporter**.
2. Complete the Google Play **sandbox** purchase dialog (shows "test card / you
   won't be charged").
3. **Assert the entitlement flips and all three surfaces unlock:**
   - [ ] Paywall **dismisses** on success.
   - [ ] **Settings → Nubecita Pro** now shows the member face:
         **"Nubecita Pro · Annual · $19.99/yr"** + **Manage subscription** +
         **Restore purchases** rows (was the single upsell row before). [.10]
   - [ ] **Own profile hero** shows the gold **Supporter** badge. [.9]
   - [ ] **Fullscreen video** → the **pop-out (PiP)** button now enters PiP
         instead of routing to the paywall. [.8]
   - [ ] **Manage subscription** opens the Play subscriptions page for
         `net.kikin.nubecita` (sku-scoped deep link).

### C. Restore survives reinstall / second device

1. Uninstall + reinstall the keyed build (or install it on a **second device**
   signed in with the **same** Play account).
2. Before any purchase, open **Settings → Nubecita Pro** → tap **Restore
   purchases** (also offered on the paywall).
3. **Assert:**
   - [ ] Restore reports success and `isPro` returns to **true** — the member
         face + badge + PiP unlock return **without re-purchasing**. (Confirms the
         anonymous Play-account binding, design D3 — checklist **F2**.)
   - [ ] Restore with **nothing to restore** (a fresh non-purchased account)
         shows the friendly "No purchases found" snackbar, not an error.

### D. Cancellation reflects in-app  *(checklist F3)*

1. Cancel the subscription (Play Store → Subscriptions, or let the sandbox sub
   lapse after its accelerated renewals).
2. Re-open the app after expiry.
3. **Assert:**
   - [ ] `isPro → false`: the Supporter badge disappears, the PiP pop-out reverts
         to the paywall upsell, and Settings shows the upsell row again.
   - [ ] An **already-open PiP window is NOT force-closed** mid-playback when the
         entitlement lapses — only *new* PiP entries are blocked (design risk
         note). Verify by entering PiP, then triggering expiry while it plays.

### E. Analytics  *(checklist F4 — informational; app-side linking is q5ge.13)*

- [ ] A sandbox purchase produces a RevenueCat event, and the RC→Firebase
      integration forwards it to **GA4**. Attribution of the event to the right
      Firebase user (linking RevenueCat's anonymous app-user id ↔ the Firebase
      app-instance id) is **nubecita-q5ge.13**, not this smoke.

## On pass

- Tick **F1** in `docs/pro-launch-checklist.md` and note the date + tester.
- `bd close nubecita-q5ge.12` with the result.
- F2 / F3 are covered by steps C / D here; record them on the launch checklist too.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| Paywall shows the error/retry state; no prices | Build is **keyless** (no `goog_` key), or the `default` offering / products aren't mapped (launch-checklist A–B). |
| Purchase dialog shows a **real** price / charge warning | Account isn't a registered **license tester**, or testing on a track the tester isn't enrolled in. |
| `isPro` never flips after a successful purchase | Entitlement id mismatch — must be exactly `pro` (`PRO_ENTITLEMENT_ID`); or the product isn't attached to the `pro` entitlement in RevenueCat (A3). |
| Pro unlocks but disappears on next cold start | Expected if the **sandbox sub expired** (accelerated renewals); re-purchase or restore. |
| Sign-in fails on a debug-signed release build | Debug cert isn't in `assetlinks.json` — see the "debug build App Link sign-in break" project memory for the `pm set-app-links-*` workaround. |
